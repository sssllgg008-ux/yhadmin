package yh.hotplugin.system.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrudHttpApiPerformanceTest {
    @Test void derivesExactMenuTotalWhenFirstPageIsNotFull() {
        assertTrue(CrudHttpApi.canDeriveTotal("menu", 1, 10000, 137));
        assertTrue(CrudHttpApi.canDeriveTotal("menu", 1, 10000, 0));
    }

    @Test void retainsCountFallbackAtLimitAndForOtherPagingCases() {
        assertFalse(CrudHttpApi.canDeriveTotal("menu", 1, 10000, 10000));
        assertFalse(CrudHttpApi.canDeriveTotal("menu", 2, 10000, 10));
        assertFalse(CrudHttpApi.canDeriveTotal("user", 1, 20, 10));
    }
}
