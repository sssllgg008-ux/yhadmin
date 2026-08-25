package yh.hotplugin.security.tenant;

public interface TenantLifecycleContributor {
    String pluginName();
    void initialize(long tenantId);
    void disable(long tenantId);
    void restore(long tenantId);
    void cleanup(long tenantId);
    boolean hasResidualData(long tenantId);
}
