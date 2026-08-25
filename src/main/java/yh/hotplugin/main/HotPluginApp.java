package yh.hotplugin.main;

import org.noear.solon.Solon;
import org.noear.solon.hotplug.PluginManager;
import yh.hotplugin.application.PluginApplicationService;
import yh.hotplugin.infrastructure.DamiPluginEventPublisher;
import yh.hotplugin.infrastructure.FilePluginRepository;
import yh.hotplugin.infrastructure.SolonPluginRuntime;
import yh.hotplugin.interfaces.PluginHttpApi;
import yh.hotplugin.security.HostSecurityFilter;
import yh.hotplugin.security.PermissionProviderRegistry;
import yh.hotplugin.security.PermissionPluginSettings;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application bootstrap and dependency wiring.
 */
public class HotPluginApp {
    public static void main(String[] args) {
        Path pluginDirectory = pluginDirectory();
        PluginApplicationService pluginService = new PluginApplicationService(
                new FilePluginRepository(pluginDirectory.resolve("plugins.json").toString()),
                new SolonPluginRuntime(),
                new DamiPluginEventPublisher());

        Solon.start(HotPluginApp.class, args, app -> {
            Map<String, String> permissionSettings = new LinkedHashMap<>();
            for (String key : new String[]{"plugin.system.datasource.driver", "plugin.system.datasource.url", "plugin.system.datasource.username", "plugin.system.datasource.password", "plugin.system.redis.host", "plugin.system.redis.port", "plugin.system.redis.password", "plugin.system.redis.database", "plugin.system.sa-token.token-name", "plugin.system.sa-token.timeout"}) {
                permissionSettings.put(key, app.cfg().get(key));
            }
            putEnvironmentIfMissing(permissionSettings, "plugin.system.datasource.url", "YHAMIN_DB_URL");
            putEnvironmentIfMissing(permissionSettings, "plugin.system.datasource.username", "YHAMIN_DB_USERNAME");
            putEnvironmentIfMissing(permissionSettings, "plugin.system.datasource.password", "YHAMIN_DB_PASSWORD");
            putEnvironmentIfMissing(permissionSettings, "plugin.system.redis.host", "YHAMIN_REDIS_HOST");
            putEnvironmentIfMissing(permissionSettings, "plugin.system.redis.port", "YHAMIN_REDIS_PORT");
            putEnvironmentIfMissing(permissionSettings, "plugin.system.redis.password", "YHAMIN_REDIS_PASSWORD");
            putEnvironmentIfMissing(permissionSettings, "plugin.system.redis.database", "YHAMIN_REDIS_DATABASE");
            loadDevelopmentFallback(permissionSettings,
                    app.cfg().get("plugin.system.dev-config",
                            "C:/Users/sssll/Documents/DDD/yhaminadmin/yhaminadmin-interface/src/main/resources/application-dev.yml"));
            PermissionPluginSettings.install(permissionSettings);
            app.router().filter(-1000, new HostSecurityFilter());
            app.router().get("/", ctx -> ctx.redirect(
                    PermissionProviderRegistry.available() ? "/index.html" : "/index.html#/login"));
            app.router().get("/favicon.ico", ctx -> {
                ctx.status(204);
                ctx.setHandled(true);
            });
            PluginManager.getPlugins().forEach(pluginService::restore);
            pluginService.bootstrap();
            pluginService.list().stream()
                    .filter(p -> "pluginSystem".equals(p.getName()))
                    .filter(p -> p.getStatus() != yh.hotplugin.domain.model.PluginStatus.STARTED)
                    .findFirst()
                    .ifPresent(p -> pluginService.start(p.getName()));
            new PluginHttpApi(pluginService, pluginDirectory.toFile()).registerRoutes(app);
        });
    }

    private static Path pluginDirectory() {
        String external = System.getenv("HOTPLUGIN_DIR");
        String value = blank(external) ? "plugins" : external.trim();
        return Paths.get(value).toAbsolutePath().normalize();
    }

    /**
     * Uses the existing DDD development profile only when normal external configuration is absent.
     */
    @SuppressWarnings("unchecked")
    private static void loadDevelopmentFallback(Map<String, String> target, String configFile) {
        if (!blank(target.get("plugin.system.datasource.url")) || blank(configFile)) {
            return;
        }
        Path path = Paths.get(configFile).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            Object document = new Yaml().load(input);
            if (!(document instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) document;
            Map<String, Object> spring = map(root.get("spring"));
            Map<String, Object> datasource = map(spring.get("datasource"));
            putIfMissing(target, "plugin.system.datasource.driver", datasource.get("driver-class-name"));
            putIfMissing(target, "plugin.system.datasource.url", datasource.get("url"));
            putIfMissing(target, "plugin.system.datasource.username", datasource.get("username"));
            putIfMissing(target, "plugin.system.datasource.password", datasource.get("password"));
            Map<String, Object> data = map(spring.get("data"));
            Map<String, Object> redis = map(data.get("redis"));
            if (redis.isEmpty()) redis = map(spring.get("redis"));
            if (!redis.isEmpty()) {
                putIfMissing(target, "plugin.system.redis.host", redis.get("host"));
                putIfMissing(target, "plugin.system.redis.port", redis.get("port"));
                putIfMissing(target, "plugin.system.redis.password", redis.get("password"));
                putIfMissing(target, "plugin.system.redis.database", redis.get("database"));
            }
            System.out.println("[HotPluginApp] Loaded local permission development configuration from " + path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load local permission development configuration: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private static void putIfMissing(Map<String, String> target, String key, Object value) {
        if (blank(target.get(key)) && value != null) target.put(key, String.valueOf(value));
    }

    private static void putEnvironmentIfMissing(Map<String, String> target, String key, String environmentName) {
        if (blank(target.get(key))) putIfMissing(target, key, System.getenv(environmentName));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty() || value.contains("${");
    }
}
