# 当前系统 Cookie 与认证链路安全检查报告

## 执行摘要

检查确认：浏览器中的 `ry-password=[REDACTED]` 来自登录页“记住密码”实现，密码以明文保存 30 天，并随同域请求自动发送。这是必须立即修复的高风险问题。当前 Bearer Token 也存放在 JavaScript 可读 Cookie 中，并同时通过 `Authorization` 与 `Cookie` 发送，扩大了 XSS 和网络暴露面。截图还显示多套并行认证 Cookie 和通配 CORS，均需收紧。

## 严重问题

### SEC-001：明文密码写入持久 Cookie

- 严重性：严重（Critical）
- 修复状态：已修复（源码）
- 位置：`yhaminweb/src/views/login/index.vue:237-270`
- 证据：

```js
const password = getCookie("ry-password");
form.password = password || "";
setCookie("ry-password", form.password, 30);
```

- 影响：密码会在浏览器 Cookie、请求头、代理/抓包工具和本机用户可访问区域中暴露。任何同源 XSS、恶意扩展或本机访问者都可读取密码。
- 已实施：取消保存和恢复 `ry-password`；界面改为“记住账号”；登录页加载时主动删除历史 `ry-password` Cookie。
- 立即处置：删除浏览器中的 `ry-password`，并修改曾在共享或不可信电脑上保存过的账号密码。

## 高风险问题

### SEC-002：Bearer Token 存储在 JavaScript 可读 Cookie

- 严重性：高（High）
- 位置：`yhaminweb/src/utils/auth.js:3-15`、`yhaminweb/src/store/modules/user.js:30-35`、`yhaminweb/src/utils/request.js:91-100`
- 证据：

```js
Cookies.set(TOKEN_KEY, token, { expires: 7, sameSite: "lax" });
config.headers["Authorization"] = `Bearer ${token}`;
```

- 影响：Token 可被同源 XSS 读取，有效期长达 7 天；同时作为 Cookie 自动发送、又作为 Authorization Header 显式发送，形成重复暴露。
- 推荐修复：后端使用 `HttpOnly + SameSite=Lax/Strict` 会话 Cookie并配套 CSRF/Origin 校验，前端不再读取 Token。
- 兼容方案：继续 Bearer 时只在内存保存短期 Token并采用安全刷新机制，不写入 Cookie/localStorage/sessionStorage。
- 说明：浏览器 JavaScript 无法创建 `HttpOnly` Cookie，仅给 `js-cookie` 增加参数不能解决问题。

## 中风险问题

### SEC-003：存在多套并行认证/会话 Cookie

- 严重性：中（Medium）
- 位置：运行时截图；源码确认 `ry_admin_token`，但未在当前源码定位另一个管理 Token Cookie 的写入方。
- 影响：认证来源不唯一，容易出现登出不彻底、旧 Token 残留、优先级混淆和会话固定问题。
- 修复：明确唯一认证机制；停用不参与认证的 Cookie；登出时服务端失效会话，客户端清理全部历史认证 Cookie。
- 待确认：额外管理 Token 可能来自旧前端、插件管理页面或浏览器扩展，应在无扩展、无旧缓存环境复测。

### SEC-004：运行时响应使用通配 CORS

- 严重性：中（Medium）
- 位置：截图响应头 `Access-Control-Allow-Origin: *`；仓库内未找到明确设置点，可能由 Solon CORS 插件或代理层产生。
- 影响：任意来源均可跨域读取允许公开的响应。当前 Bearer 模式下不等同于直接越权，但与凭据配置错误或未来 Cookie 认证组合时风险会放大。
- 修复：生产环境限制为明确前端 Origin；如使用 Cookie 认证，禁止 `*`，并严格配置凭据、CSRF 与 Origin 校验。
- 待确认：检查实际 OPTIONS 与带 Origin 请求，确认是否返回 `Access-Control-Allow-Credentials`。

### SEC-005：服务端会话 Cookie 属性需要完善

- 严重性：中（Medium）
- 位置：运行时截图；会话 Cookie 已显示 `HttpOnly`，未显示 `SameSite`，本地 HTTP 下未显示 `Secure`。
- 影响：若该 Cookie 参与认证，缺少 SameSite 会增加跨站请求风险；生产 HTTPS 若没有 Secure，会允许其通过明文 HTTP 发送。
- 修复：确认该会话是否必需；不需要则关闭创建。需要则设置 `HttpOnly`、合适的 `SameSite`，生产 HTTPS 环境启用 `Secure`。本地 HTTP 不强制 Secure。

## 国密密码改造状态

- 状态：代码级改造已完成，尚未打包或发布。
- 权限与会话：继续仅使用 Sa-Token，不新增第二套认证框架。
- 新密码存储：统一使用带随机盐、20 万次迭代的 `PBKDF2-HMAC-SM3`，格式为 `pbkdf2-sm3$v1$...`。
- 覆盖路径：登录迁移、用户新增、修改密码、管理员重置密码、租户初始化管理员及租户管理员重置。
- 历史兼容：旧 BCrypt 密码仅允许用于一次登录校验；校验成功后立即重新计算国密摘要并更新数据库。系统不再生成新的 BCrypt 密码。
- 防降级：不接受明文数据库密码，也不将前端提交的摘要字符串作为新密码直接入库。
- 算法边界：SM3 用于密码派生摘要；SM2 应用于非对称加密/签名，SM4 应用于需要可逆保护的配置数据，二者均不用于可逆保存登录密码。
- 验证：`GmPasswordEncoderTest` 已覆盖随机盐、正确/错误密码、旧 BCrypt 迁移及畸形/明文拒绝。

## 已存在的正向控制

- API 使用 `Authorization: Bearer`，没有把 Token 放进 URL。
- 服务端会话 Cookie 已设置 `HttpOnly`。
- 登录审计使用 `[credentials omitted]`，未直接记录登录请求体：`plugin-system/src/main/java/yh/hotplugin/system/integration/CrudHttpApi.java:61`。
- 服务端新密码使用 PBKDF2-HMAC-SM3；旧 BCrypt 仅保留一次性平滑迁移校验，未发现数据库保存明文密码的证据。

## 修复优先级

1. 立即移除 `ry-password` 的读写并自动清理历史 Cookie。
2. 统一 Token/Session 机制，优先迁移到服务端 HttpOnly Cookie；迁移前避免 Token 同时出现在 Cookie 和 Header。
3. 排查并清除遗留管理 Token 来源，统一登出清理。
4. 收紧生产 CORS，完善会话 Cookie 的 SameSite/Secure 策略。
5. 回归登录、记住账号、退出、Token 过期、插件热重载和跨域请求。
