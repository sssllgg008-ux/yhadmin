package yh.hotplugin.security.tenant;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TenantContributorRegistry {
    private static final Map<String, TenantLifecycleContributor> LIFE = new ConcurrentHashMap<String, TenantLifecycleContributor>();
    private static final Map<String, TenantDataContributor> DATA = new ConcurrentHashMap<String, TenantDataContributor>();
    private static final Map<String, TenantQuotaContributor> QUOTA = new ConcurrentHashMap<String, TenantQuotaContributor>();
    private TenantContributorRegistry() { }

    public static void register(TenantLifecycleContributor value) { put(LIFE, value.pluginName(), value); }
    public static void register(TenantDataContributor value) { put(DATA, value.pluginName(), value); }
    public static void register(TenantQuotaContributor value) { put(QUOTA, value.pluginName(), value); }
    private static <T> void put(Map<String,T> map, String name, T value) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("pluginName");
        if (map.putIfAbsent(name, value) != null) throw new IllegalStateException("TENANT_CONTRIBUTOR_ALREADY_REGISTERED: " + name);
    }
    public static List<TenantLifecycleContributor> lifecycle() { return snapshot(LIFE); }
    public static List<TenantDataContributor> data() { return snapshot(DATA); }
    public static List<TenantQuotaContributor> quota() { return snapshot(QUOTA); }
    private static <T> List<T> snapshot(Map<String,T> map) { return Collections.unmodifiableList(new ArrayList<T>(map.values())); }
    public static void unregister(String pluginName) { LIFE.remove(pluginName); DATA.remove(pluginName); QUOTA.remove(pluginName); }
}
