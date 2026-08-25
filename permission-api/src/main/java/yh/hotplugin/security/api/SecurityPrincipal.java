package yh.hotplugin.security.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable authenticated request principal. */
public final class SecurityPrincipal {
    private final long userId;
    private final long tenantId;
    private final String username;
    private final boolean passwordChangeRequired;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final boolean authorizationLoaded;

    public SecurityPrincipal(long userId, long tenantId, String username) {
        this(userId, tenantId, username, false);
    }

    /** Backward-compatible constructor for providers that resolve authorization separately. */
    public SecurityPrincipal(long userId, long tenantId, String username, boolean passwordChangeRequired) {
        this(userId, tenantId, username, passwordChangeRequired,
                Collections.<String>emptySet(), Collections.<String>emptySet(), false);
    }

    /** Creates a request principal carrying one immutable authorization snapshot. */
    public SecurityPrincipal(long userId, long tenantId, String username, boolean passwordChangeRequired,
                             Set<String> roles, Set<String> permissions) {
        this(userId, tenantId, username, passwordChangeRequired, roles, permissions, true);
    }

    private SecurityPrincipal(long userId, long tenantId, String username, boolean passwordChangeRequired,
                              Set<String> roles, Set<String> permissions, boolean authorizationLoaded) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.passwordChangeRequired = passwordChangeRequired;
        this.roles = immutable(roles);
        this.permissions = immutable(permissions);
        this.authorizationLoaded = authorizationLoaded;
    }

    private static Set<String> immutable(Set<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(values));
    }

    public long getUserId() { return userId; }
    public long getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }
    public boolean isAuthorizationLoaded() { return authorizationLoaded; }
}
