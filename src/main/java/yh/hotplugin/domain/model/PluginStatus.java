package yh.hotplugin.domain.model;

public enum PluginStatus {
    /**
     * 插件已注册。
     */
    REGISTERED,
    /**
     * 插件已启动。
     */
    STARTED,
    /**
     * 插件已停止。
     */
    STOPPED,
    /**
     * 插件已卸载。
     */
    UNLOADED,
    /** 核心插件已移入可恢复隔离区。 */
    QUARANTINED,
    /**
     * 插件已移除。
     */
    REMOVED
}
