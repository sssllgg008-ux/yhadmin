package yh.hotplugin.security.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPrincipalTest {
    @Test void carriesAnImmutableRequestAuthorizationSnapshot() {
        Set<String> roles = new LinkedHashSet<String>(Arrays.asList("admin"));
        Set<String> permissions = new LinkedHashSet<String>(Arrays.asList("system:menu:list", "platform:manage"));
        SecurityPrincipal principal = new SecurityPrincipal(1, 1, "admin", false, roles, permissions);

        roles.clear();
        permissions.clear();

        assertTrue(principal.isAuthorizationLoaded());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("admin")), principal.getRoles());
        assertTrue(principal.getPermissions().contains("system:menu:list"));
        assertTrue(principal.getPermissions().contains("platform:manage"));
        assertThrows(UnsupportedOperationException.class, () -> principal.getPermissions().add("other"));
    }

    @Test void preservesLegacyPrincipalConstruction() {
        SecurityPrincipal principal = new SecurityPrincipal(2, 3, "legacy", false);
        assertFalse(principal.isAuthorizationLoaded());
        assertTrue(principal.getRoles().isEmpty());
        assertTrue(principal.getPermissions().isEmpty());
    }
}
