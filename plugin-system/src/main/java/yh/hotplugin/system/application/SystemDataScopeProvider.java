package yh.hotplugin.system.application;

import yh.hotplugin.security.api.DataScopeProvider;
import yh.hotplugin.security.api.DataScopeResult;
import yh.hotplugin.security.api.SecurityPrincipal;
import yh.hotplugin.security.DataResourceRegistry;
import yh.hotplugin.system.infrastructure.JdbcAuthorizationRepository;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Resolves structured scopes only for resources with known dept/owner columns. */
public final class SystemDataScopeProvider implements DataScopeProvider {
    private final JdbcAuthorizationRepository repository;
    public SystemDataScopeProvider(JdbcAuthorizationRepository repository) { this.repository = repository; }
    public DataScopeResult resolve(SecurityPrincipal principal, String resource) {
        if (principal == null || resource == null || !DataResourceRegistry.contains(resource)) return DataScopeResult.deny();
        return repository.resolveDataScope(principal.getTenantId(), principal.getUserId());
    }
}
