package yh.hotplugin.security;

import org.noear.solon.core.handle.*;
import yh.hotplugin.security.api.SecurityPrincipal;
import yh.hotplugin.security.tenant.TenantContext;

import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

public final class HostSecurityFilter implements Filter {
    private static final String CORE_PLUGIN = "pluginSystem";
    private static final long MAINTENANCE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final Set<String> PUBLIC_PATHS = new HashSet<String>(Arrays.asList("/", "/index.html", "/favicon.ico", "/login", "/auth/login", "/auth/tenant/default", "/auth/tenants", "/captchaImage", "/auth/captcha", "/health"));
    private static final Set<String> PLUGIN_MANAGEMENT = new HashSet<String>(Arrays.asList("/api/plugins", "/api/plugins/register", "/api/plugins/start", "/api/plugins/stop", "/api/plugins/unload", "/api/plugins/remove", "/api/plugins/upload", "/api/plugins/core-status"));
    private static final Set<String> MAINTENANCE_PATHS = new HashSet<String>(Arrays.asList(
            "/api/plugins/start", "/api/plugins/unload", "/api/plugins/remove",
            "/api/plugins/upload", "/api/plugins/register", "/api/plugins/core-status"));
    private final ConcurrentMap<String, MaintenancePermit> maintenancePermits = new ConcurrentHashMap<String, MaintenancePermit>();

    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        String path = ctx.path();
        if (isPublic(path)) {
            chain.doFilter(ctx);
            return;
        }
        ctx.attrSet(PluginSecurity.REQUEST_ID_ATTRIBUTE, UUID.randomUUID().toString().replace("-", ""));
        RequestPerformance.start(ctx);
        String token = token(ctx);
        if (!PermissionProviderRegistry.available()) {
            if (isCoreMaintenance(ctx) && validMaintenancePermit(token, ctx.remoteIp())) {
                chain.doFilter(ctx);
                if (PermissionProviderRegistry.available()) maintenancePermits.remove(hash(token));
                return;
            }
            reject(ctx, 503, "PERMISSION_PLUGIN_UNAVAILABLE");
            return;
        }
        SecurityPrincipal principal = PermissionProviderRegistry.get().principal(token);
        if (principal == null) {
            reject(ctx, 401, "UNAUTHORIZED");
            return;
        }
        if (PLUGIN_MANAGEMENT.contains(path) && !PermissionProviderRegistry.get().isAllowed(principal, "plugin:manage")) {
            reject(ctx, 403, "FORBIDDEN");
            return;
        }
        boolean stoppingCore = "/api/plugins/stop".equals(path) && CORE_PLUGIN.equals(ctx.param("name"));
        ctx.attrSet(PluginSecurity.PRINCIPAL_ATTRIBUTE, principal);
        try (TenantContext.Scope ignored = TenantContext.open(principal)) {
            if (principal.isPasswordChangeRequired() && !passwordChangePath(path)) {
                reject(ctx, 409, "PASSWORD_CHANGE_REQUIRED");
                return;
            }
            if (TenantQuotaRegistry.get() != null) {
                long quotaStarted = RequestPerformance.begin();
                TenantQuotaRegistry.get().checkRequest(principal, ctx.method(), path);
                RequestPerformance.record(ctx, "quota", quotaStarted);
            }
            chain.doFilter(ctx);
            if (stoppingCore && !PermissionProviderRegistry.available()) {
                maintenancePermits.put(hash(token), new MaintenancePermit(ctx.remoteIp(),
                        System.currentTimeMillis() + MAINTENANCE_TTL_MILLIS));
            }
        } catch (PluginAccessException e) {
            reject(ctx, e.getStatus(), e.getMessage());
        } catch (TenantLimitException e) {
            ctx.status(e.getStatus());
            Map<String,Object> body = new LinkedHashMap<String,Object>();
            body.put("msg", e.getMessage());
            if (e.getQuotaKey() != null) {
                body.put("quotaKey", e.getQuotaKey());
                body.put("limit", e.getLimit());
                body.put("current", e.getCurrent());
            }
            ctx.render(body);
            ctx.setHandled(true);
        } finally {
            double totalMillis = RequestPerformance.totalMillis(ctx);
            if (totalMillis > 100D) System.out.println(RequestPerformance.slowLog(ctx, totalMillis));
            // Defensive cleanup protects pooled request threads even if a plugin opens a bad nested scope.
            TenantContext.clear();
        }
    }

    private boolean passwordChangePath(String path) {
        return "/getInfo".equals(path) || "/auth/getInfo".equals(path) || "/auth/userinfo".equals(path)
                || "/getRouters".equals(path) || "/system/menu/current".equals(path)
                || "/system/user/profile".equals(path) || "/system/user/profile/password".equals(path)
                || "/logout".equals(path) || "/auth/logout".equals(path);
    }

    private boolean isCoreMaintenance(Context ctx) {
        return MAINTENANCE_PATHS.contains(ctx.path()) && CORE_PLUGIN.equals(ctx.param("name"));
    }

    private boolean validMaintenancePermit(String token, String ip) {
        String key = hash(token);
        MaintenancePermit permit = maintenancePermits.get(key);
        boolean valid = permit != null && permit.expiresAt >= System.currentTimeMillis() && Objects.equals(permit.ip, ip);
        if (!valid && permit != null) maintenancePermits.remove(key, permit);
        return valid;
    }

    private String hash(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class MaintenancePermit {
        final String ip;
        final long expiresAt;

        MaintenancePermit(String ip, long expiresAt) {
            this.ip = ip;
            this.expiresAt = expiresAt;
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path) || path.startsWith("/static/") || path.startsWith("/js/");
    }

    private String token(Context ctx) {
        String value = ctx.header("Authorization");
        if (value == null || value.trim().isEmpty()) value = ctx.header("satoken");
        if (value != null && value.startsWith("Bearer ")) value = value.substring(7);
        return value == null ? "" : value.trim();
    }

    private void reject(Context ctx, int status, String message) throws Throwable {
        ctx.status(status);
        ctx.render(Collections.singletonMap("msg", message));
        ctx.setHandled(true);
    }
}
