package yh.hotplugin.add1;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Param;
import org.noear.solon.core.handle.Context;
import yh.hotplugin.security.PluginSecurity;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件内的 REST 风格接口（路径前缀 /plugin/hello）。
 * 通过 DemoPlugin.start() 中 beanScan 被扫描注册；stop() 时按前缀整体移除。
 */
@Controller
@Mapping("/plugin/hello")
public class HelloController {

    /**
     * GET /plugin/hello
     * 返回简单问候。
     */
    @Mapping
    public String hello(Context context) {
        PluginSecurity.requirePermission(context,"plugin:demo:view");
        return "Hello, I'm a REST api from hotplug plugin!";
    }

    /**
     * GET /plugin/hello/name?name=solon
     * 带参数的问候。
     */
    @Mapping("name")
    public String helloName(Context context,@Param(defaultValue = "world") String name) {
        PluginSecurity.requirePermission(context,"plugin:demo:view");
        return "Hello, " + name + "! (from hotplug plugin)";
    }

    /**
     * GET /plugin/hello/json
     * 返回 JSON 结构数据。
     */
    @Mapping("json")
    public Map<String, Object> helloJson(Context context) {
        PluginSecurity.requirePermission(context,"plugin:demo:view");
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello from hotplug plugin");
        data.put("plugin", "plugin-demo");
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }
}
