package yh.hotplugin.security.tenant;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public interface TenantDataContributor {
    String pluginName();
    String dataVersion();
    Set<String> tenantTables();
    Set<String> globalTables();
    void exportTenant(long tenantId, OutputStream output) throws Exception;
    void importTenant(long tenantId, InputStream input) throws Exception;
}
