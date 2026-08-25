package yh.hotplugin.system.infrastructure;

import org.junit.jupiter.api.Test;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import static org.junit.jupiter.api.Assertions.*;

class SystemTenantQuotaProviderTest {
    @Test void combinedLimitQueryIsValidSql() {
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(SystemTenantQuotaProvider.LIMITS_SQL));
    }

    @Test void usesPlanDefaultWhenNoRoutePolicyMatches() {
        SystemTenantQuotaProvider.RouteLimit result = SystemTenantQuotaProvider.policy(null, 600);
        assertEquals("plan-default", result.key);
        assertEquals(600, result.limit);
    }

    @Test void preservesMatchedPolicyAndUnlimitedLimit() {
        SystemTenantQuotaProvider.RouteLimit result = SystemTenantQuotaProvider.policy("42:-1", 600);
        assertEquals("policy-42", result.key);
        assertEquals(-1, result.limit);
    }

    @Test void decodesDatabaseBooleanValues() {
        assertTrue(SystemTenantQuotaProvider.truth(Boolean.TRUE));
        assertTrue(SystemTenantQuotaProvider.truth(1));
        assertFalse(SystemTenantQuotaProvider.truth(0));
        assertFalse(SystemTenantQuotaProvider.truth(null));
    }
}
