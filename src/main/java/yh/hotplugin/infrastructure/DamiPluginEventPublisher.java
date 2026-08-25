package yh.hotplugin.infrastructure;

import org.noear.dami2.Dami;
import yh.hotplugin.domain.model.Plugin;
import yh.hotplugin.domain.service.PluginEventPublisher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DamiBus插件事件发布器。
 */
public final class DamiPluginEventPublisher implements PluginEventPublisher {
    public static final String TOPIC = "plugin.lifecycle.changed";

    /**
     * 发布插件生命周期域事件。
     */
    @Override
    public void publish(String eventType, Plugin plugin) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("name", plugin.getName());
        payload.put("jarPath", plugin.getJarPath());
        payload.put("status", plugin.getStatus().name());
        payload.put("occurredAt", System.currentTimeMillis());
        Dami.bus().send(TOPIC, payload);
    }
}
