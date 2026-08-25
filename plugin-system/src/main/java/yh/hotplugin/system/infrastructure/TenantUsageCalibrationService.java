package yh.hotplugin.system.infrastructure;

import yh.hotplugin.security.tenant.TenantContext;
import yh.hotplugin.security.tenant.TenantContributorRegistry;
import yh.hotplugin.security.tenant.TenantQuotaContributor;
import yh.hotplugin.system.infrastructure.mybatis.MybatisExecutor;

import java.util.*;

/** Rebuilds durable resource usage from contributor-owned database facts. */
public final class TenantUsageCalibrationService {
    private final MybatisExecutor db;

    public TenantUsageCalibrationService(JdbcAuthorizationRepository authorization) {
        this.db = authorization.executor();
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "pluginSystem.usage")) {
            db.update("CREATE TABLE IF NOT EXISTS sys_tenant_usage_calibration (tenant_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,last_calibrate_time DATETIME NULL,error_message VARCHAR(1000) NULL,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
        }
    }

    public Map<String,Object> calibrate(long tenantId) {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "pluginSystem.usage")) {
            db.update("INSERT INTO sys_tenant_usage_calibration(tenant_id,status,update_time) VALUES(?,'RUNNING',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE status='RUNNING',error_message=NULL,update_time=CURRENT_TIMESTAMP", Collections.<Object>singletonList(tenantId));
            try {
                Map<String,Long> values = new LinkedHashMap<String,Long>();
                for (TenantQuotaContributor contributor : TenantContributorRegistry.quota()) {
                    Map<String,Long> current = contributor.currentUsage(tenantId);
                    if (current != null) for (Map.Entry<String,Long> entry : current.entrySet())
                        values.merge(entry.getKey(), entry.getValue() == null ? 0L : entry.getValue(), Long::sum);
                }
                db.transaction(m -> {
                    for (Map.Entry<String,Long> entry : values.entrySet())
                        m.update("INSERT INTO sys_tenant_usage(tenant_id,usage_key,usage_value,period_key,update_time) VALUES(?,?,?,'current',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE usage_value=VALUES(usage_value),update_time=CURRENT_TIMESTAMP", Arrays.<Object>asList(tenantId, entry.getKey(), entry.getValue()));
                    m.update("UPDATE sys_tenant_usage_calibration SET status='SUCCEEDED',last_calibrate_time=CURRENT_TIMESTAMP,error_message=NULL,update_time=CURRENT_TIMESTAMP WHERE tenant_id=?", Collections.<Object>singletonList(tenantId));
                    return null;
                });
                return status(tenantId);
            } catch (RuntimeException e) {
                db.update("UPDATE sys_tenant_usage_calibration SET status='FAILED',error_message=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=?", Arrays.<Object>asList(cut(e.getMessage()), tenantId));
                throw e;
            }
        }
    }

    public Map<String,Object> status(long tenantId) {
        return db.one("SELECT * FROM sys_tenant_usage_calibration WHERE tenant_id=?", Collections.<Object>singletonList(tenantId));
    }

    private static String cut(String value) {
        if (value == null) return "unknown";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
