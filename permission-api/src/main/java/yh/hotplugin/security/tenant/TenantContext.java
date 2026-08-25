package yh.hotplugin.security.tenant;

import yh.hotplugin.security.api.SecurityPrincipal;
import yh.hotplugin.security.PermissionProviderRegistry;

import java.util.concurrent.Callable;

/** Request-scoped tenant identity shared by the host and every hot plugin. */
public final class TenantContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<State>();

    private TenantContext() { }

    public static Scope open(SecurityPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("principal");
        return replace(new State(principal.getTenantId(), principal.getUserId(), principal.getUsername(), false));
    }

    public static Scope open(long tenantId, long userId, String username) {
        if (tenantId <= 0) throw new IllegalArgumentException("tenantId");
        return replace(new State(tenantId, userId, username, false));
    }

    /** Platform scope must only be opened after platform:manage has been checked. */
    public static Scope openPlatform(long userId, String username) {
        return replace(new State(null, userId, username, true));
    }

    /** Opens a user initiated platform scope only after the active provider authorizes it. */
    public static Scope openPlatform(SecurityPrincipal principal) {
        if (principal == null || !PermissionProviderRegistry.available()
                || !PermissionProviderRegistry.get().isAllowed(principal, "platform:manage")) {
            throw new SecurityException("PLATFORM_MANAGE_REQUIRED");
        }
        return replace(new State(null, principal.getUserId(), principal.getUsername(), true));
    }

    private static Scope replace(State next) {
        State previous = CURRENT.get();
        CURRENT.set(next);
        return new Scope(previous);
    }

    public static Long tenantId() { State s = CURRENT.get(); return s == null ? null : s.tenantId; }
    public static long userId() { State s = CURRENT.get(); return s == null ? 0L : s.userId; }
    public static String username() { State s = CURRENT.get(); return s == null ? null : s.username; }
    public static long requiredTenantId() {
        Long id = tenantId();
        if (id == null) throw new IllegalStateException("TENANT_CONTEXT_MISSING");
        return id.longValue();
    }
    public static boolean isPlatform() { State s = CURRENT.get(); return s != null && s.platform; }
    public static void clear() { CURRENT.remove(); }

    public static Runnable wrap(final Runnable task) {
        final State captured = CURRENT.get();
        return () -> { try (Scope ignored = replace(captured)) { task.run(); } };
    }

    public static <T> Callable<T> wrap(final Callable<T> task) {
        final State captured = CURRENT.get();
        return () -> { try (Scope ignored = replace(captured)) { return task.call(); } };
    }

    private static final class State {
        final Long tenantId; final long userId; final String username; final boolean platform;
        State(Long tenantId, long userId, String username, boolean platform) {
            this.tenantId = tenantId; this.userId = userId; this.username = username; this.platform = platform;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final State previous; private boolean closed;
        private Scope(State previous) { this.previous = previous; }
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
