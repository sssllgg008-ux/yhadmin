package yh.hotplugin.system.domain.repository;

import yh.hotplugin.system.domain.model.Role;
import yh.hotplugin.system.domain.model.User;
import yh.hotplugin.system.domain.model.AuthorizationSnapshot;
import yh.hotplugin.system.domain.model.MenuItem;

import java.util.List;

/**
 * Read port for the existing yhaminadmin authorization tables.
 */
public interface AuthorizationRepository {
    User findUser(long id);

    Role findRole(long id);

    AuthorizationSnapshot findById(long tenantId, long userId);

    AuthorizationSnapshot findByUsername(long tenantId, String username);

    boolean updatePasswordHash(long tenantId, long userId, String passwordHash);

    List<MenuItem> findMenus(long tenantId, long userId);

    void recordLogin(long tenantId, String username, boolean success, String message);
}
