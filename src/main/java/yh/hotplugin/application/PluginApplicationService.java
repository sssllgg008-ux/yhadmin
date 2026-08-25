package yh.hotplugin.application;

import org.noear.solon.hotplug.PluginInfo;
import yh.hotplugin.domain.model.Plugin;
import yh.hotplugin.domain.model.PluginStatus;
import yh.hotplugin.domain.repository.PluginRepository;
import yh.hotplugin.domain.service.PluginEventPublisher;
import yh.hotplugin.domain.service.PluginRuntime;

import java.io.File;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import yh.hotplugin.security.PermissionProviderRegistry;

/**
 * 插件应用服务。
 */
public final class PluginApplicationService {
    /**
     * 插件仓库。
     */
    private final PluginRepository repository;
    /**
     * 插件运行时服务。
     */
    private final PluginRuntime runtime;
    /**
     * 插件事件发布器。
     */
    private final PluginEventPublisher events;
    private final Map<String, Plugin> pendingReplacements = new ConcurrentHashMap<String, Plugin>();

    /**
     * 构造函数。
     */
    public PluginApplicationService(PluginRepository repository, PluginRuntime runtime,
                                    PluginEventPublisher events) {
        this.repository = repository;
        this.runtime = runtime;
        this.events = events;
    }

    /**
     * 注册插件。
     */
    public synchronized Plugin register(String name, String jarPath) {
        Plugin existing = repository.find(name);
        if (existing != null && "pluginSystem".equals(name) && existing.getStatus() == PluginStatus.QUARANTINED) {
            Plugin replacement = Plugin.register(name, jarPath);
            runtime.register(name, new File(replacement.getJarPath()));
            pendingReplacements.put(name, replacement);
            events.publish("REPLACEMENT_REGISTERED", replacement);
            return replacement;
        }
        if (existing != null) {
            throw new IllegalStateException("Plugin is already registered: " + name);
        }
        Plugin plugin = Plugin.register(name, jarPath);
        runtime.register(plugin.getName(), new File(plugin.getJarPath()));
        repository.save(plugin);
        events.publish("REGISTERED", plugin);
        return plugin;
    }

    /**
     * Bootstrap: re-register all restored plugins into the Solon runtime. Call once on startup.
     */
    public synchronized void bootstrap() {
        ArrayList<Plugin> ordered = new ArrayList<Plugin>(repository.findAll());
        ordered.sort(Comparator.comparing(p -> "pluginSystem".equals(p.getName()) ? 0 : 1));
        for (Plugin plugin : ordered) {
            if (plugin.getStatus() != PluginStatus.REMOVED && plugin.getStatus() != PluginStatus.QUARANTINED) {
                try {
                    System.out.println("[PluginApplicationService] Restoring plugin: " + plugin.getName());
                    runtime.register(plugin.getName(), new File(plugin.getJarPath()));
                    if (plugin.getStatus() == PluginStatus.STARTED) {
                        runtime.start(plugin.getName());
                        System.out.println("[PluginApplicationService] Restored plugin with STARTED status: " + plugin.getName());
                    }
                } catch (Exception e) {
                    System.err.println("WARN: Failed to re-register plugin " + plugin.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 启动插件。
     */
    public synchronized Plugin start(String name) {
        Plugin plugin = require(name);
        if (!"pluginSystem".equals(name) && !PermissionProviderRegistry.available()) {
            throw new IllegalStateException("Required plugin is not available: pluginSystem");
        }
        Plugin replacement = pendingReplacements.get(name);
        if (replacement != null) {
            try {
                runtime.start(name);
                replacement.start();
                repository.save(replacement);
                pendingReplacements.remove(name);
                deleteQuarantined(plugin);
                events.publish("STARTED", replacement);
                return replacement;
            } catch (RuntimeException failure) {
                rollbackCorePlugin(name, plugin, failure);
                throw failure;
            }
        }
        if (plugin.getStatus() == PluginStatus.QUARANTINED) {
            runtime.register(name, new File(plugin.getJarPath()));
        }
        runtime.start(name);
        plugin.start();
        repository.save(plugin);
        events.publish("STARTED", plugin);
        return plugin;
    }

    /**
     * 停止插件。
     */
    public synchronized Plugin stop(String name) {
        Plugin plugin = require(name);
        runtime.stop(name);
        plugin.stop();
        repository.save(plugin);
        events.publish("STOPPED", plugin);
        return plugin;
    }

    /**
     * 卸载插件。
     */
    public synchronized Plugin unload(String name) {
        Plugin plugin = require(name);
        if (plugin.getStatus() == PluginStatus.UNLOADED || plugin.getStatus() == PluginStatus.QUARANTINED) {
            return plugin;
        }
        runtime.unload(name);
        plugin.unload();
        repository.save(plugin);
        events.publish("UNLOADED", plugin);
        return plugin;
    }

    /**
     * 移除插件。
     */
    public synchronized Plugin remove(String name) {
        Plugin plugin = require(name);
        if (plugin.getStatus() == PluginStatus.QUARANTINED) {
            return plugin;
        }
        if (plugin.getStatus() != PluginStatus.UNLOADED) {
            runtime.unload(name);
            plugin.unload();
            repository.save(plugin);
            events.publish("UNLOADED", plugin);
        }
        runtime.remove(name);
        if ("pluginSystem".equals(name)) {
            Plugin quarantined = quarantine(plugin);
            repository.save(quarantined);
            events.publish("QUARANTINED", quarantined);
            return quarantined;
        }
        deletePluginJar(plugin);
        plugin.remove();
        events.publish("REMOVED", plugin);
        repository.remove(name);
        return plugin;
    }

    private void deletePluginJar(Plugin plugin) {
        Path jar = new File(plugin.getJarPath()).toPath();
        try {
            // Missing already means the file-removal goal has been reached; keep retries idempotent.
            Files.deleteIfExists(jar);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete plugin jar: " + jar, e);
        }
    }

    private Plugin quarantine(Plugin plugin) {
        try {
            Path source = new File(plugin.getJarPath()).toPath();
            Path dir = source.getParent().resolve(".quarantine");
            Files.createDirectories(dir);
            Path target = dir.resolve(plugin.getName() + "-" + System.currentTimeMillis() + ".jar");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return Plugin.quarantined(plugin.getName(), target.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to quarantine core plugin: " + plugin.getName(), e);
        }
    }

    private void rollbackCorePlugin(String name, Plugin quarantined, RuntimeException failure) {
        try { runtime.unload(name); } catch (Exception ignored) { }
        try { runtime.remove(name); } catch (Exception ignored) { }
        try {
            runtime.register(name, new File(quarantined.getJarPath()));
            runtime.start(name);
            quarantined.start();
            repository.save(quarantined);
            pendingReplacements.remove(name);
            events.publish("ROLLED_BACK", quarantined);
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void deleteQuarantined(Plugin quarantined) {
        if (quarantined.getStatus() != PluginStatus.QUARANTINED) return;
        try { Files.deleteIfExists(new File(quarantined.getJarPath()).toPath()); }
        catch (Exception e) { System.err.println("WARN: Failed to delete quarantined plugin: " + e.getMessage()); }
    }

    /**
     * 列出所有插件。
     */
    public Collection<Plugin> list() {
        return repository.findAll();
    }

    public Plugin get(String name) {
        return require(name);
    }

    /**
     * 恢复插件。
     */
    public synchronized void restore(PluginInfo info) {
        Plugin existing = repository.find(info.getName());
        if (existing == null) {
            repository.save(Plugin.restore(info.getName(), info.getFile().getAbsolutePath(), info.getStarted()));
        } else if (info.getStarted() && existing.getStatus() != PluginStatus.STARTED) {
            existing.start();
            repository.save(existing);
        }
    }

    /**
     * 确保插件已注册。
     */
    private Plugin require(String name) {
        Plugin plugin = repository.find(name);
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin is not registered: " + name);
        }
        return plugin;
    }
}
