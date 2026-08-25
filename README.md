# Solon H-Spi 插件热插�?演示项目

演示 Solon 框架�?**H-Spi（插件热插拔�?*：不重启主应用，动�?插上 / 拔下"业务插件�?参考官方文档：<https://solon.noear.org/article/273>

---

## 一、什么是 H-Spi

把主程序想象�?*正在直播、不能关机的电脑**�?
- **普�?Spi**：像内置硬盘，要换功能得关机重启应用�?- **H-Spi（热插拔�?*：像 **USB �?*，直播过程中直接插上 / 拔下，业务不中断�?
核心价值：**生产环境不停机，动态加功能 / 换功�?/ 撤功能�?*

---

## 二、项目结�?
```
pj/
├── pom.xml                                主程序（�?solon-hotplug�?├── src/main/java/com/example/main/
�?  └── DemoApp.java                       主程�?+ 热管理路�?├── src/main/resources/app.yml             Solon 配置（含 solon.hotplug�?├── plugin-demo/                            独立插件模块（打包成 jar 后被热加载）
�?  ├── pom.xml                             插件依赖（solon-web�?�?  ├── src/main/java/com/example/add1/    （包名与主程�?hotplugin.main 隔离�?�?  �?  └── DemoPlugin.java                Plugin 实现，start 注册 / stop 清理
�?  └── src/main/resources/META-INF/solon/plugin   SPI 声明：com.example.add1.DemoPlugin
└── README.md
```

- **主程�?*（包 `hotplugin.main`）：提供 `/start`、`/stop`、`/unload` 三个热管理路由；启动后自�?`PluginManager.load("plugin-demo").start()`�?- **插件**（包 `hotplugin.add1`）：独立 jar，提�?`/plugin/hi` 接口；停止时自动清理�?
> 官方要求�?*插件包名需独立**（主程序 `xxx.main`，插�?`xxx.add1`）；依赖包公共的放主程序，隔离的放插件�?
---

## 三、核心代�?
### 1. 主程序（DemoApp.java�?
```java
package hotplugin.main;

import org.noear.solon.Solon;
import org.noear.solon.hotplug.PluginManager;

public class DemoApp {
    public static void main(String[] args) {
        Solon.start(DemoApp.class, args, app -> {
            app.router().get("start",  ctx -> { PluginManager.start("plugin-demo");  ctx.output("OK"); });
            app.router().get("stop",   ctx -> { PluginManager.stop("plugin-demo");   ctx.output("OK"); });
            app.router().get("unload", ctx -> { PluginManager.unload("plugin-demo"); ctx.output("OK"); });
        });

        // 启动后自动加载并启动插件（依�?app.yml �?solon.hotplug.plugin-demo 配置�?        PluginManager.load("plugin-demo").start();
    }
}
```

### 2. 插件（DemoPlugin.java�?
```java
package hotplugin.add1;

import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Handler;

public class DemoPlugin implements Plugin {
    public void start(AppContext context) {
        Solon.app().router().add("/plugin/hi", ctx ->
                ctx.output("你好，我是由热插拔插件提供的接口�?));
    }

    public void stop() throws Throwable {
        // 移除 HTTP 处理 —�?拔下时必须清理，否则无法热更�?        Solon.app().router().remove("/plugin/hi");
    }
}
```

### 3. 插件 SPI 声明

`plugin-demo/src/main/resources/META-INF/solon/plugin`�?
```
hotplugin.add1.DemoPlugin
```

### 4. 随应用自动热加载（app.yml�?
> **注释�?= 不加载；取消注释 = 启动时自动加载并启动**。主程序通过 `PluginManager.getPlugins()` 判断该插件是否已登记，仅在已登记时才会自�?`load().start()`，避免未配置时抛 `Addin does not exist`�?
```yaml
solon.hotplug:
  # 取消下面这行注释后，主程序启动时会自动加载并启动该插�?  plugin-demo: "D:/work/pj/plugin-demo/target/plugin-demo-1.0.0.jar"
```

---

## 四、运行步�?
### 步骤 1：打包插�?```bash
cd plugin-demo
mvn clean package   # 生成 target/plugin-demo-1.0.0.jar
```

### 步骤 2：启动主程序
```bash
cd ..
mvn clean package
java -jar target/demo-1.0.0.jar
```
> Java 9+ 可能需要：
> `java --add-opens java.base/java.lang=ALL-UNNAMED -jar target/demo-1.0.0.jar`

### 步骤 3：访问插件接口（需先配�?app.yml 启用自动加载，或手动加载�?```bash
curl http://localhost:8080/plugin/hi
# 你好，我是由热插拔插件提供的接口�?```

### 步骤 4：热管理（不重启主程序）
```bash
curl http://localhost:8080/stop     # OK —�?停止插件（清理路由）
curl http://localhost:8080/start    # OK —�?重新启动（自动加�?+ 启动�?curl http://localhost:8080/unload   # OK —�?卸载插件 jar
```
停止后再访问 `/plugin/hi` �?404，主程序一直正常运行�?
---

## 五、注意事�?
1. **资源必须配对清理**：插�?`start()` 时加到公共空间的资源，必须在 `stop()` 里移除，否则无法干净地热更新�?2. **包名隔离**：主程序与插件包名需独立（如 `xxx.main` / `xxx.add1`）；公共依赖放主程序，隔离依赖放插件�?3. **ClassLoader 隔离**：每个插件包独享 ClassLoader、AopContext、配置�?4. **弱类型通信**：插件与主程序用 `Solon.context().getBean()` / `Solon.cfg()`；建议结�?DamiBus�?5. **Java 9+ 模块限制**：可能需�?`--add-opens java.base/java.lang=ALL-UNNAMED`�?
---

## 六、适用场景

- 生产环境不停机增减功能模�?- 插件化平�?/ 多租户功能按需加载
- 灰度发布某个能力（热插上验证，有问题热拔下）