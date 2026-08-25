# plugin-system

`plugin-system` 是基于 Solon H-Spi 的可热插拔权限组件。宿主持有安全过滤器、Token 解析入口与 DenyAll 降级；插件提供认证、RBAC、管理 API、缓存、审计、数据权限及前端静态资源。

## 已实现能力

- 兼容 `/login`、`/logout`、`/getInfo`、`/getRouters`。
- 停用租户、用户、角色不参与授权；`admin` 获得 `*`。
- 联合查询角色与菜单权限，支持菜单和按钮上的权限字符串。
- Sa-Token 会话、Redis 必需存储、登录失败锁定及授权读取。
- 用户、角色、菜单、部门、租户 CRUD，以及用户角色、角色菜单、角色部门授权。
- 全部、自定义部门、本部门、本部门及以下、仅本人五种数据范围；已接入用户和日志查询。
- 插件内置 `yhaminweb` 构建产物，启停时同步注册或移除路由及静态资源。
- 插件停止后宿主 fail-closed；授权管理员可在绑定 Token/IP 的 5 分钟维护窗口内停止、卸载、隔离、替换或恢复权限插件。
- 正常停止和卸载不清理 Redis 会话；以相同 Sa-Token 配置恢复后原 Bearer Token 继续有效。
- “移除”核心插件会把 JAR 放入 `.quarantine` 并标记为 `QUARANTINED`，替换启动失败时尝试回滚。

`sys_menu` 按原系统作为全局表处理；其他租户数据及关联表均使用已验证会话中的 `tenant_id`，不接受请求体或请求头覆盖。

## 配置

```yaml
plugin.system:
  datasource:
    driver: com.mysql.cj.jdbc.Driver
    url: ${YHAMIN_DB_URL}
    username: ${YHAMIN_DB_USERNAME}
    password: ${YHAMIN_DB_PASSWORD}
  redis:
    host: 127.0.0.1
    port: 6379
    password: ""
    database: 0
  sa-token:
    token-name: satoken
    timeout: 28800
```

开发环境未显式配置时，宿主会读取 DDD 项目的 `application-dev.yml` 中对应 MySQL 配置。Redis 为必需依赖，不可连接时插件拒绝启动，避免会话分裂。

宿主插件管理 HTTP API、状态码以及业务插件可调用的 `permission-api` 清单见根目录的 `PLUGIN-DEVELOPMENT-STANDARD.md`。

## 构建

```powershell
cd D:\work\pj\plugin-system
mvn clean package
Copy-Item target\plugin-system-1.0.0.jar ..\plugins\plugin-system-1.0.0.jar -Force
```

测试配置会在测试数为 0 时失败。当前仓储测试覆盖租户隔离、角色状态、管理员通配、菜单权限、事务行为及五种数据范围。
