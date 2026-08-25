# Solon 插件热拔插管理系统

本项目基于 Solon H-Spi 构建插件热拔插平台。宿主负责插件上传、注册、启动、停止、卸载和可恢复移除；`pluginSystem` 提供统一认证、权限、多租户、审计、套餐、配额、限流及备份能力。

Solon H-Spi 参考文档：<https://solon.noear.org/article/273>

## 项目能力

- 插件无需重启宿主即可启动、停止和卸载。
- 插件路径和状态由宿主动态管理，不依赖 `app.yml` 中的 `solon.hotplug` 静态配置。
- `pluginSystem` 保持为 Solon `Plugin` 组件，是系统唯一主权限插件。
- 身份认证和会话统一使用 Sa-Token，Redis 持久化登录状态。
- 持久层使用 MyBatis-Plus，租户数据由公共 `TenantContext` 强制隔离。
- 业务插件通过 `permission-api` 使用权限、数据范围和审计能力。
- 权限插件停止时宿主故障关闭，普通受保护接口返回 503。
- 支持租户生命周期、套餐版本、订阅、配额、限流、用量和数据库备份恢复。

## 项目结构

```text
pj/
├─ pom.xml                         Solon 宿主 Maven 配置
├─ permission-api/                 插件共享的安全与多租户 API
├─ plugin-system/                  核心权限与 SaaS 管理插件
├─ plugin-demo/                    热拔插示例插件
├─ plugins/                        宿主管理的运行时插件目录
├─ src/main/                       Solon 宿主程序源码
├─ yhaminweb/                      Vue 3 管理端
├─ PLUGIN-DEVELOPMENT-STANDARD.md  插件开发统一规范
└─ security_best_practices_report.md
```

## 核心组件

### 宿主程序

宿主负责插件元数据和运行状态管理、插件管理接口、安全过滤、公共 Provider Registry，以及核心权限插件停止期间的受控维护通道。

### pluginSystem

`pluginSystem` 启动顺序：

1. 检查 Redis 并初始化 Sa-Token。
2. 初始化数据源、MyBatis-Plus 和租户拦截器。
3. 注册权限、数据范围、审计和租户配额 Provider。
4. 注册认证、系统管理和 SaaS 管理路由。

停止时按相反顺序注销外部资源，并保留 Redis 中的有效登录关系。正常热重载后，原 Token 可以恢复会话。

### permission-api

业务插件不得自行解析 Token、查询系统权限表或维护权限缓存。应通过公共 API 获取可信身份：

```java
SecurityPrincipal principal = PluginSecurity.requirePermission(
        context, "warehouse:datasource:list");

long tenantId = principal.getTenantId();
long userId = principal.getUserId();
```

详细要求见 [插件开发统一规范](PLUGIN-DEVELOPMENT-STANDARD.md)。

## 环境要求

- JDK 17（项目字节码目标为 Java 8）
- Maven 3.6+
- Node.js 20+
- MySQL
- Redis

## 构建与运行

### 安装公共 API

```bash
cd permission-api
mvn clean install
```

### 构建前端

```bash
cd ../yhaminweb
npm install
npm run build
```

### 构建宿主

```bash
cd ..
mvn clean package
```

### 构建插件

```bash
cd plugin-system
mvn clean package

cd ../plugin-demo
mvn clean package
```

插件构建完成后，通过“插件热拔插管理”页面上传、注册和启动。宿主不会从 `app.yml` 静态加载插件路径。

## 插件生命周期规范

插件在 `start()` 中注册到宿主公共空间的资源，必须在 `stop()` 中逐项、逆序清理：

- HTTP 路由
- DamiBus 监听器
- Provider 和 Registry
- 定时任务及线程池
- Redis、数据库连接池和其他客户端
- 租户线程上下文

资源未正确清理可能造成重复路由、类加载器泄漏或插件无法移除。

## 权限与多租户约定

- 所有受保护接口使用 `Authorization: Bearer <token>`。
- 401：Token 无效。
- 403：权限不足。
- 409：生命周期、订阅或配额冲突。
- 429：租户请求超出限流额度。
- 503：权限插件或关键存储不可用。
- 租户 ID、用户 ID 和用户名必须来自可信的 `SecurityPrincipal`。
- 业务接口不得把前端提交的 `tenantId` 作为最终隔离依据。
- 缺少租户上下文时，租户表访问必须故障关闭。

## SaaS 能力

- 租户创建、初始化、启停、管理员密码重置和删除生命周期。
- 套餐草稿、复制、发布、版本比较、停用和归档。
- 订阅立即变更、到期变更、待生效取消和历史记录。
- 配额目录、套餐限制、租户覆盖值和当前用量。
- 租户级分钟/每日请求限流及路由策略。
- 租户数据库备份、鉴权下载、恢复和失败补偿。

## 开发命令

```bash
# pluginSystem 测试，不生成插件包
cd plugin-system
mvn test

# 前端开发
cd ../yhaminweb
npm run dev

# 前端生产构建
npm run build
```

## 编码与安全规范

- 源码、配置和 Markdown 文件统一使用 UTF-8。
- 文本文件统一使用 LF 换行，Windows 批处理文件使用 CRLF。
- 禁止将数据库密码、Redis 密码、Token、私钥或运行时插件包提交到 Git。
- 插件发布由管理员通过插件管理页面完成，源码构建过程不得自动上传或替换运行中的插件。
