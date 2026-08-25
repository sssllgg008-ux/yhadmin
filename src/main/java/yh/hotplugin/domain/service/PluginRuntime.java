package yh.hotplugin.domain.service;

import java.io.File;

/**
 * 插件运行时服务。
 */
public interface PluginRuntime {
    /**
     * 注册插件。
     */
    void register(String name, File jarFile);

    /**
     * 启动插件。
     */
    void start(String name);

    /**
     * 停止插件。
     */
    void stop(String name);

    /**
     * 卸载插件。
     */
    void unload(String name);

    /**
     * 移除插件。
     */
    void remove(String name);
}
