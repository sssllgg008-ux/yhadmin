package yh.hotplugin.system.integration;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.*;
import cn.dev33.satoken.solon.model.SaContextForSolon;
import cn.dev33.satoken.serializer.impl.SaSerializerTemplateForJdkUseBase64;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.noear.dami2.Dami;
import org.noear.redisx.RedisClient;
import org.noear.solon.Solon;
import org.noear.solon.core.*;
import yh.hotplugin.security.*;
import yh.hotplugin.security.api.*;
import yh.hotplugin.security.tenant.*;
import yh.hotplugin.system.application.SystemDataScopeProvider;
import yh.hotplugin.system.application.SystemPermissionProvider;
import yh.hotplugin.system.infrastructure.JdbcAuthorizationRepository;
import yh.hotplugin.system.infrastructure.JdbcCrudRepository;
import yh.hotplugin.system.infrastructure.JdbcManagementRepository;
import yh.hotplugin.system.infrastructure.SystemAuditProvider;
import yh.hotplugin.system.infrastructure.SystemTenantQuotaProvider;
import yh.hotplugin.system.infrastructure.SystemTenantContributor;
import yh.hotplugin.system.infrastructure.TenantLifecycleTaskService;
import yh.hotplugin.security.tenant.TenantContext;
import yh.hotplugin.system.infrastructure.ApiTimeFormatter;

import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

public final class SecureSystemSolonPlugin implements Plugin {
    public static final String CHECK_TOPIC = "plugin.system.permission.check", LIST_TOPIC = "plugin.system.permission.list";
    private static final String[] ROUTES = {"/login", "/auth/login", "/auth/tenant/default", "/auth/tenants", "/logout", "/auth/logout", "/getInfo", "/auth/getInfo", "/auth/userinfo", "/getRouters", "/system/menu/current", "/system/tenant/currentPlan", "/captchaImage", "/auth/captcha", "/dashboard/stats", "/plugin/system/health", "/permission/**"};
    private final ObjectMapper json = new ObjectMapper();
    private JdbcAuthorizationRepository repository;
    private SystemPermissionProvider provider;
    private SystemDataScopeProvider dataScopeProvider;
    private SystemAuditProvider auditProvider;
    private ManagementHttpApi management;
    private CrudHttpApi crudApi;
    private JdbcCrudRepository crudRepository;
    private RedisClient redisClient;
    private SystemTenantQuotaProvider quotaProvider;
    private SystemTenantContributor tenantContributor;
    private TenantLifecycleTaskService tenantLifecycleTasks;
    private SaasHttpApi saasApi;

    public void start(AppContext context) {
        String url = cfg("plugin.system.datasource.url", "spring.datasource.url", null);
        if (url == null) throw new IllegalStateException("Missing plugin.system.datasource.url");
        repository = new JdbcAuthorizationRepository(cfg("plugin.system.datasource.driver", "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"), url, cfg("plugin.system.datasource.username", "spring.datasource.username", ""), cfg("plugin.system.datasource.password", "spring.datasource.password", ""), 1);
        try {
            Properties p = new Properties();
            p.setProperty("server", cfg("plugin.system.redis.host", null, "127.0.0.1") + ":" + cfg("plugin.system.redis.port", null, "6379"));
            p.setProperty("password", cfg("plugin.system.redis.password", null, ""));
            p.setProperty("db", cfg("plugin.system.redis.database", null, "0"));
            redisClient = new RedisClient(p);
            if (!"PONG".equalsIgnoreCase(redisClient.jedis().ping()))
                throw new IllegalStateException("Redis ping failed");
            SaTokenDao dao = new SaTokenDaoForRedisx(redisClient);
            SaManager.setSaTokenContext(new SaContextForSolon());
            // Auto-configuration is intentionally excluded from the shaded hot-plugin JAR.
            // Keep session serialization explicit and stable across stop/start and classloader reloads.
            SaManager.setSaSerializerTemplate(new SaSerializerTemplateForJdkUseBase64());
            SaManager.setSaTokenDao(dao);
            SaManager.setConfig(new SaTokenConfig().setTokenName(cfg("plugin.system.sa-token.token-name", null, "satoken")).setTimeout(Long.parseLong(cfg("plugin.system.sa-token.timeout", null, "28800"))).setIsConcurrent(true).setIsShare(true));
            SaManager.setStpInterface(new SystemStpInterface(repository));
            provider = new SystemPermissionProvider(repository, dao);
        } catch (RuntimeException e) {
            repository.close();
            throw new IllegalStateException("Redis is mandatory and unavailable", e);
        }
        dataScopeProvider = new SystemDataScopeProvider(repository);
        JdbcManagementRepository managementRepository = new JdbcManagementRepository(repository);
        crudRepository = new JdbcCrudRepository(repository);
        // Plugin bootstrap has no authenticated principal. Schema/default-data
        // maintenance is an explicit platform operation and must never rely on
        // a default tenant fallback.
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, "pluginSystem-bootstrap")) {
            crudRepository.ensureCaptchaConfig();
        }
        quotaProvider = new SystemTenantQuotaProvider(repository, redisClient);
        tenantContributor = new SystemTenantContributor(managementRepository, repository);
        auditProvider = new SystemAuditProvider(crudRepository);
        registerDataResources();
        PermissionProviderRegistry.install(provider);
        DataScopeProviderRegistry.install(dataScopeProvider);
        AuditProviderRegistry.install(auditProvider);
        TenantQuotaRegistry.install(quotaProvider);
        TenantContributorRegistry.register((TenantLifecycleContributor) tenantContributor);
        TenantContributorRegistry.register((TenantDataContributor) tenantContributor);
        TenantContributorRegistry.register((TenantQuotaContributor) tenantContributor);
        // Resume persisted lifecycle work only after every local contributor is visible.
        // Otherwise a task may race plugin startup and be incorrectly marked WAITING_PLUGIN.
        tenantLifecycleTasks = new TenantLifecycleTaskService(repository, managementRepository);
        management = new ManagementHttpApi(json, managementRepository, crudRepository, provider);
        crudApi = new CrudHttpApi(json, crudRepository, managementRepository, provider, dataScopeProvider, tenantLifecycleTasks);
        saasApi = new SaasHttpApi(json, repository, managementRepository, tenantLifecycleTasks, provider,
                cfg("plugin.system.backup.directory", null, "data/tenant-backups"));
        registerBus();
        registerRoutes();
        management.register();
        crudApi.register();
        saasApi.register();
    }

    private void registerDataResources() {
        for (String resource : new String[]{"user", "login-log", "oper-log", "operation-log", "error-log"})
            DataResourceRegistry.register("pluginSystem", resource, DataResourcePolicy.departmentAndCreator());
    }

    private void registerBus() {
        Dami.bus().<PermissionCheck, Boolean>listen(CHECK_TOPIC, (e, r, s) -> {
            SecurityPrincipal p = r == null ? null : provider.principal(r.getToken());
            try {
                s.complete(p != null && provider.isAllowed(p, r.getPermission()));
            } catch (Throwable x) {
                throw new IllegalStateException(x);
            }
        });
        Dami.bus().<String, Object>listen(LIST_TOPIC, (e, token, s) -> {
            SecurityPrincipal p = provider.principal(token);
            try {
                s.complete(p == null ? Collections.emptySet() : provider.permissions(p));
            } catch (Throwable x) {
                throw new IllegalStateException(x);
            }
        });
    }

    private void registerRoutes() {
        Solon.app().router().post("/login", c -> login(c, false));
        Solon.app().router().post("/auth/login", c -> login(c, true));
        Solon.app().router().get("/auth/tenant/default", this::defaultTenant);
        Solon.app().router().get("/auth/tenants", this::activeTenants);
        Solon.app().router().post("/logout", this::logout);
        Solon.app().router().post("/auth/logout", this::logout);
        Solon.app().router().get("/getInfo", this::info);
        Solon.app().router().get("/auth/getInfo", this::info);
        Solon.app().router().get("/auth/userinfo", this::authUserInfo);
        Solon.app().router().get("/getRouters", this::menus);
        Solon.app().router().get("/system/menu/current", this::menus);
        Solon.app().router().get("/system/tenant/currentPlan", c -> c.render(response(200, "操作成功", quotaProvider.currentPlan(principal(c)))));
        Solon.app().router().get("/captchaImage", this::captcha);
        Solon.app().router().get("/auth/captcha", this::captcha);
        Solon.app().router().get("/dashboard/stats", this::dashboard);
        Solon.app().router().get("/plugin/system/health", c -> c.render(response(200, "ok", Collections.singletonMap("available", true))));
        Solon.app().router().get("/permission/**", this::staticFile);
    }

    private void staticFile(org.noear.solon.core.handle.Context c) throws Exception {
        String path = c.path();
        if (path.contains("..")) {
            c.status(400);
            return;
        }
        String relative = path.substring("/permission/".length());
        if (relative.isEmpty()) relative = "index.html";
        java.io.InputStream in = getClass().getResourceAsStream("/static/permission/" + relative);
        if (in == null) {
            c.status(404);
            return;
        }
        c.contentType(contentType(relative));
        c.output(in);
    }

    private String contentType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html;charset=UTF-8";
        if (p.endsWith(".js")) return "application/javascript;charset=UTF-8";
        if (p.endsWith(".css")) return "text/css;charset=UTF-8";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    private void login(org.noear.solon.core.handle.Context c, boolean full) throws Throwable {
        LoginRequest r = json.readValue(c.body(), LoginRequest.class);
        long tenant = r.getTenantId() == null ? 1 : r.getTenantId();
        long start = System.currentTimeMillis();
        String username = r.getUsername() == null ? "unknown" : r.getUsername();
        try (TenantContext.Scope ignored = TenantContext.open(tenant, 0, username)) {
        try {
            verifyCaptcha(r);
            LoginResult result = provider.login(new LoginCommand(tenant, r.getUsername(), r.getPassword()));
            enrichLogin(c, tenant, r.getUsername());
            crudApi.auditAuthentication(c, result.getPrincipal(), true, "", System.currentTimeMillis() - start);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("token", result.getToken());
            if (full) {
                data.put("tokenName", result.getTokenName());
                data.put("user", user(result.getPrincipal()));
                data.put("roles", provider.roles(result.getPrincipal()));
                data.put("permissions", provider.permissions(result.getPrincipal()));
            }
            c.render(response(200, "登录成功", data));
        } catch (SecurityException ex) {
            enrichLogin(c, tenant, r.getUsername());
            crudApi.auditAuthentication(c, new SecurityPrincipal(0, tenant, username), false, ex.getMessage(), System.currentTimeMillis() - start);
            c.status(401);
            c.render(response(401, ex.getMessage(), null));
        } catch (Throwable ex) {
            System.err.println("[pluginSystem] Login failed unexpectedly: " + ex.getMessage());
            ex.printStackTrace();
            c.status(500);
            c.render(response(500, "权限服务内部错误", null));
        }
        }
    }

    private void enrichLogin(org.noear.solon.core.handle.Context c, long tenant, String username) {
        if (username == null) return;
        String ip = c.header("X-Forwarded-For");
        if (ip == null || ip.trim().isEmpty()) ip = c.header("X-Real-IP");
        if (ip == null || ip.trim().isEmpty()) ip = c.remoteIp();
        if (ip != null && ip.contains(",")) ip = ip.substring(0, ip.indexOf(',')).trim();
        String ua = c.header("User-Agent");
        repository.enrichLatestLogin(tenant, username, ip, isLocalIp(ip) ? "内网IP" : "未知", browser(ua), operatingSystem(ua));
    }

    private boolean isLocalIp(String ip) {
        return ip == null || ip.equals("127.0.0.1") || ip.equals("::1") || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    private String browser(String ua) {
        if (ua == null) return "未知";
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) return "Safari";
        return "其他";
    }

    private String operatingSystem(String ua) {
        if (ua == null) return "未知";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Mac OS")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "其他";
    }

    private void defaultTenant(org.noear.solon.core.handle.Context c) throws Throwable {
        c.render(response(200, "操作成功", crudApi.defaultTenant()));
    }

    private void activeTenants(org.noear.solon.core.handle.Context c) throws Throwable {
        c.render(response(200, "操作成功", crudApi.activeTenants()));
    }

    private void logout(org.noear.solon.core.handle.Context c) throws Throwable {
        provider.logout(principal(c));
        c.render(response(200, "退出成功", null));
    }

    private void info(org.noear.solon.core.handle.Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Map<String, Object> full = crudApi.userProfile(p);
        data.put("user", full == null ? user(p) : full);
        data.put("roles", provider.roles(p));
        data.put("permissions", provider.permissions(p));
        data.put("menus", repository.findRouterTree(p.getTenantId(), p.getUserId()));
        data.put("userId", p.getUserId());
        data.put("username", p.getUsername());
        data.put("nickname", full == null ? p.getUsername() : full.get("nickname"));
        c.render(response(200, "操作成功", data));
    }

    private void menus(org.noear.solon.core.handle.Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        c.render(response(200, "操作成功", repository.findRouterTree(p.getTenantId(), p.getUserId())));
    }

    private void captcha(org.noear.solon.core.handle.Context c) throws Throwable {
        long tenantId = positiveLong(c.param("tenantId"), 1L);
        boolean enabled = captchaEnabled(tenantId);
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("captchaEnabled", enabled);
        if (enabled) {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            d.put("uuid", uuid);
            String code = String.format(Locale.ROOT, "%04d", new java.security.SecureRandom().nextInt(10000));
            provider.putCaptcha(uuid, code, 120);
            d.put("img", captchaImage(code));
        } else {
            d.put("uuid", "");
            d.put("img", "");
        }
        c.render(response(200, "操作成功", d));
    }

    private void dashboard(org.noear.solon.core.handle.Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, Long> d = crudApi.dashboard(p);
        d.put("onlineCount", provider.onlineCount(p.getTenantId()));
        c.render(response(200, "操作成功", d));
    }

    private void verifyCaptcha(LoginRequest r) {
        long tenantId = r.getTenantId() == null ? 1L : r.getTenantId();
        if (captchaEnabled(tenantId) && !provider.consumeCaptcha(r.getUuid(), r.getCode()))
            throw new SecurityException("验证码错误或已过期");
    }

    private boolean captchaEnabled(long tenantId) {
        String fallback = cfg("plugin.system.captcha.enabled", null, "false");
        // /captchaImage is anonymous, so bind the tenant selected on the login
        // page explicitly. The scope restores any outer login/request context.
        try (TenantContext.Scope ignored = TenantContext.open(tenantId, 0, "captcha")) {
            String value = crudRepository.configValue(tenantId, "sys.account.captchaEnabled", fallback);
            return "true".equalsIgnoreCase(value) || "1".equals(value)
                    || "y".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
                    || "on".equalsIgnoreCase(value);
        }
    }

    private long positiveLong(String value, long defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String captchaImage(String code) throws Exception {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, 120, 40);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            g.setColor(new Color(40, 90, 170));
            g.drawString(code, 27, 29);
            g.setColor(new Color(150, 170, 200));
            for (int i = 0; i < 6; i++) g.drawLine(i * 23, 0, 120 - i * 17, 39);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private void authUserInfo(org.noear.solon.core.handle.Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("tokenName", "satoken");
        d.put("userId", p.getUserId());
        d.put("username", p.getUsername());
        Map<String, Object> u = crudApi.userProfile(p);
        d.put("nickname", u == null ? p.getUsername() : u.get("nickname"));
        d.put("roles", provider.roles(p));
        d.put("permissions", provider.permissions(p));
        c.render(response(200, "操作成功", d));
    }

    private SecurityPrincipal principal(org.noear.solon.core.handle.Context c) {
        return c.attr("security.principal");
    }

    private Map<String, Object> user(SecurityPrincipal p) {
        Map<String, Object> u = new LinkedHashMap<String, Object>();
        u.put("id", p.getUserId());
        u.put("userId", p.getUserId());
        u.put("username", p.getUsername());
        u.put("userName", p.getUsername());
        u.put("tenantId", p.getTenantId());
        u.put("passwordChangeRequired", p.isPasswordChangeRequired());
        return u;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> response(int code, String msg, Object data) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("code", code);
        r.put("msg", msg);
        if (data != null) {
            Object normalized = ApiTimeFormatter.normalize(data);
            if (normalized instanceof Map) r.putAll((Map<String, Object>) normalized);
            else r.put("data", normalized);
        }
        return r;
    }

    public void stop() {
        if (provider != null) provider.stopAccepting();
        PermissionProviderRegistry.uninstall(provider);
        DataScopeProviderRegistry.uninstall(dataScopeProvider);
        AuditProviderRegistry.uninstall(auditProvider);
        TenantQuotaRegistry.uninstall(quotaProvider);
        TenantContributorRegistry.unregister("pluginSystem");
        DataResourceRegistry.unregisterOwner("pluginSystem");
        Dami.bus().unlisten(CHECK_TOPIC);
        Dami.bus().unlisten(LIST_TOPIC);
        for (String route : ROUTES) Solon.app().router().remove(route);
        for (String route : ManagementHttpApi.ROUTES) Solon.app().router().remove(route);
        for (String resource : CrudHttpApi.RESOURCES) {
            String base = "/system/" + resource;
            Solon.app().router().remove(base);
            Solon.app().router().remove(base + "/list");
            Solon.app().router().remove(base + "/{id}");
            Solon.app().router().remove(base + "/{ids}");
        }
        for (String route : CrudHttpApi.EXTRA_ROUTES) Solon.app().router().remove(route);
        for (String route : SaasHttpApi.ROUTES) Solon.app().router().remove(route);
        if (saasApi != null) saasApi.close();
        TenantContext.clear();
        if (tenantLifecycleTasks != null) tenantLifecycleTasks.close();
        if (provider != null) provider.close();
        if (repository != null) repository.close();
        if (redisClient != null) try {
            redisClient.close();
        } catch (Exception ignored) {
        }
        provider = null;
        dataScopeProvider = null;
        auditProvider = null;
        quotaProvider = null;
        tenantContributor = null;
        tenantLifecycleTasks = null;
        saasApi = null;
        management = null;
        crudApi = null;
        repository = null;
        redisClient = null;
    }

    private String cfg(String primary, String fallback, String def) {
        String v = PermissionPluginSettings.get(primary, null);
        if ((v == null || v.trim().isEmpty())) v = Solon.cfg().get(primary);
        if ((v == null || v.trim().isEmpty()) && fallback != null) v = Solon.cfg().get(fallback);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }
}
