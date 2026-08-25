# Solon 热插拔插件开发统一规范

本规范适用于 `D:\work\pj` 中所有新增业务插件。目标是让插件可独立构建、热插拔，同时统一复用宿主身份、权限、数据范围与审计能力。

## 1. 必须遵守的架构边界

- 宿主拥有 `permission-api`、安全过滤器、Provider Registry 和审计缓冲队列。
- `pluginSystem` 是所有受保护业务插件的强依赖，独占访问 `sys_*` 权限与日志表。
- 业务插件不得解析 Token、查询权限表、判断 admin、直接写系统日志表或自行维护权限缓存。
- 业务插件只通过 `PluginSecurity`、`PluginAudit`、`DataResourceRegistry` 使用安全能力。
- `pluginSystem` 不可用时所有受保护接口必须故障关闭并返回 503，禁止降级放行。
- `pluginSystem` 是核心插件：停止后进入最多 5 分钟的受控维护窗口，只有原管理员可继续操作该插件；其他插件不得依赖此维护通道。

## 2. 标准目录

```text
plugin-example/
├─ pom.xml
└─ src/main/
   ├─ java/<package>/ExamplePlugin.java
   ├─ java/<package>/controller/
   ├─ java/<package>/application/
   ├─ java/<package>/domain/
   ├─ java/<package>/infrastructure/
   └─ resources/
      ├─ META-INF/solon/plugin
      ├─ META-INF/solon/plugin.properties
      └─ plugin-manifest.yml
```

插件入口实现 Solon `Plugin`，在 `start()` 注册全部外部资源，在 `stop()` 中逆序、逐项移除。

## 3. Maven 依赖

```xml
<dependency>
  <groupId>yh.hotplugin</groupId>
  <artifactId>permission-api</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
```

Solon API、DamiBus 和 `permission-api` 必须使用 `provided`。最终插件 JAR 中不得出现 `yh/hotplugin/security/**`、Solon 或 DamiBus 的重复类。第三方私有依赖可打入插件 JAR，但必须在停止时关闭线程、连接池和客户端。

## 4. 插件清单

```yaml
plugin:
  name: warehouse
  requires:
    - pluginSystem
  permissions:
    - warehouse:datasource:list
    - warehouse:datasource:add
    - warehouse:datasource:edit
    - warehouse:datasource:remove
  dataResources:
    - warehouse:datasource
```

- 插件名全局唯一，建议使用小写字母、数字和连字符。
- 权限格式为 `模块:资源:动作`，一经发布不得改变语义。
- 资源名格式为 `模块:资源`，所有权全局唯一。
- 路由必须以插件模块前缀开头，禁止覆盖宿主和其他插件路由。

## 5. 权限与可信上下文

```java
SecurityPrincipal principal = PluginSecurity.requirePermission(
    context, "warehouse:datasource:add");
long tenantId = principal.getTenantId();
```

- `tenantId`、`userId`、用户名只能取自 `SecurityPrincipal`。
- 请求体或查询参数中的租户字段必须忽略或拒绝。
- 无登录返回 401，无权限返回 403，权限插件不可用返回 503。
- Controller 不得直接访问 `PermissionProviderRegistry`，统一使用 `PluginSecurity`。

### permission-api 开发接口

| API | 用途 | 使用位置 |
| --- | --- | --- |
| `PluginSecurity.requirePrincipal(Context)` | 取得已认证用户 | 只要求登录的接口 |
| `PluginSecurity.requirePermission(Context, permission)` | 登录及权限校验，并返回可信用户 | 普通受保护接口 |
| `PluginSecurity.requireDataScope(Context, permission, resource)` | 权限校验并取得结构化数据范围 | 列表、检索和导出接口 |
| `PluginAudit.execute(...)` | 执行业务写操作并记录操作/错误审计 | 新增、修改、删除、授权等操作 |
| `DataResourceRegistry.register(owner, resource, policy)` | 声明插件拥有的数据资源 | `Plugin.start()` |
| `DataResourceRegistry.unregisterOwner(owner)` | 移除该插件的全部数据资源 | `Plugin.stop()` |

这些类由宿主的 `permission-api` 提供。插件不得直接调用 Sa-Token、Redis、`PermissionProviderRegistry`、`DataScopeProviderRegistry` 或 `AuditProviderRegistry`。

## 6. 数据权限

插件启动时注册，停止时按 owner 清理：

```java
DataResourceRegistry.register(
    "warehouse", "warehouse:datasource",
    DataResourcePolicy.departmentAndCreator());

DataResourceRegistry.unregisterOwner("warehouse");
```

查询前取得范围：

```java
DataScopeResult scope = PluginSecurity.requireDataScope(
    context, "warehouse:datasource:list", "warehouse:datasource");
repository.list(principal.getTenantId(), scope, query);
```

Repository 必须显式实现：`ALL` 仅租户隔离；`CUSTOM/DEPT/DEPT_AND_BELOW` 限制部门；`SELF` 限制创建人或用户；`DENY` 拒绝或返回空集合。禁止把结构化范围直接拼成未经参数化的 SQL。

## 7. 操作日志与错误日志

所有新增、修改、删除、导入、导出、授权操作必须使用：

```java
Result result = PluginAudit.execute(
    context,
    "warehouse",
    "数据源管理",
    BusinessType.INSERT,
    "warehouse:datasource:add",
    () -> service.create(command));
```

- 包装器自动记录租户、用户、请求 ID、插件、URI、IP、成功状态和耗时。
- 异常同时产生失败操作日志和错误日志，然后继续抛出原业务异常。
- `password`、Token、Cookie、Secret、Access Key、连接密码必须脱敏。
- 禁止记录完整请求头、完整响应正文、二进制内容和超大文本。
- 日志写入失败不得回滚已经成功的业务事务；宿主队列负责短期缓存和恢复重试。

## 8. 生命周期

`start()` 顺序：校验依赖、创建内部资源、注册数据资源、注册事件监听器、注册路由、健康检查。

`stop()` 逆序执行：停止接收请求、等待在途调用、移除路由、注销监听器、注销数据资源、停止任务和线程池、清理 ThreadLocal、关闭客户端和连接池、释放引用。每一步必须可重复调用。

DamiBus 只用于普通业务事件，不得作为核心权限判断的唯一通道。每个 `listen` 必须有对应 `unlisten`。

标准状态为：`REGISTERED → STARTED → STOPPED → UNLOADED → REMOVED`。业务插件的移除是最终状态；核心插件 `pluginSystem` 的移除改为 `QUARANTINED`，JAR 保存在 `.quarantine` 中并可恢复。

## 9. 宿主插件管理 API

所有请求使用 `Authorization: Bearer <token>`，并要求 `plugin:manage` 权限。除上传外，请求体使用 `application/x-www-form-urlencoded`，参数为 `name`。

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/plugins` | 无 | 查询已注册插件；权限服务不可用时不开放 |
| `POST` | `/api/plugins/register` | `name`, `path` | 注册宿主本地 JAR |
| `POST` | `/api/plugins/start` | `name` | 启动插件；非核心插件要求 `pluginSystem` 可用 |
| `POST` | `/api/plugins/stop` | `name` | 停止插件并保留注册信息 |
| `POST` | `/api/plugins/unload` | `name` | 释放插件运行实例；重复卸载按幂等成功处理 |
| `POST` | `/api/plugins/remove` | `name` | 普通插件移除；`pluginSystem` 进入隔离区 |
| `POST` | `/api/plugins/upload` | multipart: `file`, `name`, `autoStart` | 上传、注册并可选择启动插件 |
| `GET` | `/api/plugins/core-status` | `name=pluginSystem` | 维护期只查询核心插件状态 |

成功响应保持 `{ "success": true, "data": ... }`；插件视图包含 `name`、`jarPath`、`status`，并可包含 `maintenance`、`recoverable`。失败响应为 `{ "success": false, "message": "..." }`。

- `401`：Token 无效，客户端应清除登录状态。
- `403`：Token 有效但权限不足，客户端必须保留 Token。
- `503 PERMISSION_PLUGIN_UNAVAILABLE`：核心权限插件维护中，客户端必须保留 Token。

### pluginSystem 维护窗口

- 管理员成功停止 `pluginSystem` 后，宿主在内存中创建绑定 Token 哈希、客户端 IP 和插件名的维护凭证，默认有效期 5 分钟。
- 维护期仅放行目标为 `pluginSystem` 的启动、卸载、移除、上传、注册及核心状态查询；不能操作其他插件或访问业务接口。
- 正常停止和卸载不会注销 Sa-Token 登录，也不会删除 Redis 会话。使用相同配置重新启动后，原 Bearer Token 自动恢复。
- 替换包启动成功后清除隔离包和维护凭证；启动失败时宿主尝试恢复隔离版本。
- Redis 会话过期、用户/租户禁用或 Token 配置改变时，恢复后仍返回 401，需要重新登录。

## 10. 构建和发布门槛

```powershell
mvn test
mvn package
jar tf target/plugin-example-1.0.0.jar
```

发布前必须确认：

- 单元测试和集成测试全部通过，测试数不得为 0。
- JAR 不包含共享 SDK、Solon 和 DamiBus 重复类。
- 无权限为 403，权限插件停止为 503。
- 伪造租户 ID 无法跨租户访问。
- 五种数据范围符合预期。
- 写操作进入 `sys_oper_log`，异常进入 `sys_error_log`，敏感参数已脱敏。
- 连续 100 次启动、停止和恢复无残留路由、监听器、线程或连接。
- 插件停止后其路由、数据资源和事件能力全部消失。
- `pluginSystem` 执行 `停止 → 卸载 → 启动` 后原 Redis Token 仍可使用。
- 维护凭证不能操作非核心插件，过期或 IP/Token 不匹配时返回 503。

## 11. 标准样板

`plugin-demo` 是当前规范的可运行样板。新插件应复制其 Maven `provided` 依赖、清单、`PluginSecurity`、`PluginAudit`、数据资源注册和 `stop()` 清理方式，再替换模块名、权限和业务实现。
