package yh.hotplugin.security.api;

import java.util.Map;

public interface TenantQuotaProvider {
    void checkRequest(SecurityPrincipal principal, String method, String path);
    void checkResource(SecurityPrincipal principal, String quotaKey, long currentUsage, long increment);
    Map<String,Object> currentPlan(SecurityPrincipal principal);
    /** Returns whether the tenant's active plan enables a named feature entitlement. */
    default boolean hasFeature(SecurityPrincipal principal, String featureKey) { return false; }
}
