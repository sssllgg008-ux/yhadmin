package yh.hotplugin.security;

import java.util.*;

public final class PermissionPluginSettings {
    private static volatile Map<String, String> values = Collections.emptyMap();

    private PermissionPluginSettings() {
    }

    public static void install(Map<String, String> s) {
        values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(s));
    }

    public static String get(String k, String d) {
        String v = values.get(k);
        return v == null || v.trim().isEmpty() ? d : v.trim();
    }
}
