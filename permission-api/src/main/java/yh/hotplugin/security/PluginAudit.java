package yh.hotplugin.security;

import org.noear.solon.core.handle.Context;
import yh.hotplugin.security.api.*;

import java.util.UUID;
import java.util.regex.Pattern;

public final class PluginAudit {
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|token|authorization|cookie|secret|access[_-]?key|connectionPassword)(\\s*[=:]\\s*)([^,&;\\s]+)");

    private PluginAudit() {
    }

    public interface Action<T> {
        T run() throws Throwable;
    }

    public static <T> T execute(Context c, String pluginName, String feature, BusinessType type, String permission, Action<T> action) throws Throwable {
        SecurityPrincipal p = PluginSecurity.requirePermission(c, permission);
        long start = System.currentTimeMillis();
        String requestId = requestId(c);
        c.attrSet(PluginSecurity.PLUGIN_ATTRIBUTE, pluginName);
        try {
            T result = action.run();
            AuditGateway.operation(operation(c, p, pluginName, feature, type, permission, requestId, true, "", System.currentTimeMillis() - start));
            return result;
        } catch (Throwable error) {
            long cost = System.currentTimeMillis() - start;
            AuditGateway.operation(operation(c, p, pluginName, feature, type, permission, requestId, false, error.getMessage(), cost));
            AuditGateway.error(new ErrorAuditEvent(pluginName, feature, requestId, c.method(), c.path(), clientIp(c), p, 500, error));
            throw error;
        }
    }

    public static void error(Context c, String pluginName, String feature, int status, Throwable error) {
        SecurityPrincipal p = c.attr(PluginSecurity.PRINCIPAL_ATTRIBUTE);
        AuditGateway.error(new ErrorAuditEvent(pluginName, feature, requestId(c), c.method(), c.path(), clientIp(c), p, status, error));
    }

    private static OperationAuditEvent operation(Context c, SecurityPrincipal p, String plugin, String feature, BusinessType type, String permission, String requestId, boolean success, String error, long cost) {
        return new OperationAuditEvent(plugin, feature, permission, type, requestId, c.method(), c.path(), clientIp(c), p, sanitize(c.queryString()), success, cut(error, 2000), cost);
    }

    private static String requestId(Context c) {
        String id = c.attr(PluginSecurity.REQUEST_ID_ATTRIBUTE);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "");
            c.attrSet(PluginSecurity.REQUEST_ID_ATTRIBUTE, id);
        }
        return id;
    }

    private static String clientIp(Context c) {
        String ip = c.header("X-Forwarded-For");
        if (ip == null || ip.trim().isEmpty()) ip = c.header("X-Real-IP");
        if (ip == null || ip.trim().isEmpty()) ip = c.remoteIp();
        if (ip != null && ip.contains(",")) ip = ip.substring(0, ip.indexOf(',')).trim();
        return ip;
    }

    public static String sanitize(String value) {
        if (value == null) return "";
        return cut(SECRET.matcher(value).replaceAll("$1$2***"), 2000);
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
