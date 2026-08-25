package yh.hotplugin.security.api;

import java.util.Optional;

/** Stable host session contract shared with hot-plugged permission providers. */
public interface SessionGateway {
    TokenResult login(SecurityPrincipal principal, LoginOptions options);
    Optional<SecurityPrincipal> resolve(String token);
    void logout(String token);
    void kickout(long tenantId, long userId);
}
