package yh.hotplugin.domain.model;

import java.io.File;

/**
 * 动态加载插件的聚合根。
 */
public final class Plugin {
    /**
     * 插件名称。
     */
    private final String name;
    /**
     * 插件jar文件路径。
     */
    private final String jarPath;
    /**
     * 插件状态。
     */
    private PluginStatus status;

    /**
     * 构造函数。
     */
    private Plugin(String name, String jarPath, PluginStatus status) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin name must not be blank");
        }
        if (jarPath == null || jarPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin jar path must not be blank");
        }
        this.name = name.trim();
        this.jarPath = new File(jarPath).getAbsolutePath();
        this.status = status;
    }

    /**
     * 注册插件。
     */
    public static Plugin register(String name, String jarPath) {
        File jar = new File(jarPath);
        if (!jar.isFile() || !jar.getName().endsWith(".jar")) {
            throw new IllegalArgumentException("Plugin jar does not exist or is not a .jar file: " + jarPath);
        }
        return new Plugin(name, jarPath, PluginStatus.REGISTERED);
    }

    /**
     * 恢复插件。
     */
    public static Plugin restore(String name, String jarPath, boolean started) {
        return new Plugin(name, jarPath, started ? PluginStatus.STARTED : PluginStatus.REGISTERED);
    }

    public static Plugin restore(String name, String jarPath, PluginStatus status) {
        return new Plugin(name, jarPath, status == null ? PluginStatus.REGISTERED : status);
    }

    public static Plugin quarantined(String name, String jarPath) {
        return new Plugin(name, jarPath, PluginStatus.QUARANTINED);
    }

    /**
     * 启动插件。
     */
    public void start() {
        ensureNotRemoved();
        status = PluginStatus.STARTED;
    }

    /**
     * 停止插件。
     */
    public void stop() {
        ensureNotRemoved();
        if (status == PluginStatus.STARTED) {
            status = PluginStatus.STOPPED;
        }
    }

    /**
     * 卸载插件。
     */
    public void unload() {
        ensureNotRemoved();
        status = PluginStatus.UNLOADED;
    }

    /**
     * 移除插件。
     */
    public void remove() {
        status = PluginStatus.REMOVED;
    }

    /**
     * 确保插件未被移除。
     */
    private void ensureNotRemoved() {
        if (status == PluginStatus.REMOVED) {
            throw new IllegalStateException("Plugin has already been removed: " + name);
        }
    }

    /**
     * 获取插件名称。
     */
    public String getName() {
        return name;
    }

    /**
     * 获取插件jar文件路径。
     */
    public String getJarPath() {
        return jarPath;
    }

    /**
     * 获取插件状态。
     */
    public PluginStatus getStatus() {
        return status;
    }
}
