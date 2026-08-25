package yh.hotplugin.security.tenant;

import java.util.Map;
import java.util.Set;

public interface TenantQuotaContributor {
    String pluginName();
    Set<String> supportedQuotaKeys();
    Map<String, Long> currentUsage(long tenantId);
}
