package yh.hotplugin.system.domain.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User projection used by the authorization domain.
 */
public final class User {
    private final long id;
    private final String username;
    private final Set<Long> roleIds = new LinkedHashSet<>();
    private final boolean enabled;

    public User(long id, String username, boolean enabled) {
        if (id <= 0) throw new IllegalArgumentException("user id must be positive");
        if (username == null || username.trim().isEmpty())
            throw new IllegalArgumentException("username must not be blank");
        this.id = id;
        this.username = username.trim();
        this.enabled = enabled;
    }

    public void assignRoles(Set<Long> ids) {
        roleIds.clear();
        if (ids != null) roleIds.addAll(ids);
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Long> getRoleIds() {
        return Collections.unmodifiableSet(roleIds);
    }
}