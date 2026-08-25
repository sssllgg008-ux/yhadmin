package yh.hotplugin.security;

import yh.hotplugin.security.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Host-classloader-owned bounded audit buffer. */
public final class AuditGateway {
    private static final int CAPACITY=4096;
    private static final LinkedBlockingDeque<Entry> QUEUE=new LinkedBlockingDeque<Entry>(CAPACITY);
    private static final AtomicLong DROPPED=new AtomicLong();
    private static final Thread WORKER;
    static {WORKER=new Thread(AuditGateway::run,"plugin-audit-gateway");WORKER.setDaemon(true);WORKER.setContextClassLoader(AuditGateway.class.getClassLoader());WORKER.start();}
    private AuditGateway(){}
    public static void operation(OperationAuditEvent event){if(event!=null&&!QUEUE.offerLast(new Entry(event,null)))DROPPED.incrementAndGet();}
    public static void error(ErrorAuditEvent event){if(event==null)return;Entry e=new Entry(null,event);if(!QUEUE.offerFirst(e)){QUEUE.pollLast();if(!QUEUE.offerFirst(e))DROPPED.incrementAndGet();}}
    public static long droppedCount(){return DROPPED.get();}
    static void signal(){WORKER.interrupt();}
    private static void run(){for(;;){try{Entry e=QUEUE.takeFirst();if(!AuditProviderRegistry.available()){QUEUE.offerFirst(e);Thread.sleep(250);continue;}try{if(e.error!=null)AuditProviderRegistry.error(e.error);else AuditProviderRegistry.operation(e.operation);}catch(Throwable failure){QUEUE.offerFirst(e);Thread.sleep(250);}}catch(InterruptedException ignored){}}}
    private static final class Entry{final OperationAuditEvent operation;final ErrorAuditEvent error;Entry(OperationAuditEvent o,ErrorAuditEvent e){operation=o;error=e;}}
}
