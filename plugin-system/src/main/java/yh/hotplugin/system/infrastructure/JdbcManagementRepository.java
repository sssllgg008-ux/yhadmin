package yh.hotplugin.system.infrastructure;

import yh.hotplugin.system.infrastructure.mybatis.*;

import java.util.*;
import java.security.SecureRandom;
import yh.hotplugin.security.tenant.TenantContext;
import yh.hotplugin.security.tenant.*;
import yh.hotplugin.system.security.GmPasswordEncoder;

/**
 * MyBatis-Plus management repository. Name retained for binary compatibility.
 */
public final class JdbcManagementRepository {
    private final GmPasswordEncoder passwords = new GmPasswordEncoder();
    private final MybatisExecutor db;

    public JdbcManagementRepository(JdbcAuthorizationRepository a) {
        db = a.executor();
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "pluginSystem")) {
            ensureSaasTables();
        }
    }

    private void ensureSaasTables() {
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_lifecycle (tenant_id BIGINT NOT NULL,lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',stage VARCHAR(64) NULL,error_message VARCHAR(1000) NULL,retry_count INT NOT NULL DEFAULT 0,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_plan (id BIGINT NOT NULL AUTO_INCREMENT,plan_name VARCHAR(100) NOT NULL,plan_code VARCHAR(64) NOT NULL,status VARCHAR(1) NOT NULL DEFAULT '0',description VARCHAR(1000) NULL,is_default CHAR(1) NOT NULL DEFAULT 'N',create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),UNIQUE KEY uk_plan_code(plan_code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_plan_quota (id BIGINT NOT NULL AUTO_INCREMENT,plan_id BIGINT NOT NULL,quota_key VARCHAR(100) NOT NULL,quota_limit BIGINT NOT NULL,PRIMARY KEY(id),UNIQUE KEY uk_plan_quota(plan_id,quota_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_subscription (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,plan_id BIGINT NOT NULL,start_time DATETIME NOT NULL,end_time DATETIME NULL,status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),KEY idx_subscription_tenant(tenant_id,status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_quota_override (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,quota_key VARCHAR(100) NOT NULL,quota_limit BIGINT NOT NULL,PRIMARY KEY(id),UNIQUE KEY uk_tenant_quota(tenant_id,quota_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_usage (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,usage_key VARCHAR(100) NOT NULL,usage_value BIGINT NOT NULL DEFAULT 0,period_key VARCHAR(32) NOT NULL DEFAULT 'current',update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),UNIQUE KEY uk_tenant_usage(tenant_id,usage_key,period_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_backup (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,file_name VARCHAR(255) NULL,file_path VARCHAR(1000) NULL,checksum VARCHAR(128) NULL,error_message VARCHAR(1000) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,finish_time DATETIME NULL,PRIMARY KEY(id),KEY idx_backup_tenant(tenant_id,create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_plan_change (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,old_plan_id BIGINT NULL,new_plan_id BIGINT NOT NULL,changed_by VARCHAR(100) NULL,remark VARCHAR(500) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),KEY idx_plan_change_tenant(tenant_id,create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_rate_policy (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,route_pattern VARCHAR(255) NOT NULL DEFAULT '*',minute_limit BIGINT NULL,day_limit BIGINT NULL,status VARCHAR(1) NOT NULL DEFAULT '0',create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NULL,PRIMARY KEY(id),UNIQUE KEY uk_tenant_route_policy(tenant_id,route_pattern)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_limit_event (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,quota_key VARCHAR(100) NOT NULL,limit_value BIGINT NOT NULL,current_value BIGINT NOT NULL,request_path VARCHAR(500) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),KEY idx_limit_event_tenant(tenant_id,create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_restore_task (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,backup_id BIGINT NOT NULL,status VARCHAR(40) NOT NULL,actor VARCHAR(100) NULL,error_message VARCHAR(1000) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,start_time DATETIME NULL,finish_time DATETIME NULL,PRIMARY KEY(id),KEY idx_restore_tenant(tenant_id,create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_restore_detail (id BIGINT NOT NULL AUTO_INCREMENT,task_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,plugin_name VARCHAR(100) NOT NULL,sequence_no INT NOT NULL,status VARCHAR(40) NOT NULL,error_message VARCHAR(1000) NULL,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),UNIQUE KEY uk_restore_plugin(task_id,plugin_name),KEY idx_restore_detail_tenant(tenant_id,task_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        db.update("CREATE TABLE IF NOT EXISTS sys_tenant_usage_calibration (tenant_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,last_calibrate_time DATETIME NULL,error_message VARCHAR(1000) NULL,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        addColumnIfMissing("sys_plan", "version", "INT NOT NULL DEFAULT 1");
        addColumnIfMissing("sys_plan", "lifecycle_status", "VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'");
        addColumnIfMissing("sys_plan", "display_price", "DECIMAL(12,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("sys_plan", "currency", "VARCHAR(10) NOT NULL DEFAULT 'CNY'");
        addColumnIfMissing("sys_plan", "billing_cycle", "VARCHAR(20) NOT NULL DEFAULT 'MONTH'");
        addColumnIfMissing("sys_plan", "trial_days", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("sys_plan", "sort_order", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("sys_plan", "publish_time", "DATETIME NULL");
        addColumnIfMissing("sys_plan", "update_time", "DATETIME NULL");
        dropIndexIfExists("sys_plan", "uk_plan_code");
        addUniqueIndexIfMissing("sys_plan", "uk_plan_code_version", "plan_code,version");
        addColumnIfMissing("sys_plan_quota", "quota_name", "VARCHAR(100) NULL");
        addColumnIfMissing("sys_plan_quota", "unit", "VARCHAR(30) NULL");
        addColumnIfMissing("sys_plan_quota", "description", "VARCHAR(500) NULL");
        addColumnIfMissing("sys_plan_quota", "category", "VARCHAR(50) NULL");
        addColumnIfMissing("sys_plan_quota", "unlimited", "CHAR(1) NOT NULL DEFAULT 'N'");
        db.update("CREATE TABLE IF NOT EXISTS sys_plan_feature (id BIGINT NOT NULL AUTO_INCREMENT,plan_id BIGINT NOT NULL,feature_key VARCHAR(100) NOT NULL,feature_name VARCHAR(100) NOT NULL,enabled CHAR(1) NOT NULL DEFAULT 'Y',description VARCHAR(500) NULL,PRIMARY KEY(id),UNIQUE KEY uk_plan_feature(plan_id,feature_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        addColumnIfMissing("sys_tenant_subscription", "plan_version", "INT NOT NULL DEFAULT 1");
        addColumnIfMissing("sys_tenant_subscription", "source", "VARCHAR(30) NOT NULL DEFAULT 'PLATFORM'");
        addColumnIfMissing("sys_tenant_subscription", "grace_days", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("sys_tenant_subscription", "pending_plan_id", "BIGINT NULL");
        addColumnIfMissing("sys_tenant_subscription", "pending_effective_time", "DATETIME NULL");
        addColumnIfMissing("sys_tenant_plan_change", "old_plan_version", "INT NULL");
        addColumnIfMissing("sys_tenant_plan_change", "new_plan_version", "INT NULL");
        addColumnIfMissing("sys_tenant_plan_change", "effective_mode", "VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE'");
        addColumnIfMissing("sys_tenant_plan_change", "scheduled_time", "DATETIME NULL");
        addColumnIfMissing("sys_tenant_plan_change", "execution_status", "VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED'");
        db.update("UPDATE sys_plan SET lifecycle_status='PUBLISHED',publish_time=COALESCE(publish_time,create_time) WHERE lifecycle_status IS NULL OR lifecycle_status=''", Collections.<Object>emptyList());
        addColumnIfMissing("sys_user", "password_change_required", "CHAR(1) NOT NULL DEFAULT '0'");
        addColumnIfMissing("sys_user", "temp_password_expire_time", "DATETIME NULL");
        addIndexIfMissing("sys_menu", "idx_menu_status_order", "status,order_num,id");
        addIndexIfMissing("sys_menu", "idx_menu_module_status_order", "module_id,status,order_num,id");
        addIndexIfMissing("sys_tenant_subscription", "idx_subscription_tenant_status_id", "tenant_id,status,id");
        addIndexIfMissing("sys_tenant_rate_policy", "idx_rate_policy_tenant_status_id", "tenant_id,status,id");
        // The wildcard admin role is tenant-scoped and must never imply platform authority.
        // Bootstrap one explicit hidden permission and grant it only to the existing platform administrator.
        db.update("INSERT INTO sys_menu(parent_id,module_id,menu_name,menu_type,order_num,path,component,icon,perms,status,visible,remark) SELECT 0,1,'平台管理授权','F',9999,'','','','platform:manage','0','1','平台级权限（系统维护）' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='platform:manage')", Collections.<Object>emptyList());
        db.update("INSERT INTO sys_role_menu(tenant_id,role_id,menu_id) SELECT 1,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.perms='platform:manage' WHERE r.tenant_id=1 AND r.role_key='admin' AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.tenant_id=1 AND x.role_id=r.id AND x.menu_id=m.id)", Collections.<Object>emptyList());
        db.update("INSERT INTO sys_menu(parent_id,module_id,menu_name,menu_type,order_num,path,component,icon,perms,status,visible,remark) SELECT p.id,p.module_id,'套餐管理','C',95,'plan','system/plan/index','Tickets','system:plan:list','0','0','SaaS 套餐版本、权益和配额管理' FROM sys_menu p JOIN sys_module module ON module.id=p.module_id AND module.module_code='system' AND module.status='0' WHERE p.parent_id=0 AND p.menu_type='M' AND (p.menu_name='系统管理' OR COALESCE(p.path,'')='') AND p.status='0' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='system:plan:list') ORDER BY p.id LIMIT 1", Collections.<Object>emptyList());
        for (String[] permission : new String[][]{{"套餐查询","system:plan:query"},{"套餐新增","system:plan:add"},{"套餐编辑","system:plan:edit"},{"套餐发布","system:plan:publish"},{"套餐删除","system:plan:remove"},{"租户订阅管理","system:tenant:subscription"},{"限流查看","system:tenant:rate:list"},{"限流维护","system:tenant:rate:edit"},{"备份查看","system:tenant:backup:list"},{"创建备份","system:tenant:backup:create"},{"下载备份","system:tenant:backup:download"},{"恢复备份","system:tenant:backup:restore"},{"用量校准","system:tenant:usage:calibrate"}})
            db.update("INSERT INTO sys_menu(parent_id,module_id,menu_name,menu_type,order_num,path,component,icon,perms,status,visible,remark) SELECT p.id,p.module_id,?,'F',1,'','','',?,'0','1','套餐管理按钮权限' FROM sys_menu p WHERE p.perms='system:plan:list' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms=?)", Arrays.<Object>asList(permission[0],permission[1],permission[1]));
        db.update("INSERT INTO sys_role_menu(tenant_id,role_id,menu_id) SELECT 1,r.id,m.id FROM sys_role r JOIN sys_menu m ON (m.perms LIKE 'system:plan:%' OR m.perms IN ('system:tenant:subscription','system:tenant:rate:list','system:tenant:rate:edit','system:tenant:backup:list','system:tenant:backup:create','system:tenant:backup:download','system:tenant:backup:restore','system:tenant:usage:calibrate')) WHERE r.tenant_id=1 AND r.role_key='admin' AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.tenant_id=1 AND x.role_id=r.id AND x.menu_id=m.id)", Collections.<Object>emptyList());
        db.update("INSERT INTO sys_plan(plan_name,plan_code,status,description,is_default) SELECT '基础版','BASIC','0','系统默认 SaaS 套餐','Y' WHERE NOT EXISTS(SELECT 1 FROM sys_plan WHERE plan_code='BASIC')", Collections.<Object>emptyList());
        for (Object[] q : new Object[][]{{"users.max",100L,"用户数量","个","账号资源"},{"roles.max",50L,"角色数量","个","账号资源"},{"departments.max",100L,"部门数量","个","账号资源"},{"notices.max",1000L,"公告数量","条","内容资源"},{"dicts.max",100L,"字典数量","项","内容资源"},{"configs.max",200L,"参数数量","项","内容资源"},{"api.minute",600L,"每分钟 API 请求","次/分钟","接口资源"},{"api.day",100000L,"每日 API 请求","次/日","接口资源"},{"backups.max",5L,"备份保留数量","份","数据保护"}}) {
            db.update("INSERT INTO sys_plan_quota(plan_id,quota_key,quota_limit,quota_name,unit,category,unlimited) SELECT id,?,?,?,?,?,'N' FROM sys_plan WHERE plan_code='BASIC' AND version=1 AND NOT EXISTS(SELECT 1 FROM sys_plan_quota pq WHERE pq.plan_id=sys_plan.id AND pq.quota_key=?)", Arrays.<Object>asList(q[0],q[1],q[2],q[3],q[4],q[0]));
            db.update("UPDATE sys_plan_quota pq JOIN sys_plan p ON p.id=pq.plan_id SET pq.quota_name=COALESCE(pq.quota_name,?),pq.unit=COALESCE(pq.unit,?),pq.category=COALESCE(pq.category,?) WHERE p.plan_code='BASIC' AND p.version=1 AND pq.quota_key=?", Arrays.<Object>asList(q[2],q[3],q[4],q[0]));
        }
        db.update("INSERT INTO sys_tenant_subscription(tenant_id,plan_id,start_time,status) " +
                "SELECT t.id,p.id,CURRENT_TIMESTAMP,'ACTIVE' FROM sys_tenant t JOIN sys_plan p ON p.is_default='Y' AND p.status='0' " +
                "WHERE t.status='0' AND NOT EXISTS(SELECT 1 FROM sys_tenant_subscription s WHERE s.tenant_id=t.id AND s.status='ACTIVE')", Collections.<Object>emptyList());
    }

    /** Compatible with MySQL versions that do not support ADD COLUMN IF NOT EXISTS. */
    private void addColumnIfMissing(String table, String column, String definition) {
        long count = db.count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                Arrays.<Object>asList(table, column));
        if (count == 0) db.update("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition, Collections.<Object>emptyList());
    }

    private void dropIndexIfExists(String table, String index) {
        long count = db.count("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?",
                Arrays.<Object>asList(table, index));
        if (count > 0) db.update("ALTER TABLE " + table + " DROP INDEX " + index, Collections.<Object>emptyList());
    }

    private void addUniqueIndexIfMissing(String table, String index, String columns) {
        long count = db.count("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?",
                Arrays.<Object>asList(table, index));
        if (count == 0) db.update("ALTER TABLE " + table + " ADD UNIQUE INDEX " + index + " (" + columns + ")", Collections.<Object>emptyList());
    }

    private void addIndexIfMissing(String table, String index, String columns) {
        long count = db.count("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?",
                Arrays.<Object>asList(table, index));
        if (count == 0) db.update("ALTER TABLE " + table + " ADD INDEX " + index + " (" + columns + ")", Collections.<Object>emptyList());
    }

    public TenantCreation createTenant(Map<String,Object> body, String actor) {
        final String password = randomPassword();
        final String hash = passwords.encode(password);
        return db.transaction(m -> {
            Map<String,Object> h = new HashMap<String,Object>();
            m.insert("INSERT INTO sys_tenant(tenant_name,tenant_code,contact,phone,email,expire_time,status,remark,create_by,create_time) VALUES(?,?,?,?,?,?,'1',?,?,CURRENT_TIMESTAMP)", Arrays.<Object>asList(body.get("tenantName"),body.get("tenantCode"),body.get("contact"),body.get("phone"),body.get("email"),body.get("expireTime"),body.get("remark"),actor), h);
            long t = ((Number)h.get("id")).longValue();
            m.update("INSERT INTO sys_tenant_lifecycle(tenant_id,lifecycle_status,stage) VALUES(?,'INITIALIZING','DATABASE')", Collections.<Object>singletonList(t));
            bootstrapTenant(m,t,String.valueOf(body.get("tenantName")),actor,hash);
            m.update("INSERT INTO sys_tenant_subscription(tenant_id,plan_id,start_time,status) SELECT ?,id,CURRENT_TIMESTAMP,'ACTIVE' FROM sys_plan WHERE is_default='Y' AND status='0' ORDER BY id LIMIT 1", Collections.<Object>singletonList(t));
            m.update("UPDATE sys_tenant SET status='0',update_time=CURRENT_TIMESTAMP WHERE id=?", Collections.<Object>singletonList(t));
            m.update("UPDATE sys_tenant_lifecycle SET lifecycle_status='ACTIVE',stage='READY',update_time=CURRENT_TIMESTAMP WHERE tenant_id=?", Collections.<Object>singletonList(t));
            return new TenantCreation(t,password);
        });
    }

    public void changeStatus(String table, long t, long id, String status) {
        if (!Arrays.asList("sys_user", "sys_role", "sys_menu", "sys_dept").contains(table))
            throw new IllegalArgumentException("table");
        if ("1".equals(status) && "sys_user".equals(table) && has(table, t, id, "username", "admin"))
            throw new IllegalArgumentException("超级管理员不可停用");
        if ("1".equals(status) && "sys_role".equals(table) && has(table, t, id, "role_key", "admin"))
            throw new IllegalArgumentException("超级管理员角色不可停用");
        one("UPDATE " + table + " SET status=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", Arrays.<Object>asList(status, t, id));
    }

    public void updateOwnTenant(long t, String status, String remark) {
        one("UPDATE sys_tenant SET status=?,remark=?,update_time=CURRENT_TIMESTAMP WHERE id=?", Arrays.<Object>asList(status, remark, t));
    }

    public void changeTenantStatus(long t, String status) {
        if (t == 1 && "1".equals(status)) throw new IllegalArgumentException("默认租户不可停用");
        one("UPDATE sys_tenant SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=?", Arrays.<Object>asList(status, t));
    }

    public void resetPassword(long t, long id, String hash) {
        one("UPDATE sys_user SET password=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", Arrays.<Object>asList(hash, t, id));
    }

    public String resetTenantAdminPassword(long tenantId) {
        if (tenantId <= 1) throw new IllegalArgumentException("默认租户管理员请在用户管理中重置密码");
        String password = randomPassword();
        String hash = passwords.encode(password);
        one("UPDATE sys_user u JOIN sys_tenant t ON t.id=u.tenant_id SET u.password=?,u.password_change_required='1',u.temp_password_expire_time=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 24 HOUR),u.remark='首次登录必须修改密码',u.update_time=CURRENT_TIMESTAMP " +
                "WHERE u.tenant_id=? AND u.username='admin' AND t.status='0'", Arrays.<Object>asList(hash, tenantId));
        return password;
    }

    public void updateRoleDataScope(long t, long id, int scope) {
        if (scope < 1 || scope > 5) throw new IllegalArgumentException("dataScope");
        one("UPDATE sys_role SET data_scope=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", Arrays.<Object>asList(scope, t, id));
    }

    public void replaceUserRoles(long t, long id, Collection<Long> v) {
        replace("sys_user_role", "user_id", id, "role_id", v, t);
    }

    public void replaceRoleMenus(long t, long id, Collection<Long> v) {
        replace("sys_role_menu", "role_id", id, "menu_id", v, t);
    }

    public void replaceRoleDepartments(long t, long id, Collection<Long> v) {
        replace("sys_role_dept", "role_id", id, "dept_id", v, t);
    }

    public void replaceRoleUsers(long t, long id, Collection<Long> v) {
        replace("sys_user_role", "role_id", id, "user_id", v, t);
    }

    public Set<Long> roleIdsForUser(long t, long id) {
        return ids("SELECT role_id value FROM sys_user_role WHERE tenant_id=? AND user_id=?", t, id);
    }

    public Set<Long> menuIdsForRole(long t, long id) {
        return ids("SELECT menu_id value FROM sys_role_menu WHERE tenant_id=? AND role_id=?", t, id);
    }

    public Set<Long> deptIdsForRole(long t, long id) {
        return ids("SELECT dept_id value FROM sys_role_dept WHERE tenant_id=? AND role_id=?", t, id);
    }

    public Set<Long> usersForRole(long t, long id) {
        return ids("SELECT user_id value FROM sys_user_role WHERE tenant_id=? AND role_id=?", t, id);
    }

    public Map<Long, Set<Long>> usersForMenu(long menu) {
        Map<Long, Set<Long>> o = new LinkedHashMap<Long, Set<Long>>();
        for (Map<String, Object> r : db.query("SELECT DISTINCT ur.tenant_id,ur.user_id FROM sys_role_menu rm JOIN sys_user_role ur ON ur.tenant_id=rm.tenant_id AND ur.role_id=rm.role_id WHERE rm.menu_id=?", Collections.<Object>singletonList(menu)))
            o.computeIfAbsent(num(r, "tenantId"), k -> new LinkedHashSet<Long>()).add(num(r, "userId"));
        return o;
    }

    public Set<Long> usersInTenant(long t) {
        Set<Long> o = new LinkedHashSet<Long>();
        try (TenantContext.Scope ignored = TenantContext.open(t, 0, "tenant-lifecycle")) {
            for (Map<String, Object> r : db.query("SELECT id value FROM sys_user WHERE tenant_id=?", Collections.<Object>singletonList(t)))
                o.add(num(r, "value"));
        }
        return o;
    }

    public long tenantAdminUserId(long tenantId) {
        Map<String, Object> row = db.one("SELECT id FROM sys_user WHERE tenant_id=? AND username='admin' LIMIT 1", Collections.<Object>singletonList(tenantId));
        if (row == null) throw new IllegalArgumentException("租户管理员不存在");
        return num(row, "id");
    }

    public void rollbackFailedTenantInitialization(long tenantId) {
        cleanupTenant(tenantId);
        db.transaction(m -> {
            m.update("DELETE FROM sys_tenant_lifecycle WHERE tenant_id=?", Collections.<Object>singletonList(tenantId));
            m.update("DELETE FROM sys_tenant WHERE id=?", Collections.<Object>singletonList(tenantId));
            return null;
        });
    }

    private void bootstrapTenant(DynamicSqlMapper m,long t,String name,String actor,String hash) {
        Map<Long,Long> deptIds=copyBaseDepartments(m,t,name,actor);
        Map<Long,Long> roleIds=copyBaseRoles(m,t,actor);
        long dept=firstRootDepartment(m,t,deptIds);
        long role=adminRole(m,t,actor,roleIds);
        Map<String,Object> h=new HashMap<String,Object>();
        m.insert("INSERT INTO sys_user(tenant_id,dept_id,dept_name,username,nickname,status,password,password_change_required,temp_password_expire_time,remark,create_by,create_time) VALUES(?,?,?,'admin','租户管理员','0',?,'1',DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 24 HOUR),'首次登录必须修改密码',?,CURRENT_TIMESTAMP)",Arrays.<Object>asList(t,dept,name,hash,actor),h); long user=((Number)h.get("id")).longValue();
        m.update("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES(?,?,?)",Arrays.<Object>asList(t,user,role));
        copyBaseRoleRelations(m,t,roleIds,deptIds);
        m.update("INSERT INTO sys_dict_type(tenant_id,dict_name,dict_type,status,is_system,remark,create_by,create_time) SELECT ?,dict_name,dict_type,status,is_system,remark,?,CURRENT_TIMESTAMP FROM sys_dict_type WHERE tenant_id=1",Arrays.<Object>asList(t,actor));
        m.update("INSERT INTO sys_dict_data(tenant_id,dict_type_id,dict_type,dict_label,dict_value,dict_sort,list_class,css_class,is_default,status,remark,create_by,create_time) SELECT ?,nt.id,d.dict_type,d.dict_label,d.dict_value,d.dict_sort,d.list_class,d.css_class,d.is_default,d.status,d.remark,?,CURRENT_TIMESTAMP FROM sys_dict_data d JOIN sys_dict_type nt ON nt.tenant_id=? AND nt.dict_type=d.dict_type WHERE d.tenant_id=1",Arrays.<Object>asList(t,actor,t));
        m.update("INSERT INTO sys_config(tenant_id,config_name,config_key,config_value,config_type,remark,create_by,create_time) SELECT ?,config_name,config_key,config_value,config_type,remark,?,CURRENT_TIMESTAMP FROM sys_config WHERE tenant_id=1",Arrays.<Object>asList(t,actor));
        m.update("INSERT INTO sys_ext_field(tenant_id,entity_type,field_key,field_label,field_type,dict_type,sort,status,remark,create_by,create_time) SELECT ?,entity_type,field_key,field_label,field_type,dict_type,sort,status,remark,?,CURRENT_TIMESTAMP FROM sys_ext_field WHERE tenant_id=1",Arrays.<Object>asList(t,actor));
    }

    private Map<Long,Long> copyBaseDepartments(DynamicSqlMapper m,long tenantId,String tenantName,String actor) {
        List<Map<String,Object>> pending=new ArrayList<Map<String,Object>>(m.select("SELECT id,parent_id,dept_name,order_num,leader,phone,email,status,remark FROM sys_dept WHERE tenant_id=1 ORDER BY parent_id,id",Collections.<Object>emptyList()));
        Map<Long,Long> ids=new LinkedHashMap<Long,Long>(); Map<Long,String> ancestors=new HashMap<Long,String>(); boolean rootNamed=false;
        while(!pending.isEmpty()) {
            boolean progressed=false;
            for(Iterator<Map<String,Object>> it=pending.iterator();it.hasNext();) {
                Map<String,Object> row=it.next();long oldId=number(row,"id"),oldParent=number(row,"parentId");
                if(oldParent!=0&&!ids.containsKey(oldParent))continue;
                long parent=oldParent==0?0:ids.get(oldParent);String path=oldParent==0?"0":ancestors.get(oldParent)+","+parent;
                String deptName=text(row,"deptName");if(oldParent==0&&!rootNamed){deptName=tenantName;rootNamed=true;}
                Map<String,Object> key=new HashMap<String,Object>();
                m.insert("INSERT INTO sys_dept(tenant_id,parent_id,ancestors,dept_name,order_num,leader,phone,email,status,remark,create_by,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",Arrays.<Object>asList(tenantId,parent,path,deptName,value(row,"orderNum"),value(row,"leader"),value(row,"phone"),value(row,"email"),value(row,"status"),value(row,"remark"),actor),key);
                ids.put(oldId,((Number)key.get("id")).longValue());ancestors.put(oldId,path);it.remove();progressed=true;
            }
            if(!progressed)throw new IllegalStateException("TENANT_BASE_DEPARTMENT_TREE_INVALID");
        }
        if(ids.isEmpty()){Map<String,Object> key=new HashMap<String,Object>();m.insert("INSERT INTO sys_dept(tenant_id,parent_id,ancestors,dept_name,order_num,status,remark,create_by,create_time) VALUES(?,0,'0',?,0,'0','租户根部门（系统初始化）',?,CURRENT_TIMESTAMP)",Arrays.<Object>asList(tenantId,tenantName,actor),key);ids.put(0L,((Number)key.get("id")).longValue());}
        return ids;
    }

    private Map<Long,Long> copyBaseRoles(DynamicSqlMapper m,long tenantId,String actor) {
        Map<Long,Long> ids=new LinkedHashMap<Long,Long>();
        for(Map<String,Object> row:m.select("SELECT id,role_name,role_key,sort,status,data_scope,remark FROM sys_role WHERE tenant_id=1 ORDER BY sort,id",Collections.<Object>emptyList())) {
            Map<String,Object> key=new HashMap<String,Object>();
            m.insert("INSERT INTO sys_role(tenant_id,role_name,role_key,sort,status,data_scope,remark,create_by,create_time) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",Arrays.<Object>asList(tenantId,value(row,"roleName"),value(row,"roleKey"),value(row,"sort"),value(row,"status"),value(row,"dataScope"),value(row,"remark"),actor),key);
            ids.put(number(row,"id"),((Number)key.get("id")).longValue());
        }
        return ids;
    }

    private long firstRootDepartment(DynamicSqlMapper m,long tenantId,Map<Long,Long> copied) {
        Map<String,Object> row=m.select("SELECT id FROM sys_dept WHERE tenant_id=? AND parent_id=0 ORDER BY id LIMIT 1",Collections.<Object>singletonList(tenantId)).get(0);
        return number(row,"id");
    }

    private long adminRole(DynamicSqlMapper m,long tenantId,String actor,Map<Long,Long> copied) {
        List<Map<String,Object>> rows=m.select("SELECT id FROM sys_role WHERE tenant_id=? AND role_key='admin' ORDER BY id LIMIT 1",Collections.<Object>singletonList(tenantId));
        if(!rows.isEmpty())return number(rows.get(0),"id");
        Map<String,Object> key=new HashMap<String,Object>();m.insert("INSERT INTO sys_role(tenant_id,role_name,role_key,sort,status,data_scope,remark,create_by,create_time) VALUES(?,'超级管理员','admin',1,'0',1,'租户超级管理员（系统初始化）',?,CURRENT_TIMESTAMP)",Arrays.<Object>asList(tenantId,actor),key);long role=((Number)key.get("id")).longValue();m.update("INSERT INTO sys_role_menu(tenant_id,role_id,menu_id) SELECT ?,?,id FROM sys_menu WHERE perms IS NULL OR (perms NOT LIKE 'platform:%' AND perms NOT LIKE 'system:plan:%' AND perms<>'system:tenant:subscription')",Arrays.<Object>asList(tenantId,role));return role;
    }

    private void copyBaseRoleRelations(DynamicSqlMapper m,long tenantId,Map<Long,Long> roleIds,Map<Long,Long> deptIds) {
        for(Map<String,Object> row:m.select("SELECT rm.role_id,rm.menu_id FROM sys_role_menu rm JOIN sys_menu menu ON menu.id=rm.menu_id WHERE rm.tenant_id=1 AND (menu.perms IS NULL OR (menu.perms NOT LIKE 'platform:%' AND menu.perms NOT LIKE 'system:plan:%' AND menu.perms<>'system:tenant:subscription'))",Collections.<Object>emptyList())) {
            Long role=roleIds.get(number(row,"roleId"));if(role!=null)m.update("INSERT INTO sys_role_menu(tenant_id,role_id,menu_id) VALUES(?,?,?)",Arrays.<Object>asList(tenantId,role,value(row,"menuId")));
        }
        for(Map<String,Object> row:m.select("SELECT role_id,dept_id FROM sys_role_dept WHERE tenant_id=1",Collections.<Object>emptyList())) {
            Long role=roleIds.get(number(row,"roleId")),dept=deptIds.get(number(row,"deptId"));if(role!=null&&dept!=null)m.update("INSERT INTO sys_role_dept(tenant_id,role_id,dept_id) VALUES(?,?,?)",Arrays.<Object>asList(tenantId,role,dept));
        }
    }

    private static Object value(Map<String,Object> row,String key){Object v=row.get(key);if(v==null){String snake=key.replaceAll("([A-Z])","_$1").toLowerCase(Locale.ROOT);v=row.get(snake);}return v;}
    private static String text(Map<String,Object> row,String key){Object v=value(row,key);return v==null?null:String.valueOf(v);}
    private static long number(Map<String,Object> row,String key){Object v=value(row,key);return v==null?0:((Number)v).longValue();}

    public int deleteAggregate(String r, long t, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        String in = marks(ids.size());
        List<Object> a = args(t, ids);
        if ("user".equals(r)) {
            deny("SELECT 1 FROM sys_user WHERE tenant_id=? AND id IN (" + in + ") AND username='admin'", a, "超级管理员不可删除");
            db.update("DELETE FROM sys_user_role WHERE tenant_id=? AND user_id IN (" + in + ")", a);
            return db.update("DELETE FROM sys_user WHERE tenant_id=? AND id IN (" + in + ")", a);
        }
        if ("role".equals(r)) {
            deny("SELECT 1 FROM sys_role WHERE tenant_id=? AND id IN (" + in + ") AND role_key='admin'", a, "超级管理员角色不可删除");
            deny("SELECT 1 FROM sys_user_role WHERE tenant_id=? AND role_id IN (" + in + ")", a, "角色已关联用户，无法删除");
            db.update("DELETE FROM sys_role_menu WHERE tenant_id=? AND role_id IN (" + in + ")", a);
            db.update("DELETE FROM sys_role_dept WHERE tenant_id=? AND role_id IN (" + in + ")", a);
            return db.update("DELETE FROM sys_role WHERE tenant_id=? AND id IN (" + in + ")", a);
        }
        if ("menu".equals(r)) {
            if (ids.contains(1L)) throw new IllegalArgumentException("系统内置菜单不可删除");
            deny("SELECT 1 FROM sys_menu WHERE parent_id IN (" + in + ")", new ArrayList<Object>(ids), "菜单下存在子菜单，无法删除");
            db.update("DELETE FROM sys_role_menu WHERE menu_id IN (" + in + ")", new ArrayList<Object>(ids));
            return db.update("DELETE FROM sys_menu WHERE id IN (" + in + ")", new ArrayList<Object>(ids));
        }
        if ("dept".equals(r)) {
            deny("SELECT 1 FROM sys_dept WHERE tenant_id=? AND id IN (" + in + ") AND parent_id=0", a, "根部门不可删除");
            deny("SELECT 1 FROM sys_user WHERE tenant_id=? AND dept_id IN (" + in + ")", a, "部门仍有关联用户");
            return db.update("DELETE FROM sys_dept WHERE tenant_id=? AND id IN (" + in + ")", a);
        }
        if ("tenant".equals(r)) {
            if (ids.contains(1L)) throw new IllegalArgumentException("默认租户不可删除");
            for (Long id : ids) cleanupAcrossPlugins(id);
            return ids.size();
        }
        if ("module".equals(r)) {
            for (Long id : ids) if (id <= 3) throw new IllegalArgumentException("系统内置模块不可删除");
            deny("SELECT 1 FROM sys_menu WHERE module_id IN (" + in + ")", new ArrayList<Object>(ids), "模块下存在菜单，无法删除");
            return db.update("DELETE FROM sys_module WHERE id IN (" + in + ")", new ArrayList<Object>(ids));
        }
        if ("dict".equals(r)) {
            deny("SELECT 1 FROM sys_dict_type WHERE tenant_id=? AND id IN (" + in + ") AND is_system='Y'", a, "系统内置字典不可删除");
            db.update("DELETE d FROM sys_dict_data d JOIN sys_dict_type x ON x.tenant_id=d.tenant_id AND x.dict_type=d.dict_type WHERE d.tenant_id=? AND x.id IN (" + in + ")", a);
            return db.update("DELETE FROM sys_dict_type WHERE tenant_id=? AND id IN (" + in + ")", a);
        }
        String table = "config".equals(r) ? "sys_config" : "dictData".equals(r) ? "sys_dict_data" : "notice".equals(r) ? "sys_notice" : "extField".equals(r) ? "sys_ext_field" : null;
        if (table == null) throw new IllegalArgumentException("Unsupported resource");
        if ("config".equals(r))
            deny("SELECT 1 FROM sys_config WHERE tenant_id=? AND id IN (" + in + ") AND config_type='Y'", a, "系统内置参数不可删除");
        return db.update("DELETE FROM " + table + " WHERE tenant_id=? AND id IN (" + in + ")", a);
    }

    private void replace(String table, String owner, long ownerId, String target, Collection<Long> values, long t) {
        db.transaction(m -> {
            m.update("DELETE FROM " + table + " WHERE tenant_id=? AND " + owner + "=?", Arrays.<Object>asList(t, ownerId));
            if (values != null) for (Long id : values) {
                boolean global = "menu_id".equals(target);
                String targetTable = "role_id".equals(target) ? "sys_role" : "user_id".equals(target) ? "sys_user" : "menu_id".equals(target) ? "sys_menu" : "sys_dept";
                m.update("INSERT INTO " + table + "(tenant_id," + owner + "," + target + ") SELECT ?,?,id FROM " + targetTable + (global ? " WHERE id=?" : " WHERE tenant_id=? AND id=?"), global ? Arrays.<Object>asList(t, ownerId, id) : Arrays.<Object>asList(t, ownerId, t, id));
            }
            return null;
        });
    }

    private Set<Long> ids(String q, long t, long id) {
        Set<Long> o = new LinkedHashSet<Long>();
        for (Map<String, Object> r : db.query(q, Arrays.<Object>asList(t, id))) o.add(num(r, "value"));
        return o;
    }

    private void one(String q, List<Object> a) {
        if (db.update(q, a) != 1) throw new IllegalArgumentException("Target not found in current tenant");
    }

    private boolean has(String table, long t, long id, String col, String value) {
        return db.one("SELECT 1 value FROM " + table + " WHERE tenant_id=? AND id=? AND " + col + "=?", Arrays.<Object>asList(t, id, value)) != null;
    }

    private void deny(String q, List<Object> a, String msg) {
        if (db.one(q, a) != null) throw new IllegalArgumentException(msg);
    }

    public void cleanupTenant(long t) {
        db.transaction(m -> {
            m.update("UPDATE sys_tenant SET status='1',update_time=CURRENT_TIMESTAMP WHERE id=?",Collections.<Object>singletonList(t));
            m.update("INSERT INTO sys_tenant_lifecycle(tenant_id,lifecycle_status,stage,update_time) VALUES(?,'DELETING','DATABASE',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE lifecycle_status='DELETING',stage='DATABASE',error_message=NULL,update_time=CURRENT_TIMESTAMP",Collections.<Object>singletonList(t));
            for(String x:Arrays.asList("sys_user_role","sys_role_menu","sys_role_dept","sys_notice_read","sys_dict_data","sys_ext_value","sys_ext_field","sys_notice","sys_config","sys_dict_type","sys_oper_log","sys_logininfor","sys_error_log","sys_tenant_restore_detail","sys_tenant_restore_task","sys_tenant_usage_calibration","sys_tenant_limit_event","sys_tenant_rate_policy","sys_tenant_plan_change","sys_tenant_usage","sys_tenant_quota_override","sys_tenant_subscription","sys_tenant_backup","sys_user","sys_role","sys_dept"))
                m.update("DELETE FROM "+x+" WHERE tenant_id=?",Collections.<Object>singletonList(t));
            int completed = m.update("UPDATE sys_tenant_lifecycle SET lifecycle_status='DELETED',stage='COMPLETE',update_time=CURRENT_TIMESTAMP WHERE tenant_id=?",Collections.<Object>singletonList(t));
            if (completed != 1) throw new IllegalStateException("TENANT_DELETE_STATE_UPDATE_FAILED: " + t);
            return null;
        });
    }

    private void cleanupAcrossPlugins(long tenantId) {
        Map<String,TenantLifecycleContributor> lifecycle=new LinkedHashMap<String,TenantLifecycleContributor>();
        for(TenantLifecycleContributor c:TenantContributorRegistry.lifecycle())lifecycle.put(c.pluginName(),c);
        for(TenantDataContributor data:TenantContributorRegistry.data())if(!lifecycle.containsKey(data.pluginName()))throw new IllegalStateException("TENANT_CLEANUP_CONTRIBUTOR_MISSING: "+data.pluginName());
        List<TenantLifecycleContributor> contributors=new ArrayList<TenantLifecycleContributor>(lifecycle.values());Collections.reverse(contributors);
        try {
            db.update("UPDATE sys_tenant SET status='1',update_time=CURRENT_TIMESTAMP WHERE id=?",Collections.<Object>singletonList(tenantId));
            db.update("INSERT INTO sys_tenant_lifecycle(tenant_id,lifecycle_status,stage,update_time) VALUES(?,'DELETING','CONTRIBUTORS',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE lifecycle_status='DELETING',stage='CONTRIBUTORS',error_message=NULL,update_time=CURRENT_TIMESTAMP",Collections.<Object>singletonList(tenantId));
            for(TenantLifecycleContributor c:contributors)if(!"pluginSystem".equals(c.pluginName())){c.cleanup(tenantId);if(c.hasResidualData(tenantId))throw new IllegalStateException("TENANT_RESIDUAL_DATA: "+c.pluginName());}
            cleanupTenant(tenantId);
        } catch(RuntimeException e) {
            db.update("UPDATE sys_tenant_lifecycle SET lifecycle_status='DELETE_FAILED',stage='CONTRIBUTORS',error_message=?,retry_count=retry_count+1,update_time=CURRENT_TIMESTAMP WHERE tenant_id=?",Arrays.<Object>asList(cut(e.getMessage(),1000),tenantId));
            throw e;
        }
    }

    private static String cut(String value,int max){if(value==null)return "unknown";return value.length()<=max?value:value.substring(0,max);}

    public boolean hasTenantResidualData(long t) {
        for(String x:Arrays.asList("sys_user_role","sys_role_menu","sys_role_dept","sys_notice_read","sys_dict_data","sys_ext_value","sys_ext_field","sys_notice","sys_config","sys_dict_type","sys_oper_log","sys_logininfor","sys_error_log","sys_tenant_restore_detail","sys_tenant_restore_task","sys_tenant_usage_calibration","sys_tenant_limit_event","sys_tenant_rate_policy","sys_tenant_plan_change","sys_tenant_usage","sys_tenant_quota_override","sys_tenant_subscription","sys_tenant_backup","sys_user","sys_role","sys_dept"))
            if(db.count("SELECT COUNT(*) FROM "+x+" WHERE tenant_id=?",Collections.<Object>singletonList(t))>0)return true;
        return false;
    }

    public void setTenantEnabled(long t, boolean enabled) {
        db.transaction(m->{m.update("UPDATE sys_tenant SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=?",Arrays.<Object>asList(enabled?"0":"1",t));m.update("INSERT INTO sys_tenant_lifecycle(tenant_id,lifecycle_status,stage,update_time) VALUES(?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE lifecycle_status=?,stage=?,update_time=CURRENT_TIMESTAMP",Arrays.<Object>asList(t,enabled?"ACTIVE":"DISABLED",enabled?"READY":"DISABLED",enabled?"ACTIVE":"DISABLED",enabled?"READY":"DISABLED"));return null;});
    }

    private static String randomPassword(){String chars="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";SecureRandom r=new SecureRandom();StringBuilder b=new StringBuilder();for(int i=0;i<16;i++)b.append(chars.charAt(r.nextInt(chars.length())));return b.toString();}
    public static final class TenantCreation { public final long id; public final String temporaryPassword; TenantCreation(long id,String password){this.id=id;this.temporaryPassword=password;} }

    private static List<Object> args(long t, Collection<Long> ids) {
        List<Object> a = new ArrayList<Object>();
        a.add(t);
        a.addAll(ids);
        return a;
    }

    private static String marks(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static long num(Map<String, Object> r, String k) {
        return ((Number) r.get(k)).longValue();
    }
}
