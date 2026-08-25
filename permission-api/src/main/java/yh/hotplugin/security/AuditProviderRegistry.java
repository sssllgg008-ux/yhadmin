package yh.hotplugin.security;

import yh.hotplugin.security.api.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuditProviderRegistry {
    private static final AuditProvider UNAVAILABLE=new AuditProvider(){public void operation(OperationAuditEvent e){throw new IllegalStateException("AUDIT_PROVIDER_UNAVAILABLE");}public void error(ErrorAuditEvent e){throw new IllegalStateException("AUDIT_PROVIDER_UNAVAILABLE");}};
    private static final AtomicReference<AuditProvider> CURRENT=new AtomicReference<AuditProvider>(UNAVAILABLE);
    private static final AtomicInteger IN_FLIGHT=new AtomicInteger();
    private AuditProviderRegistry(){}
    public static AuditProvider get(){return CURRENT.get();}
    public static boolean available(){return CURRENT.get()!=UNAVAILABLE;}
    public static void install(AuditProvider provider){CURRENT.set(provider==null?UNAVAILABLE:provider);AuditGateway.signal();}
    public static void uninstall(AuditProvider provider){CURRENT.compareAndSet(provider,UNAVAILABLE);long until=System.currentTimeMillis()+5000;while(IN_FLIGHT.get()>0&&System.currentTimeMillis()<until){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}}
    static void operation(OperationAuditEvent event){AuditProvider provider=CURRENT.get();if(provider==UNAVAILABLE)throw new IllegalStateException("AUDIT_PROVIDER_UNAVAILABLE");IN_FLIGHT.incrementAndGet();try{if(provider!=CURRENT.get())throw new IllegalStateException("AUDIT_PROVIDER_SWITCHED");provider.operation(event);}finally{IN_FLIGHT.decrementAndGet();}}
    static void error(ErrorAuditEvent event){AuditProvider provider=CURRENT.get();if(provider==UNAVAILABLE)throw new IllegalStateException("AUDIT_PROVIDER_UNAVAILABLE");IN_FLIGHT.incrementAndGet();try{if(provider!=CURRENT.get())throw new IllegalStateException("AUDIT_PROVIDER_SWITCHED");provider.error(event);}finally{IN_FLIGHT.decrementAndGet();}}
}
