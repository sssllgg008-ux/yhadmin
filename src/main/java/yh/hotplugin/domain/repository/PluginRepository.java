package yh.hotplugin.domain.repository;

import yh.hotplugin.domain.model.Plugin;

import java.util.Collection;

/**
 * 插件仓库。
 */
public interface PluginRepository {
    /**
     * 根据名称查找插件。
     */
    Plugin find(String name);

    /**
     * 查找所有插件。
     */
    Collection<Plugin> findAll();

    /**
     * 保存插件。
     */
    void save(Plugin plugin);

    /**
     * 移除插件。
     */
    void remove(String name);
}
