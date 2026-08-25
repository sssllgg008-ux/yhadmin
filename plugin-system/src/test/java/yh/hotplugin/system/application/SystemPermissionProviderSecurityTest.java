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

class SystemPermissionProviderSecurityTest {
    @Test
    void globalWildcardDoesNotGrantPlatformAdministration() {
        RecordingRepository repository = new RecordingRepository();
        SystemPermissionProvider provider = provider(repository);
        SecurityPrincipal principal = loadedPrincipal("*");

        assertTrue(provider.isAllowed(principal, "system:user:list"));
        assertFalse(provider.isAllowed(principal, "platform:manage"));
    }

    @Test
    void platformAdministrationRequiresAnExactGrant() {
        RecordingRepository repository = new RecordingRepository();
        SystemPermissionProvider provider = provider(repository);
        SecurityPrincipal principal = loadedPrincipal("*", "platform:manage");

        assertTrue(provider.isAllowed(principal, " platform:manage "));
        assertFalse(provider.isAllowed(principal, "platform:tenant:delete"));
        assertEquals(0, repository.findByIdCalls);
    }

    @Test
    void missingPrincipalAndBlankPermissionAreDeniedByDefault() {
        SystemPermissionProvider provider = provider(new RecordingRepository());

        assertFalse(provider.isAllowed(null, "system:user:list"));
        assertFalse(provider.isAllowed(loadedPrincipal("*"), null));
        assertFalse(provider.isAllowed(loadedPrincipal("*"), "   "));
    }

    @Test
    void legacyPrincipalLookupIsScopedToItsTenantAndUser() {
        RecordingRepository repository = new RecordingRepository();
        repository.snapshot = snapshot(true, "system:user:list");
        SystemPermissionProvider provider = provider(repository);
        SecurityPrincipal legacyPrincipal = new SecurityPrincipal(42L, 7L, "alice");

        assertTrue(provider.isAllowed(legacyPrincipal, "system:user:list"));
        assertEquals(1, repository.findByIdCalls);
        assertEquals(7L, repository.lastTenantId);
        assertEquals(42L, repository.lastUserId);
    }

    @Test
    void disabledSnapshotCannotAuthorizeALegacyPrincipal() {
        RecordingRepository repository = new RecordingRepository();
        repository.snapshot = snapshot(false, "*");
        SystemPermissionProvider provider = provider(repository);

        assertFalse(provider.isAllowed(new SecurityPrincipal(42L, 7L, "alice"), "system:user:list"));
    }

    private static SystemPermissionProvider provider(RecordingRepository repository) {
        SaTokenDao dao = (SaTokenDao) Proxy.newProxyInstance(
                SaTokenDao.class.getClassLoader(), new Class<?>[]{SaTokenDao.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new SystemPermissionProvider(repository, dao);
    }

    private static SecurityPrincipal loadedPrincipal(String... permissions) {
        return new SecurityPrincipal(42L, 7L, "alice", false,
                Collections.<String>emptySet(),
                new LinkedHashSet<String>(Arrays.asList(permissions)));
    }

    private static AuthorizationSnapshot snapshot(boolean enabled, String... permissions) {
        return new AuthorizationSnapshot(42L, 7L, "alice", "hash", enabled, true,
                Collections.<String>emptySet(),
                new LinkedHashSet<String>(Arrays.asList(permissions)));
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

    private static final class RecordingRepository implements AuthorizationRepository {
        AuthorizationSnapshot snapshot;
        int findByIdCalls;
        long lastTenantId;
        long lastUserId;

        public User findUser(long id) { return null; }
        public Role findRole(long id) { return null; }
        public AuthorizationSnapshot findById(long tenantId, long userId) {
            findByIdCalls++;
            lastTenantId = tenantId;
            lastUserId = userId;
            return snapshot;
        }
        public AuthorizationSnapshot findByUsername(long tenantId, String username) { return null; }
        public boolean updatePasswordHash(long tenantId, long userId, String passwordHash) { return false; }
        public List<MenuItem> findMenus(long tenantId, long userId) { return Collections.emptyList(); }
        public void recordLogin(long tenantId, String username, boolean success, String message) { }
    }
}
