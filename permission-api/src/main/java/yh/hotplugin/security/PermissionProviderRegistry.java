package yh.hotplugin.security;

import yh.hotplugin.security.api.PermissionProvider;

import java.util.concurrent.atomic.AtomicReference;

public final class PermissionProviderRegistry {
    private static final PermissionProvider DENY = new DenyAllPermissionProvider();
    private static final AtomicReference<PermissionProvider> CURRENT = new AtomicReference<PermissionProvider>(DENY);

    private PermissionProviderRegistry() {
    }

    public static PermissionProvider get() {
        return CURRENT.get();
    }

    public static void install(PermissionProvider p) {
        if (p == null) throw new IllegalArgumentException("provider");
        CURRENT.set(p);
    }

    public static void uninstall(PermissionProvider p) {
        CURRENT.compareAndSet(p, DENY);
    }

    public static boolean available() {
        return CURRENT.get() != DENY;
    }
}
