package yh.hotplugin.security.tenant;

import org.junit.jupiter.api.*;
import yh.hotplugin.security.api.SecurityPrincipal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {
    @AfterEach void clear(){TenantContext.clear();}
    @Test void missingTenantFailsClosed(){assertEquals("TENANT_CONTEXT_MISSING",assertThrows(IllegalStateException.class,TenantContext::requiredTenantId).getMessage());}
    @Test void nestedScopesRestorePreviousIdentity(){try(TenantContext.Scope outer=TenantContext.open(new SecurityPrincipal(7,11,"one"))){assertEquals(11,TenantContext.requiredTenantId());try(TenantContext.Scope inner=TenantContext.open(22,8,"two")){assertEquals(22,TenantContext.requiredTenantId());}assertEquals(11,TenantContext.requiredTenantId());}assertNull(TenantContext.tenantId());}
    @Test void wrappedTaskPropagatesThenClears() throws Exception {ExecutorService executor=Executors.newSingleThreadExecutor();try(TenantContext.Scope ignored=TenantContext.open(33,9,"async")){Future<Long> value=executor.submit(TenantContext.wrap(() -> TenantContext.requiredTenantId()));assertEquals(33,value.get().longValue());}finally{executor.shutdownNow();}}
}
