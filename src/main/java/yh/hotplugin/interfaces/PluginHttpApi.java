package yh.hotplugin.interfaces;

import org.noear.dami2.Dami;
import org.noear.solon.SolonApp;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.UploadedFile;
import yh.hotplugin.application.PluginApplicationService;
import yh.hotplugin.domain.model.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP插件接口。它不包含域规则。
 */
public final class PluginHttpApi {
    /**
     * 插件应用服务。
     */
    private final PluginApplicationService service;
    private final File pluginDirectory;

    /**
     * 构造函数。
     */
    public PluginHttpApi(PluginApplicationService service, File pluginDirectory) {
        this.service = service;
        this.pluginDirectory = pluginDirectory.getAbsoluteFile();
    }

    /**
     * 注册插件路由。
     */
    public void registerRoutes(SolonApp app) {
        /* 获取所有插件。 */
        app.router().get("/api/plugins", ctx -> execute(ctx, () -> service.list()));
        app.router().get("/api/plugins/core-status", ctx -> {
            String name = ctx.param("name");
            if (!"pluginSystem".equals(name)) throw new IllegalArgumentException("Only pluginSystem status is available");
            execute(ctx, () -> service.get(name));
        });
        /* 调用插件能力。 */
        app.router().get("/api/plugin-capabilities/greet", ctx -> execute(ctx, () ->
                Dami.bus().<String, String>call("plugin.demo.greet",
                        ctx.paramOrDefault("name", "world")).get(3, TimeUnit.SECONDS)));
        /* 注册插件。 */
        app.router().post("/api/plugins/register", ctx -> execute(ctx, () -> service.register(ctx.param("name"), ctx.param("path"))));
        /* 启动插件。 */
        app.router().post("/api/plugins/start", ctx -> execute(ctx, () -> service.start(ctx.param("name"))));
        /* 停止插件。 */
        app.router().post("/api/plugins/stop", ctx -> execute(ctx, () -> service.stop(ctx.param("name"))));
        /* 卸载插件。 */
        app.router().post("/api/plugins/unload", ctx -> execute(ctx, () -> service.unload(ctx.param("name"))));
        /* 移除插件。 */
        app.router().post("/api/plugins/remove", ctx -> execute(ctx, () ->
                service.remove(ctx.param("name"))));
        /* 上传插件。 */
        app.router().post("/api/plugins/upload", ctx -> execute(ctx, () -> {
            UploadedFile file = ctx.file("file");
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("No file uploaded (field name must be 'file')");
            }
            String fileName = file.getName();
            if (fileName == null || !fileName.endsWith(".jar")) {
                throw new IllegalArgumentException("Only .jar files are accepted: " + fileName);
            }
            String name = ctx.param("name");
            if (name == null || name.trim().isEmpty()) {
                name = fileName.substring(0, fileName.length() - 4);
            }
            boolean autoStart = "true".equalsIgnoreCase(ctx.paramOrDefault("autoStart", "false"));

            File dir = pluginDirectory;
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dest = new File(dir, fileName);
            try {
                file.transferTo(dest);
            } finally {
                file.delete();
            }

            Plugin plugin = service.register(name, dest.getAbsolutePath());
            if (autoStart) {
                plugin = service.start(name);
            }
            return plugin;
        }));
    }

    /**
     * 执行插件操作。
     */
    private void execute(Context ctx, UseCase action) throws Throwable {
        try {
            Object data = action.run();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", toView(data));
            ctx.render(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", buildErrorMessage(e));
            ctx.render(response);
        } catch (Throwable e) {
            ctx.status(500);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", buildErrorMessage(e));
            ctx.render(response);
        }
    }

    /**
     * Build detailed error message including cause chain.
     */
    private String buildErrorMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());
        Throwable cause = e.getCause();
        while (cause != null) {
            sb.append(" | Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage());
            cause = cause.getCause();
        }
        return sb.toString();
    }

    /**
     * 转换为视图。
     */
    private Object toView(Object data) {
        if (data instanceof Plugin) {
            return pluginView((Plugin) data);
        }
        if (data instanceof Collection) {
            java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object item : (Collection<?>) data) {
                result.add(pluginView((Plugin) item));
            }
            return result;
        }
        return data;
    }

    /**
     * 转换为插件视图。
     */
    private Map<String, Object> pluginView(Plugin plugin) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", plugin.getName());
        view.put("jarPath", plugin.getJarPath());
        view.put("status", plugin.getStatus().name());
        view.put("maintenance", "pluginSystem".equals(plugin.getName()) &&
                plugin.getStatus() != yh.hotplugin.domain.model.PluginStatus.STARTED);
        view.put("recoverable", plugin.getStatus() == yh.hotplugin.domain.model.PluginStatus.QUARANTINED);
        return view;
    }

    /**
     * 插件操作。
     */
    private interface UseCase {
        Object run() throws Throwable;
    }
}
