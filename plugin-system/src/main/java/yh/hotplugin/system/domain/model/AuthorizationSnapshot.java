package yh.hotplugin.system.domain.model;

import java.util.*;

/**
 * One-query authorization projection scoped to a verified tenant and user.
 */
public final class AuthorizationSnapshot {
    private final long userId, tenantId;
    private final String username, passwordHash;
    private final boolean enabled, tenantEnabled;
    private final boolean passwordChangeRequired;
    private final Set<String> roles, permissions;

    public AuthorizationSnapshot(long userId, long tenantId, String username, String passwordHash, boolean enabled, boolean tenantEnabled, Set<String> roles, Set<String> permissions) {
        this(userId, tenantId, username, passwordHash, enabled, tenantEnabled, false, roles, permissions);
    }

    public AuthorizationSnapshot(long userId, long tenantId, String username, String passwordHash, boolean enabled, boolean tenantEnabled, boolean passwordChangeRequired, Set<String> roles, Set<String> permissions) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.tenantEnabled = tenantEnabled;
        this.passwordChangeRequired = passwordChangeRequired;
        this.roles = immutable(roles);
        this.permissions = immutable(permissions);
    }

    private static Set<String> immutable(Set<String> value) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(value == null ? Collections.<String>emptySet() : value));
    }

    public long getUserId() {
        return userId;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled && tenantEnabled;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }

    public Set<String> getPermissions() {
        return permissions;
    }
}
