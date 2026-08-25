package yh.hotplugin.domain.service;

import yh.hotplugin.domain.model.Plugin;

/**
 * Port for 发布插件生命周期域事件的端口。
 */
public interface PluginEventPublisher {
    /**
     * 发布插件生命周期域事件。
     */
    void publish(String eventType, Plugin plugin);
}
