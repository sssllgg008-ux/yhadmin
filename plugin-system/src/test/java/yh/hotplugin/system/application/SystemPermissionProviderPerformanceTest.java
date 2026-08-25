package yh.hotplugin.system.application;

import cn.dev33.satoken.dao.SaTokenDao;
import org.junit.jupiter.api.Test;
import yh.hotplugin.security.api.SecurityPrincipal;
import yh.hotplugin.system.domain.model.AuthorizationSnapshot;
import yh.hotplugin.system.domain.model.MenuItem;
import yh.hotplugin.system.domain.model.Role;
import yh.hotplugin.system.domain.model.User;
import yh.hotplugin.system.domain.repository.AuthorizationRepository;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemPermissionProviderPerformanceTest {
    @Test void repeatedChecksUseThePrincipalSnapshotWithoutRepositoryQueries() {
        CountingRepository repository = new CountingRepository();
        SaTokenDao dao = (SaTokenDao) Proxy.newProxyInstance(
                SaTokenDao.class.getClassLoader(), new Class<?>[]{SaTokenDao.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        SystemPermissionProvider provider = new SystemPermissionProvider(repository, dao);
        SecurityPrincipal principal = new SecurityPrincipal(1, 1, "admin", false,
                new LinkedHashSet<String>(Arrays.asList("admin")),
                new LinkedHashSet<String>(Arrays.asList("*", "platform:manage")));

        assertTrue(provider.isAllowed(principal, "system:menu:list"));
        assertTrue(provider.isAllowed(principal, "platform:manage"));
        assertEquals(Collections.singleton("admin"), provider.roles(principal));
        assertEquals(0, repository.authorizationQueries);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class CountingRepository implements AuthorizationRepository {
        int authorizationQueries;
        public User findUser(long id) { return null; }
        public Role findRole(long id) { return null; }
        public AuthorizationSnapshot findById(long tenantId, long userId) { authorizationQueries++; return null; }
        public AuthorizationSnapshot findByUsername(long tenantId, String username) { authorizationQueries++; return null; }
        public boolean updatePasswordHash(long tenantId, long userId, String passwordHash) { return false; }
        public List<MenuItem> findMenus(long tenantId, long userId) { return Collections.emptyList(); }
        public void recordLogin(long tenantId, String username, boolean success, String message) { }
    }
}
