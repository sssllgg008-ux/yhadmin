package yh.hotplugin.security;

import yh.hotplugin.security.api.TenantQuotaProvider;
import java.util.concurrent.atomic.AtomicReference;

public final class TenantQuotaRegistry {
    private static final AtomicReference<TenantQuotaProvider> CURRENT=new AtomicReference<TenantQuotaProvider>();
    private TenantQuotaRegistry(){}
    public static void install(TenantQuotaProvider p){if(p==null||!CURRENT.compareAndSet(null,p))throw new IllegalStateException("TENANT_QUOTA_PROVIDER_ALREADY_INSTALLED");}
    public static TenantQuotaProvider get(){return CURRENT.get();}
    public static void uninstall(TenantQuotaProvider p){CURRENT.compareAndSet(p,null);}
}
