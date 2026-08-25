package yh.hotplugin.system.infrastructure;

import yh.hotplugin.security.tenant.*;
import yh.hotplugin.system.infrastructure.mybatis.DynamicSqlMapper;
import yh.hotplugin.system.infrastructure.mybatis.MybatisExecutor;

import java.util.*;
import java.util.concurrent.*;

/** Persistent, restart-safe tenant lifecycle task runner owned by pluginSystem. */
public final class TenantLifecycleTaskService implements AutoCloseable {
    private final MybatisExecutor db;
    private final JdbcManagementRepository management;
    private final ExecutorService executor;
    private final Set<Long> running = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    public TenantLifecycleTaskService(JdbcAuthorizationRepository authorization, JdbcManagementRepository management) {
        this.db = authorization.executor();
        this.management = management;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "plugin-system-tenant-lifecycle");
            t.setDaemon(true);
            return t;
        });
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "tenant-lifecycle-bootstrap")) {
            db.update("CREATE TABLE IF NOT EXISTS sys_tenant_lifecycle_task (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,operation VARCHAR(32) NOT NULL,status VARCHAR(32) NOT NULL,stage VARCHAR(64) NULL,progress INT NOT NULL DEFAULT 0,error_message VARCHAR(1000) NULL,requested_by VARCHAR(100) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,start_time DATETIME NULL,finish_time DATETIME NULL,PRIMARY KEY(id),KEY idx_tenant_task(tenant_id,status,create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
            db.update("CREATE TABLE IF NOT EXISTS sys_tenant_lifecycle_task_detail (id BIGINT NOT NULL AUTO_INCREMENT,task_id BIGINT NOT NULL,plugin_name VARCHAR(100) NOT NULL,status VARCHAR(32) NOT NULL,error_message VARCHAR(1000) NULL,start_time DATETIME NULL,finish_time DATETIME NULL,PRIMARY KEY(id),UNIQUE KEY uk_task_plugin(task_id,plugin_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
            for (Map<String,Object> task : db.query("SELECT id FROM sys_tenant_lifecycle_task WHERE status IN ('PENDING','RUNNING','WAITING_PLUGIN')", Collections.<Object>emptyList()))
                submit(((Number) task.get("id")).longValue());
        }
    }

    public long requestDelete(long tenantId, String actor) {
        if (tenantId == 1) throw new IllegalArgumentException("默认租户不可删除");
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, actor)) {
            Map<String,Object> active = db.one("SELECT id FROM sys_tenant_lifecycle_task WHERE tenant_id=? AND operation='DELETE' AND status IN ('PENDING','RUNNING','WAITING_PLUGIN') ORDER BY id DESC LIMIT 1", Collections.<Object>singletonList(tenantId));
            if (active != null) return ((Number) active.get("id")).longValue();
            long id = db.transaction(m -> createDeleteTask(m, tenantId, actor));
            submit(id);
            return id;
        }
    }

    private long createDeleteTask(DynamicSqlMapper m, long tenantId, String actor) {
        m.update("UPDATE sys_tenant SET status='1',update_time=CURRENT_TIMESTAMP WHERE id=?", Collections.<Object>singletonList(tenantId));
        m.update("INSERT INTO sys_tenant_lifecycle(tenant_id,lifecycle_status,stage,error_message,update_time) VALUES(?,'DELETING','QUEUED',NULL,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE lifecycle_status='DELETING',stage='QUEUED',error_message=NULL,update_time=CURRENT_TIMESTAMP", Collections.<Object>singletonList(tenantId));
        Map<String,Object> holder = new HashMap<String,Object>();
        m.insert("INSERT INTO sys_tenant_lifecycle_task(tenant_id,operation,status,stage,progress,requested_by) VALUES(?,'DELETE','PENDING','QUEUED',0,?)", Arrays.<Object>asList(tenantId, actor), holder);
        return ((Number) holder.get("id")).longValue();
    }

    public long retry(long tenantId, String actor) {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, actor)) {
            Map<String,Object> failed = db.one("SELECT id FROM sys_tenant_lifecycle_task WHERE tenant_id=? AND operation='DELETE' AND status IN ('FAILED','WAITING_PLUGIN') ORDER BY id DESC LIMIT 1", Collections.<Object>singletonList(tenantId));
            if (failed == null) throw new IllegalArgumentException("没有可重试的删除任务");
            long id = ((Number) failed.get("id")).longValue();
            db.update("UPDATE sys_tenant_lifecycle_task SET status='PENDING',stage='QUEUED',progress=0,error_message=NULL,requested_by=?,start_time=NULL,finish_time=NULL WHERE id=?", Arrays.<Object>asList(actor, id));
            submit(id);
            return id;
        }
    }

    public boolean cancel(long tenantId) {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "tenant-delete-cancel")) {
            int changed = db.update("UPDATE sys_tenant_lifecycle_task SET status='CANCELLED',stage='CANCELLED',finish_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND operation='DELETE' AND status='PENDING'", Collections.<Object>singletonList(tenantId));
            if (changed == 0) return false;
            db.update("UPDATE sys_tenant SET status='0',update_time=CURRENT_TIMESTAMP WHERE id=?", Collections.<Object>singletonList(tenantId));
            db.update("UPDATE sys_tenant_lifecycle SET lifecycle_status='ACTIVE',stage='READY',error_message=NULL,update_time=CURRENT_TIMESTAMP WHERE tenant_id=?", Collections.<Object>singletonList(tenantId));
            return true;
        }
    }

    public Map<String,Object> status(long tenantId) {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "tenant-lifecycle-status")) {
            Map<String,Object> lifecycle = db.one("SELECT t.id tenant_id,t.tenant_name,t.status,l.lifecycle_status,l.stage,l.error_message,l.retry_count,l.update_time FROM sys_tenant t LEFT JOIN sys_tenant_lifecycle l ON l.tenant_id=t.id WHERE t.id=?", Collections.<Object>singletonList(tenantId));
            if (lifecycle == null) return null;
            lifecycle.put("tasks", db.query("SELECT * FROM sys_tenant_lifecycle_task WHERE tenant_id=? ORDER BY id DESC LIMIT 20", Collections.<Object>singletonList(tenantId)));
            for (Map<String,Object> task : (List<Map<String,Object>>) lifecycle.get("tasks"))
                task.put("details", db.query("SELECT * FROM sys_tenant_lifecycle_task_detail WHERE task_id=? ORDER BY id", Collections.<Object>singletonList(task.get("id"))));
            return lifecycle;
        }
    }

    public List<Map<String,Object>> deletedTenants() {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "tenant-deleted-audit")) {
            return db.query("SELECT t.*,l.lifecycle_status,l.stage,l.error_message,l.retry_count,l.update_time lifecycle_update_time FROM sys_tenant t JOIN sys_tenant_lifecycle l ON l.tenant_id=t.id WHERE l.lifecycle_status='DELETED' ORDER BY l.update_time DESC", Collections.<Object>emptyList());
        }
    }

    private void submit(long taskId) {
        if (!running.add(taskId)) return;
        executor.submit(() -> {
            try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "tenant-lifecycle-worker")) {
                runDelete(taskId);
            } finally {
                running.remove(taskId);
            }
        });
    }

    private void runDelete(long taskId) {
        Map<String,Object> task = db.one("SELECT * FROM sys_tenant_lifecycle_task WHERE id=?", Collections.<Object>singletonList(taskId));
        if (task == null || "CANCELLED".equals(task.get("status"))) return;
        long tenantId = ((Number) task.get("tenantId")).longValue();
        db.update("UPDATE sys_tenant_lifecycle_task SET status='RUNNING',stage='CONTRIBUTORS',progress=10,start_time=COALESCE(start_time,CURRENT_TIMESTAMP) WHERE id=?", Collections.<Object>singletonList(taskId));
        try {
            Map<String,TenantLifecycleContributor> lifecycle = new LinkedHashMap<String,TenantLifecycleContributor>();
            for (TenantLifecycleContributor contributor : TenantContributorRegistry.lifecycle()) lifecycle.put(contributor.pluginName(), contributor);
            for (TenantDataContributor contributor : TenantContributorRegistry.data())
                if (!lifecycle.containsKey(contributor.pluginName())) throw new IllegalStateException("TENANT_CLEANUP_CONTRIBUTOR_MISSING: " + contributor.pluginName());
            List<TenantLifecycleContributor> ordered = new ArrayList<TenantLifecycleContributor>(lifecycle.values());
            Collections.reverse(ordered);
            for (TenantLifecycleContributor contributor : ordered) {
                String plugin = contributor.pluginName();
                db.update("INSERT INTO sys_tenant_lifecycle_task_detail(task_id,plugin_name,status,start_time) VALUES(?,?,'RUNNING',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE status='RUNNING',error_message=NULL,start_time=CURRENT_TIMESTAMP,finish_time=NULL", Arrays.<Object>asList(taskId,plugin));
                try {
                    if ("pluginSystem".equals(plugin)) management.cleanupTenant(tenantId); else contributor.cleanup(tenantId);
                    if (contributor.hasResidualData(tenantId)) throw new IllegalStateException("TENANT_RESIDUAL_DATA: " + plugin);
                    db.update("UPDATE sys_tenant_lifecycle_task_detail SET status='SUCCEEDED',finish_time=CURRENT_TIMESTAMP WHERE task_id=? AND plugin_name=?",Arrays.<Object>asList(taskId,plugin));
                } catch (RuntimeException failure) {
                    db.update("UPDATE sys_tenant_lifecycle_task_detail SET status='FAILED',error_message=?,finish_time=CURRENT_TIMESTAMP WHERE task_id=? AND plugin_name=?",Arrays.<Object>asList(cut(failure.getMessage()),taskId,plugin));
                    throw failure;
                }
            }
            db.update("UPDATE sys_tenant_lifecycle_task SET status='SUCCEEDED',stage='COMPLETE',progress=100,error_message=NULL,finish_time=CURRENT_TIMESTAMP WHERE id=?", Collections.<Object>singletonList(taskId));
        } catch (RuntimeException e) {
            String message = cut(e.getMessage());
            String status = message.contains("CONTRIBUTOR_MISSING") ? "WAITING_PLUGIN" : "FAILED";
            db.update("UPDATE sys_tenant_lifecycle_task SET status=?,stage='FAILED',error_message=?,finish_time=CURRENT_TIMESTAMP WHERE id=?", Arrays.<Object>asList(status, message, taskId));
        }
    }

    private static String cut(String value) {
        if (value == null) return "unknown";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public void close() {
        executor.shutdownNow();
    }
}
