package yh.hotplugin.security;

import yh.hotplugin.security.api.*;

import java.util.*;

public final class DenyAllPermissionProvider implements PermissionProvider {
    public LoginResult login(LoginCommand c) {
        throw new IllegalStateException("Permission service unavailable");
    }

    public SecurityPrincipal principal(String t) {
        return null;
    }

    public void logout(SecurityPrincipal p) {
    }

    public boolean isAllowed(SecurityPrincipal p, String x) {
        return false;
    }

    public Set<String> permissions(SecurityPrincipal p) {
        return Collections.emptySet();
    }

    public Set<String> roles(SecurityPrincipal p) {
        return Collections.emptySet();
    }
}
