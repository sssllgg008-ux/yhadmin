package yh.hotplugin.security.api;

public interface DataScopeProvider {
    DataScopeResult resolve(SecurityPrincipal p, String resource);
}
