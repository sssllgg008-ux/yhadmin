package yh.hotplugin.security;

import java.util.concurrent.*;

public final class DataResourceRegistry {
    private static final ConcurrentMap<String,Registration> RESOURCES=new ConcurrentHashMap<String,Registration>();
    private DataResourceRegistry(){}
    public static void register(String owner,String resource,DataResourcePolicy policy){if(blank(owner)||blank(resource)||policy==null)throw new IllegalArgumentException("owner/resource/policy");Registration next=new Registration(owner,policy),old=RESOURCES.putIfAbsent(resource,next);if(old!=null&&!old.owner.equals(owner))throw new IllegalStateException("Data resource already owned by "+old.owner+": "+resource);}
    public static DataResourcePolicy get(String resource){Registration r=RESOURCES.get(resource);return r==null?null:r.policy;}
    public static boolean contains(String resource){return RESOURCES.containsKey(resource);}
    public static void unregisterOwner(String owner){RESOURCES.entrySet().removeIf(e->e.getValue().owner.equals(owner));}
    private static boolean blank(String v){return v==null||v.trim().isEmpty();}
    private static final class Registration{final String owner;final DataResourcePolicy policy;Registration(String o,DataResourcePolicy p){owner=o;policy=p;}}
}
