package yh.hotplugin.security;

import yh.hotplugin.security.api.DataScopeProvider;
import yh.hotplugin.security.api.DataScopeResult;
import yh.hotplugin.security.api.SecurityPrincipal;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stable host-owned switch for data-scope resolution.
 */
public final class DataScopeProviderRegistry {
    private static final DataScopeProvider DENY = new DataScopeProvider() {
        public DataScopeResult resolve(SecurityPrincipal principal, String resource) {
            return DataScopeResult.deny();
        }
    };
    private static final AtomicReference<DataScopeProvider> CURRENT = new AtomicReference<DataScopeProvider>(DENY);

    private DataScopeProviderRegistry() {
    }

    public static DataScopeProvider get() {
        return CURRENT.get();
    }

    public static void install(DataScopeProvider provider) {
        CURRENT.set(provider == null ? DENY : provider);
    }

    public static void uninstall(DataScopeProvider provider) {
        CURRENT.compareAndSet(provider, DENY);
    }
}
