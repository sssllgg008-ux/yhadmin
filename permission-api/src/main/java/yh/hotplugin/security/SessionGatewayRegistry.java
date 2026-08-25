package yh.hotplugin.security;

import yh.hotplugin.security.api.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host-owned registry. Plugins consume it but never replace it.
 */
public final class SessionGatewayRegistry {
    private static final SessionGateway UNAVAILABLE = new SessionGateway() {
        public TokenResult login(SecurityPrincipal p, LoginOptions o) {
            throw new IllegalStateException("SESSION_GATEWAY_UNAVAILABLE");
        }

        public Optional<SecurityPrincipal> resolve(String token) {
            return Optional.empty();
        }

        public void logout(String token) {
        }

        public void kickout(long tenantId, long userId) {
        }
    };
    private static final AtomicReference<SessionGateway> CURRENT = new AtomicReference<SessionGateway>(UNAVAILABLE);

    private SessionGatewayRegistry() {
    }

    public static void install(SessionGateway gateway) {
        if (gateway == null) throw new IllegalArgumentException("gateway");
        CURRENT.set(gateway);
    }

    public static SessionGateway get() {
        return CURRENT.get();
    }

    public static boolean available() {
        return CURRENT.get() != UNAVAILABLE;
    }
}
