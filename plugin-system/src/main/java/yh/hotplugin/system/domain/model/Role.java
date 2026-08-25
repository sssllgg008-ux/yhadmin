package yh.hotplugin.system.domain.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Role projection holding permission codes assigned by sys_role_menu.
 */
public final class Role {
    private final long id;
    private final String key;
    private final Set<String> permissions = new LinkedHashSet<>();
    private final boolean enabled;

    public Role(long id, String key, boolean enabled) {
        if (id <= 0) throw new IllegalArgumentException("role id must be positive");
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("role key must not be blank");
        this.id = id;
        this.key = key.trim();
        this.enabled = enabled;
    }

    public void authorize(Set<String> values) {
        permissions.clear();
        if (values != null)
            for (String value : values) if (value != null && !value.trim().isEmpty()) permissions.add(value.trim());
    }

    public long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}