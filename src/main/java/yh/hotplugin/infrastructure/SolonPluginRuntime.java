package yh.hotplugin.infrastructure;

import org.noear.solon.hotplug.PluginManager;
import yh.hotplugin.domain.service.PluginRuntime;

import java.io.File;

/**
 * Solon插件运行时服务。
 */
public final class SolonPluginRuntime implements PluginRuntime {
    /**
     * 注册插件。
     */
    @Override
    public void register(String name, File jarFile) {
        System.out.println("[PluginRuntime] Registering plugin: " + name + " from " + jarFile.getAbsolutePath());
        PluginManager.add(name, jarFile);
        System.out.println("[PluginRuntime] Plugin registered successfully: " + name);
    }

    /**
     * 启动插件。
     */
    @Override
    public void start(String name) {
        System.out.println("[PluginRuntime] Starting plugin: " + name);
        try {
            PluginManager.start(name);
            System.out.println("[PluginRuntime] Plugin started successfully: " + name);
        } catch (Exception e) {
            System.err.println("[PluginRuntime] Failed to start plugin " + name + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 停止插件。
     */
    @Override
    public void stop(String name) {
        System.out.println("[PluginRuntime] Stopping plugin: " + name);
        PluginManager.stop(name);
        System.out.println("[PluginRuntime] Plugin stopped successfully: " + name);
    }

    /**
     * 卸载插件。
     */
    @Override
    public void unload(String name) {
        System.out.println("[PluginRuntime] Unloading plugin: " + name);
        PluginManager.unload(name);
        System.out.println("[PluginRuntime] Plugin unloaded successfully: " + name);
    }

    /**
     * 移除插件。
     */
    @Override
    public void remove(String name) {
        System.out.println("[PluginRuntime] Removing plugin: " + name);
        PluginManager.remove(name);
        System.out.println("[PluginRuntime] Plugin removed successfully: " + name);
    }
}