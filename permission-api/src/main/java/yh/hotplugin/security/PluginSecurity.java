package yh.hotplugin.security;

import org.noear.solon.core.handle.Context;
import yh.hotplugin.security.api.*;

public final class PluginSecurity {
    public static final String PRINCIPAL_ATTRIBUTE = "security.principal", REQUEST_ID_ATTRIBUTE = "security.requestId", PLUGIN_ATTRIBUTE = "security.pluginName";

    private PluginSecurity() {
    }

    public static SecurityPrincipal requirePrincipal(Context context) {
        if (!PermissionProviderRegistry.available())
            throw new PluginAccessException(503, "PERMISSION_PLUGIN_UNAVAILABLE");
        SecurityPrincipal p = context.attr(PRINCIPAL_ATTRIBUTE);
        if (p == null) throw new PluginAccessException(401, "UNAUTHORIZED");
        return p;
    }

    public static SecurityPrincipal requirePermission(Context context, String permission) {
        SecurityPrincipal p = requirePrincipal(context);
        if (!PermissionProviderRegistry.get().isAllowed(p, permission))
            throw new PluginAccessException(403, "FORBIDDEN");
        return p;
    }

    public static DataScopeResult requireDataScope(Context context, String permission, String resource) {
        SecurityPrincipal p = requirePermission(context, permission);
        if (!DataResourceRegistry.contains(resource))
            throw new IllegalStateException("UNREGISTERED_DATA_RESOURCE: " + resource);
        DataScopeResult result = DataScopeProviderRegistry.get().resolve(p, resource);
        if (result == null || result.getScope() == DataScopeResult.Scope.DENY)
            throw new PluginAccessException(403, "DATA_SCOPE_DENIED");
        return result;
    }
}
