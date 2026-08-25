package yh.hotplugin.infrastructure.dto;

import yh.hotplugin.domain.model.Plugin;
import yh.hotplugin.domain.model.PluginStatus;

/**
 * Lightweight DTO for JSON serialization of plugin metadata.
 * Does not contain business logic; used only for persistence.
 */
public final class PluginJsonDto {
    private String name;
    private String jarPath;
    private String status;

    public PluginJsonDto() {
    }

    public PluginJsonDto(String name, String jarPath, String status) {
        this.name = name;
        this.jarPath = jarPath;
        this.status = status;
    }

    public static PluginJsonDto from(Plugin plugin) {
        return new PluginJsonDto(
                plugin.getName(),
                plugin.getJarPath(),
                plugin.getStatus().name()
        );
    }

    public Plugin toPlugin() {
        PluginStatus restored;
        try { restored = PluginStatus.valueOf(status); }
        catch (Exception ignored) { restored = PluginStatus.REGISTERED; }
        return Plugin.restore(name, jarPath, restored);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
