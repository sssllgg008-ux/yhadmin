package yh.hotplugin.add1;

import org.noear.dami2.Dami;
import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import yh.hotplugin.security.*;
import yh.hotplugin.security.api.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** A hot plugin exposing its capability through DamiBus without a shared Java API. */
public class DemoPlugin implements Plugin {
    public static final String GREET_TOPIC = "plugin.demo.greet";

    @Override
    public void start(AppContext context) {
        context.beanScan(DemoPlugin.class);

        Solon.app().router().get("/plugin/hi", ctx ->
                { PluginSecurity.requirePermission(ctx,"plugin:demo:view");ctx.output("Hello from the hot-plug plugin"); });

        DataResourceRegistry.register("plugin-demo", "demo:greeting", DataResourcePolicy.departmentAndCreator());
        Solon.app().router().get("/plugin/demo/scope", ctx -> {
            DataScopeResult scope=PluginSecurity.requireDataScope(ctx,"plugin:demo:view","demo:greeting");
            Map<String,Object> result=new LinkedHashMap<>();result.put("scope",scope.getScope().name());result.put("departmentIds",scope.getDepartmentIds());result.put("ownerUserId",scope.getOwnerUserId());ctx.render(result);
        });
        Solon.app().router().post("/plugin/demo/audited", ctx -> {
            Map<String,Object> result=PluginAudit.execute(ctx,"plugin-demo","示例数据",BusinessType.INSERT,"plugin:demo:edit",()->{Map<String,Object> value=new LinkedHashMap<>();value.put("created",true);return value;});ctx.render(result);
        });
        Solon.app().router().get("/plugin/demo/error", ctx -> PluginAudit.execute(ctx,"plugin-demo","示例异常",BusinessType.OTHER,"plugin:demo:view",()->{throw new IllegalStateException("plugin-demo test error");}));

        Dami.bus().<String, String>listen(GREET_TOPIC, (event, name, sink) ->
                sink.complete("Hello, " + (name == null ? "world" : name) + "! (via DamiBus)"));

        System.out.println("[DemoPlugin] started; DamiBus topic=" + GREET_TOPIC);
    }

    @Override
    public void stop() {
        // Every externally registered resource must be removed before the classloader is closed.
        Dami.bus().unlisten(GREET_TOPIC);
        Solon.app().router().remove("/plugin/hi");
        Solon.app().router().remove("/plugin/hello");
        Solon.app().router().remove("/plugin/demo/scope");
        Solon.app().router().remove("/plugin/demo/audited");
        Solon.app().router().remove("/plugin/demo/error");
        DataResourceRegistry.unregisterOwner("plugin-demo");
        System.out.println("[DemoPlugin] stopped; DamiBus listener removed");
    }
}
