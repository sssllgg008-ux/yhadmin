package yh.hotplugin.system.domain.service;

import yh.hotplugin.system.domain.model.Role;
import yh.hotplugin.system.domain.model.User;
import yh.hotplugin.system.domain.repository.AuthorizationRepository;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Domain service equivalent to yhaminadmin StpInterfaceImpl authorization semantics.
 */
public final class AuthorizationService {
    private final AuthorizationRepository repository;

    public AuthorizationService(AuthorizationRepository repository) {
        this.repository = repository;
    }

    public Set<String> permissionsOf(long userId) {
        User user = repository.findUser(userId);
        Set<String> result = new LinkedHashSet<>();
        if (user == null || !user.isEnabled()) return result;
        for (Long roleId : user.getRoleIds()) {
            Role role = repository.findRole(roleId);
            if (role != null && role.isEnabled()) result.addAll(role.getPermissions());
        }
        return result;
    }

    public boolean isAllowed(long userId, String permission) {
        if (permission == null || permission.trim().isEmpty()) return false;
        Set<String> granted = permissionsOf(userId);
        return granted.contains("*") || granted.contains(permission.trim());
    }
}