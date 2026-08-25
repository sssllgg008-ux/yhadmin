package yh.hotplugin.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.noear.solon.Solon;
import org.noear.solon.core.handle.Context;
import yh.hotplugin.security.api.*;
import yh.hotplugin.security.TenantQuotaRegistry;
import yh.hotplugin.security.RequestPerformance;
import yh.hotplugin.security.tenant.TenantContributorRegistry;
import yh.hotplugin.security.tenant.TenantLifecycleContributor;
import yh.hotplugin.system.application.SystemPermissionProvider;
import yh.hotplugin.system.infrastructure.*;
import yh.hotplugin.system.security.GmPasswordEncoder;

import java.util.*;

/**
 * Existing yhaminweb-compatible CRUD and data-scoped query endpoints.
 */
final class CrudHttpApi {
    private static final String AUDIT_REQUEST_BODY = "audit.request.body";
    private static final java.util.regex.Pattern AUDIT_SECRET = java.util.regex.Pattern.compile(
            "(?i)(\\\"?(?:password|passwd|token|authorization|cookie|secret|access[_-]?key|connectionPassword)\\\"?\\s*[:=]\\s*\\\"?)([^\\\",}&;\\s]+)");
    static final String[] RESOURCES = {"user", "role", "menu", "dept", "tenant", "module", "dict", "dictData", "config", "notice", "extField"};
    static final String[] EXTRA_ROUTES = {"/system/role/optionselect", "/system/dept/tree", "/system/dept/descendants/{id}", "/system/menu/treeselect", "/system/module/optionselect", "/system/module/status", "/system/tenant/listAll", "/system/dict/type/{dictType}", "/system/dict/data/list", "/system/dict/data/type/{dictType}", "/system/dict/changeStatus", "/system/dict/refreshCache", "/system/config/key/{configKey}", "/system/config/refreshCache", "/system/notice/published", "/system/notice/inbox", "/system/notice/{id}/read", "/system/notice/readAll", "/system/notice/changeStatus", "/system/user/profile", "/system/user/profile/password", "/system/extField/byEntity/{entityType}", "/system/extField/changeStatus", "/system/extValue/{entityType}/{entityId}", "/system/extValue", "/system/extValue/{entityType}/{entityId}/{fieldKey}", "/monitor/operlog/list", "/monitor/operlog/{id}", "/monitor/operlog/{ids}", "/monitor/operlog/clean", "/monitor/logininfor/list", "/monitor/logininfor/{id}", "/monitor/logininfor/{ids}", "/monitor/logininfor/clean", "/monitor/logininfor/unlock", "/monitor/errorlog/list", "/monitor/errorlog/{id}"};
    private final ObjectMapper json;
    private final JdbcCrudRepository crud;
    private final JdbcManagementRepository management;
    private final SystemPermissionProvider provider;
    private final DataScopeProvider dataScope;
    private final TenantLifecycleTaskService tenantTasks;
    private final GmPasswordEncoder passwords = new GmPasswordEncoder();

    CrudHttpApi(ObjectMapper json, JdbcCrudRepository crud, JdbcManagementRepository management, SystemPermissionProvider provider, DataScopeProvider dataScope, TenantLifecycleTaskService tenantTasks) {
        this.json = json;
        this.crud = crud;
        this.management = management;
        this.provider = provider;
        this.dataScope = dataScope;
        this.tenantTasks = tenantTasks;
    }

    Map<String, Object> userProfile(SecurityPrincipal p) {
        return crud.get("user", p.getTenantId(), p.getUserId());
    }

    Map<String, Object> defaultTenant() {
        return crud.defaultTenant();
    }

    List<Map<String, Object>> activeTenants() {
        return crud.activeTenants();
    }

    Map<String, Long> dashboard(SecurityPrincipal p) {
        return crud.dashboard(p.getTenantId());
    }

    void auditAuthentication(Context c, SecurityPrincipal p, boolean success, String error, long cost) {
        Map<String, Object> u = p.getUserId() > 0 ? crud.get("user", p.getTenantId(), p.getUserId()) : null;
        String dept = u == null ? "" : String.valueOf(u.get("deptName"));
        crud.audit(p.getTenantId(), p.getUsername(), dept, "用户登录", 4, "SecureSystemSolonPlugin.login", c.method(), c.path(), c.remoteIp(), "[credentials omitted]", success, error, cost);
    }

    void register() {
        // Solon evaluates these hot-plugged routes in registration order. Register
        // single-segment static paths before the generic /{id} CRUD routes.
        Solon.app().router().get("/system/role/optionselect", c -> guard(c, () -> options(c, "role")));
        Solon.app().router().get("/system/dept/tree", c -> guard(c, () -> options(c, "dept")));
        Solon.app().router().get("/system/menu/treeselect", c -> guard(c, () -> options(c, "menu")));
        Solon.app().router().get("/system/module/optionselect", c -> guard(c, () -> options(c, "module")));
        Solon.app().router().get("/system/tenant/listAll", c -> guard(c, () -> options(c, "tenant")));
        Solon.app().router().get("/system/notice/published", c -> guard(c, () -> published(c)));
        Solon.app().router().get("/system/notice/inbox", c -> guard(c, () -> noticeInbox(c)));
        Solon.app().router().get("/system/user/profile", c -> guard(c, () -> profile(c)));
        for (String resource : RESOURCES) {
            String base = "/system/" + resource;
            Solon.app().router().get(base + "/list", c -> guard(c, () -> list(c, resource)));
            Solon.app().router().get(base + "/{id}", c -> guard(c, () -> get(c, resource)));
            Solon.app().router().post(base, c -> guard(c, () -> add(c, resource)));
            Solon.app().router().put(base, c -> guard(c, () -> update(c, resource)));
            Solon.app().router().delete(base + "/{ids}", c -> guard(c, () -> delete(c, resource)));
        }
        Solon.app().router().get("/system/dept/descendants/{id}", c -> guard(c, () -> descendants(c)));
        Solon.app().router().put("/system/module/status", c -> guard(c, () -> simpleStatus(c, "module")));
        Solon.app().router().get("/system/dict/type/{dictType}", c -> guard(c, () -> dictType(c)));
        Solon.app().router().get("/system/dict/data/list", c -> guard(c, () -> list(c, "dictData")));
        Solon.app().router().get("/system/dict/data/type/{dictType}", c -> guard(c, () -> dictByType(c)));
        Solon.app().router().put("/system/dict/changeStatus", c -> guard(c, () -> simpleStatus(c, "dict")));
        Solon.app().router().put("/system/dict/refreshCache", c -> guard(c, () -> cacheRefresh(c, "dict")));
        Solon.app().router().get("/system/config/key/{configKey}", c -> guard(c, () -> configByKey(c)));
        Solon.app().router().put("/system/config/refreshCache", c -> guard(c, () -> cacheRefresh(c, "config")));
        Solon.app().router().put("/system/notice/{id}/read", c -> guard(c, () -> markNoticeRead(c)));
        Solon.app().router().put("/system/notice/readAll", c -> guard(c, () -> markAllNoticesRead(c)));
        Solon.app().router().put("/system/notice/changeStatus", c -> guard(c, () -> simpleStatus(c, "notice")));
        Solon.app().router().put("/system/user/profile", c -> guard(c, () -> updateProfile(c)));
        Solon.app().router().put("/system/user/profile/password", c -> guard(c, () -> changeOwnPassword(c)));
        registerExt();
        registerLogs();
    }

    private void registerExt() {
        Solon.app().router().get("/system/extField/byEntity/{entityType}", c -> guard(c, () -> extFields(c)));
        Solon.app().router().get("/system/extValue/{entityType}/{entityId}", c -> guard(c, () -> extValues(c)));
        Solon.app().router().put("/system/extValue", c -> guard(c, () -> saveExtValues(c)));
        Solon.app().router().delete("/system/extValue/{entityType}/{entityId}", c -> guard(c, () -> deleteExtValues(c, false)));
        Solon.app().router().delete("/system/extValue/{entityType}/{entityId}/{fieldKey}", c -> guard(c, () -> deleteExtValues(c, true)));
        Solon.app().router().put("/system/extField/changeStatus", c -> guard(c, () -> simpleStatus(c, "extField")));
    }

    private void registerLogs() {
        Solon.app().router().get("/monitor/operlog/list", c -> guard(c, () -> logs(c, "oper")));
        Solon.app().router().get("/monitor/operlog/{id}", c -> guard(c, () -> logDetail(c, "oper")));
        Solon.app().router().delete("/monitor/operlog/clean", c -> guard(c, () -> clearLogs(c, "oper")));
        Solon.app().router().delete("/monitor/operlog/{ids}", c -> guard(c, () -> deleteLogs(c, "oper")));
        Solon.app().router().get("/monitor/logininfor/list", c -> guard(c, () -> logs(c, "login")));
        Solon.app().router().get("/monitor/logininfor/{id}", c -> guard(c, () -> logDetail(c, "login")));
        Solon.app().router().delete("/monitor/logininfor/clean", c -> guard(c, () -> clearLogs(c, "login")));
        Solon.app().router().delete("/monitor/logininfor/{ids}", c -> guard(c, () -> deleteLogs(c, "login")));
        Solon.app().router().put("/monitor/logininfor/unlock", c -> guard(c, () -> unlock(c)));
        Solon.app().router().get("/monitor/errorlog/list", c -> guard(c, () -> logs(c, "error")));
        Solon.app().router().get("/monitor/errorlog/{id}", c -> guard(c, () -> logDetail(c, "error")));
    }

    private void options(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":query", resource);
        List<Map<String, Object>> data = view(resource, crud.list(resource, p.getTenantId(), 0, 10000));
        if ("dept".equals(resource) || "menu".equals(resource)) data = tree(data);
        Map<String, Object> r = base();
        r.put("data", data);
        c.render(r);
    }

    private void list(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":list", resource);
        int page = Math.max(1, c.paramAsInt("pageNum", 1));
        // Menu management renders one client-side tree. Returning only the generic
        // first page detaches later children and button nodes from their parents.
        int defaultSize = "menu".equals(resource) ? 10000 : 20;
        int maxSize = "menu".equals(resource) ? 10000 : 200;
        int size = Math.min(maxSize, Math.max(1, c.paramAsInt("pageSize", defaultSize)));
        Map<String, String> filters = filters(c);
        DataScopeResult scope = "user".equals(resource) ? dataScope.resolve(p, "user") : null;
        long queryStarted = RequestPerformance.begin();
        List<Map<String, Object>> rows = scope == null ? crud.listFiltered(resource, p.getTenantId(), (page - 1) * size, size, filters) : crud.listUsers(p.getTenantId(), (page - 1) * size, size, scope, filters);
        if ("menu".equals(resource)) RequestPerformance.record(c, "menuQuery", queryStarted);
        long total;
        if (canDeriveTotal(resource, page, size, rows.size())) {
            // The first page contains the complete menu tree, so its size is the exact total.
            total = rows.size();
        } else {
            long countStarted = RequestPerformance.begin();
            total = scope == null ? crud.countFiltered(resource, p.getTenantId(), filters) : crud.countUsers(p.getTenantId(), scope, filters);
            if ("menu".equals(resource)) RequestPerformance.record(c, "menuCount", countStarted);
        }
        Map<String, Object> r = base();
        r.put("rows", view(resource, rows));
        r.put("total", total);
        long renderStarted = RequestPerformance.begin();
        c.render(r);
        if ("menu".equals(resource)) RequestPerformance.record(c, "menuRender", renderStarted);
    }

    static boolean canDeriveTotal(String resource, int page, int size, int rowCount) {
        return "menu".equals(resource) && page == 1 && rowCount < size;
    }

    private void get(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":query", resource);
        Map<String, Object> data = crud.get(resource, p.getTenantId(), Long.parseLong(c.pathMap("/system/" + resource + "/{id}").get("id")));
        if (data == null) {
            c.status(404);
            c.render(error(404, "数据不存在"));
            return;
        }
        Map<String, Object> r = base();
        r.put("data", view(resource, Collections.singletonList(data)).get(0));
        c.render(r);
    }

    private void add(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":add", resource);
        Map<String, Object> b = body(c);
        normalize(resource, b);
        protectPassword(resource, b);
        String quotaKey = "user".equals(resource) ? "users.max" : "role".equals(resource) ? "roles.max" : "notice".equals(resource) ? "notices.max" : null;
        if (quotaKey != null && TenantQuotaRegistry.get() != null)
            TenantQuotaRegistry.get().checkResource(p, quotaKey, crud.count(resource, p.getTenantId()), 1);
        long id;
        JdbcManagementRepository.TenantCreation tenantCreation = null;
        if ("tenant".equals(resource)) {
            tenantCreation = management.createTenant(b, p.getUsername());
            id = tenantCreation.id;
            List<TenantLifecycleContributor> initialized = new ArrayList<TenantLifecycleContributor>();
            try {
                for (TenantLifecycleContributor contributor : TenantContributorRegistry.lifecycle()) {
                    if ("pluginSystem".equals(contributor.pluginName())) continue;
                    contributor.initialize(id);
                    initialized.add(contributor);
                }
            } catch (RuntimeException failure) {
                Collections.reverse(initialized);
                for (TenantLifecycleContributor contributor : initialized) {
                    try { contributor.cleanup(id); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                }
                management.rollbackFailedTenantInitialization(id);
                throw failure;
            }
        } else id = crud.insert(resource, p.getTenantId(), b, p.getUsername());
        invalidate(resource, p, id);
        Map<String, Object> r = base();
        r.put("data", Collections.singletonMap("id", id));
        if (tenantCreation != null) {
            Map<String,Object> data = new LinkedHashMap<String,Object>();
            data.put("id",id); data.put("temporaryPassword",tenantCreation.temporaryPassword); data.put("passwordChangeRequired",true);
            r.put("data",data);
        }
        c.render(r);
    }

    private void update(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":edit", resource);
        Map<String, Object> b = body(c);
        long id = number(b.get("id"));
        normalize(resource, b);
        protectPassword(resource, b);
        if (!crud.update(resource, p.getTenantId(), id, b, p.getUsername())) {
            c.status(404);
            c.render(error(404, "数据不存在"));
            return;
        }
        invalidate(resource, p, id);
        c.render(base());
    }

    private void delete(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":remove", resource);
        List<Long> ids = parseIds(c.pathMap("/system/" + resource + "/{ids}").get("ids"));
        Map<Long, Set<Long>> affected = new LinkedHashMap<Long, Set<Long>>();
        Map<Long, Set<Long>> tenantUsers = new LinkedHashMap<Long, Set<Long>>();
        if ("tenant".equals(resource)) for (Long id : ids)
            tenantUsers.put(id, management.usersInTenant(id));
        if ("role".equals(resource)) for (Long id : ids)
            affected.computeIfAbsent(p.getTenantId(), k -> new LinkedHashSet<Long>()).addAll(management.usersForRole(p.getTenantId(), id));
        if ("menu".equals(resource)) for (Long id : ids)
            for (Map.Entry<Long, Set<Long>> e : management.usersForMenu(id).entrySet())
                affected.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<Long>()).addAll(e.getValue());
        if ("tenant".equals(resource)) {
            for (Long id : ids) tenantTasks.requestDelete(id, p.getUsername());
        } else management.deleteAggregate(resource, p.getTenantId(), ids);
        if ("user".equals(resource)) for (Long id : ids) provider.invalidate(p.getTenantId(), id);
        if ("tenant".equals(resource)) for (Map.Entry<Long, Set<Long>> entry : tenantUsers.entrySet())
            for (Long user : entry.getValue()) provider.kickout(entry.getKey(), user);
        for (Map.Entry<Long, Set<Long>> e : affected.entrySet())
            for (Long user : e.getValue()) provider.invalidate(e.getKey(), user);
        c.render(base());
    }

    private void profile(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, Object> r = base();
        r.put("data", crud.get("user", p.getTenantId(), p.getUserId()));
        c.render(r);
    }

    private void updateProfile(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, Object> b = body(c);
        String nickname = String.valueOf(b.get("nickname"));
        if (nickname.trim().isEmpty() || "null".equals(nickname)) throw new IllegalArgumentException("昵称不能为空");
        if (!crud.updateProfile(p.getTenantId(), p.getUserId(), b, p.getUsername()))
            throw new IllegalArgumentException("用户不存在");
        provider.invalidate(p.getTenantId(), p.getUserId());
        profile(c);
    }

    private void changeOwnPassword(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, Object> b = body(c);
        String old = String.valueOf(b.get("oldPassword")), next = String.valueOf(b.get("newPassword"));
        if (next.length() < 5 || next.length() > 50)
            throw new IllegalArgumentException("新密码长度必须在5-50个字符之间");
        String stored = crud.passwordHash(p.getTenantId(), p.getUserId());
        boolean matches = passwords.matches(old, stored);
        if (!matches) throw new IllegalArgumentException("原密码错误");
        crud.updateOwnPassword(p.getTenantId(), p.getUserId(), passwords.encode(next), p.getUsername());
        provider.invalidate(p.getTenantId(), p.getUserId());
        c.render(base());
    }

    private void logs(Context c, String kind) throws Throwable {
        String permission = "error".equals(kind) ? "monitor:errorlog:list" : "monitor:" + ("oper".equals(kind) ? "operlog" : "logininfor") + ":list";
        SecurityPrincipal p = require(c, permission, null);
        int page = Math.max(1, c.paramAsInt("pageNum", 1)), size = Math.min(200, Math.max(1, c.paramAsInt("pageSize", 20)));
        DataScopeResult scope = dataScope.resolve(p, kind + "-log");
        Map<String, String> f = filters(c);
        for (String k : Arrays.asList("title", "operName", "businessType", "userName", "ipaddr", "feature", "httpStatus", "exceptionType")) {
            String v = c.param(k);
            if (v != null) f.put(k, v);
        }
        List<Map<String, Object>> rows = crud.listLogs(kind, p.getTenantId(), (page - 1) * size, size, scope, p.getUsername(), f);
        Map<String, Object> r = base();
        r.put("rows", rows);
        r.put("total", crud.countLogs(kind, p.getTenantId(), scope, p.getUsername(), f));
        c.render(r);
    }

    private void logDetail(Context c, String kind) throws Throwable {
        SecurityPrincipal p = require(c, "monitor:" + ("oper".equals(kind) ? "operlog" : "login".equals(kind) ? "logininfor" : "errorlog") + ":query", null);
        String pattern = "/monitor/" + ("oper".equals(kind) ? "operlog" : "login".equals(kind) ? "logininfor" : "errorlog") + "/{id}";
        long id = Long.parseLong(c.pathMap(pattern).get("id"));
        DataScopeResult scope = dataScope.resolve(p, kind + "-log");
        Map<String, Object> d = crud.getLogScoped(kind, p.getTenantId(), id, scope, p.getUsername());
        if (d == null) {
            c.status(404);
            c.render(error(404, "日志不存在或无权访问"));
            return;
        }
        Map<String, Object> r = base();
        r.put("data", d);
        c.render(r);
    }

    private void deleteLogs(Context c, String kind) throws Throwable {
        SecurityPrincipal p = require(c, "monitor:" + ("oper".equals(kind) ? "operlog" : "logininfor") + ":remove", null);
        String pattern = "/monitor/" + ("oper".equals(kind) ? "operlog" : "logininfor") + "/{ids}";
        crud.deleteLogs(kind, p.getTenantId(), parseIds(c.pathMap(pattern).get("ids")));
        c.render(base());
    }

    private void clearLogs(Context c, String kind) throws Throwable {
        SecurityPrincipal p = require(c, "monitor:" + ("oper".equals(kind) ? "operlog" : "logininfor") + ":remove", null);
        crud.clearLogs(kind, p.getTenantId());
        c.render(base());
    }

    private void unlock(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "monitor:logininfor:unlock", null);
        Map<String, Object> b = body(c);
        Object u = b.get("userName");
        if (u == null) u = b.get("username");
        if (u == null || String.valueOf(u).trim().isEmpty()) throw new IllegalArgumentException("用户名不能为空");
        provider.unlock(p.getTenantId(), String.valueOf(u));
        c.render(base());
    }

    private void extFields(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        String type = c.pathMap("/system/extField/byEntity/{entityType}").get("entityType");
        Map<String, Object> r = base();
        r.put("data", crud.enabledExtFields(p.getTenantId(), type));
        c.render(r);
    }

    private void extValues(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "system:extField:query", "extField");
        Map<String, String> m = c.pathMap("/system/extValue/{entityType}/{entityId}");
        Map<String, Object> r = base();
        r.put("data", crud.extValues(p.getTenantId(), m.get("entityType"), Long.parseLong(m.get("entityId"))));
        c.render(r);
    }

    @SuppressWarnings("unchecked")
    private void saveExtValues(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "system:extField:edit", "extField");
        Map<String, Object> b = body(c);
        String type = String.valueOf(b.get("entityType"));
        long id = number(b.get("entityId"));
        Object raw = b.get("values");
        if (type.trim().isEmpty() || "null".equals(type) || id <= 0 || !(raw instanceof Map))
            throw new IllegalArgumentException("扩展属性参数不完整");
        crud.replaceExtValues(p.getTenantId(), type, id, (Map<String, Object>) raw, p.getUsername());
        c.render(base());
    }

    private void deleteExtValues(Context c, boolean one) throws Throwable {
        SecurityPrincipal p = require(c, "system:extField:remove", "extField");
        String pattern = one ? "/system/extValue/{entityType}/{entityId}/{fieldKey}" : "/system/extValue/{entityType}/{entityId}";
        Map<String, String> m = c.pathMap(pattern);
        crud.deleteExtValues(p.getTenantId(), m.get("entityType"), Long.parseLong(m.get("entityId")), one ? m.get("fieldKey") : null);
        c.render(base());
    }

    private SecurityPrincipal principal(Context c) {
        SecurityPrincipal p = c.attr("security.principal");
        if (p == null) throw new SecurityException("UNAUTHORIZED");
        return p;
    }

    private SecurityPrincipal require(Context c, String permission, String resource) {
        SecurityPrincipal p = c.attr("security.principal");
        if (permission != null && permission.startsWith("system:dictData:"))
            permission = "system:dict:" + permission.substring("system:dictData:".length());
        if (p == null || !provider.isAllowed(p, permission)) throw new SecurityException("FORBIDDEN");
        if (("tenant".equals(resource) || "menu".equals(resource) || "module".equals(resource)) && !provider.isAllowed(p, "platform:manage"))
            throw new SecurityException("PLATFORM_MANAGE_REQUIRED");
        return p;
    }

    private void invalidate(String resource, SecurityPrincipal p, long id) {
        if ("user".equals(resource)) {
            provider.invalidate(p.getTenantId(), id);
            return;
        }
        if ("role".equals(resource)) {
            for (Long u : management.usersForRole(p.getTenantId(), id)) provider.invalidate(p.getTenantId(), u);
            return;
        }
        if ("menu".equals(resource)) {
            for (Map.Entry<Long, Set<Long>> e : management.usersForMenu(id).entrySet())
                for (Long u : e.getValue()) provider.invalidate(e.getKey(), u);
        }
    }

    private void protectPassword(String resource, Map<String, Object> b) {
        if (!"user".equals(resource) || !b.containsKey("password")) return;
        String raw = String.valueOf(b.get("password"));
        if (raw.trim().isEmpty()) b.remove("password");
        else if (passwords.isGmEncoded(raw) || raw.startsWith("$2")) b.remove("password");
        else b.put("password", passwords.encode(raw));
    }

    private void normalize(String resource, Map<String, Object> b) {
        if ("dept".equals(resource) && b.containsKey("label")) b.put("deptName", b.get("label"));
        if (("dept".equals(resource) || "menu".equals(resource) || "module".equals(resource)) && b.containsKey("order"))
            b.put("orderNum", b.get("order"));
        if ("menu".equals(resource) && b.containsKey("visible")) {
            Object visible = b.get("visible");
            // Backward compatibility for clients that used booleans:
            // true means visible, while sys_menu stores visible as 0=shown, 1=hidden.
            if (visible instanceof Boolean) b.put("visible", Boolean.TRUE.equals(visible) ? "0" : "1");
            else b.put("visible", "1".equals(String.valueOf(visible)) ? "1" : "0");
        }
    }

    private List<Map<String, Object>> view(String resource, List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> source : rows) {
            Map<String, Object> row = new LinkedHashMap<String, Object>(source);
            if ("dept".equals(resource)) {
                row.put("label", row.get("deptName"));
                row.put("order", row.get("orderNum"));
            }
            if ("menu".equals(resource)) row.put("order", row.get("orderNum"));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> tree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<Long, Map<String, Object>>();
        for (Map<String, Object> row : rows) byId.put(number(row.get("id")), row);
        List<Map<String, Object>> roots = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            long parent = row.get("parentId") == null ? 0 : number(row.get("parentId"));
            Map<String, Object> owner = byId.get(parent);
            if (parent != 0 && owner != null) {
                @SuppressWarnings("unchecked") List<Map<String, Object>> children = (List<Map<String, Object>>) owner.computeIfAbsent("children", k -> new ArrayList<Map<String, Object>>());
                children.add(row);
            } else roots.add(row);
        }
        return roots;
    }

    private void dictByType(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "system:dict:query", "dict");
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("dictType", c.pathMap("/system/dict/data/type/{dictType}").get("dictType"));
        Map<String, Object> r = base();
        r.put("data", crud.listFiltered("dictData", p.getTenantId(), 0, 10000, f));
        c.render(r);
    }

    private void dictType(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "system:dict:query", "dict");
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("dictType", c.pathMap("/system/dict/type/{dictType}").get("dictType"));
        List<Map<String, Object>> rows = crud.listFiltered("dict", p.getTenantId(), 0, 1, f);
        Map<String, Object> r = base();
        r.put("data", rows.isEmpty() ? null : rows.get(0));
        c.render(r);
    }

    private void configByKey(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "system:config:query", "config");
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("configKey", c.pathMap("/system/config/key/{configKey}").get("configKey"));
        List<Map<String, Object>> rows = crud.listFiltered("config", p.getTenantId(), 0, 1, f);
        Map<String, Object> r = base();
        r.put("data", rows.isEmpty() ? null : rows.get(0).get("configValue"));
        c.render(r);
    }

    private void descendants(Context c) throws Throwable {
        SecurityPrincipal p = require(c, "system:dept:query", "dept");
        long id = Long.parseLong(c.pathMap("/system/dept/descendants/{id}").get("id"));
        List<Map<String, Object>> all = view("dept", crud.list("dept", p.getTenantId(), 0, 10000)), out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> d : all) {
            String a = String.valueOf(d.get("ancestors"));
            if (number(d.get("id")) == id || Arrays.asList(a.split(",")).contains(String.valueOf(id))) out.add(d);
        }
        Map<String, Object> r = base();
        r.put("data", out);
        c.render(r);
    }

    private void simpleStatus(Context c, String resource) throws Throwable {
        SecurityPrincipal p = require(c, "system:" + resource + ":edit", resource);
        Map<String, Object> b = body(c);
        long id = number(b.get("id"));
        if ("dict".equals(resource)) {
            Map<String, Object> d = crud.get(resource, p.getTenantId(), id);
            if (d != null && "Y".equals(d.get("isSystem")) && "1".equals(String.valueOf(b.get("status"))))
                throw new IllegalArgumentException("系统内置字典不可停用");
        }
        Map<String, Object> u = new LinkedHashMap<String, Object>();
        u.put("status", b.get("status"));
        if (!crud.update(resource, p.getTenantId(), id, u, p.getUsername()))
            throw new IllegalArgumentException("数据不存在");
        c.render(base());
    }

    private void cacheRefresh(Context c, String resource) throws Throwable {
        require(c, "system:" + resource + ":edit", resource);
        c.render(base());
    }

    private void published(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("status", "0");
        Map<String, Object> r = base();
        r.put("data", crud.listFiltered("notice", p.getTenantId(), 0, 100, f));
        c.render(r);
    }

    private void noticeInbox(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        int limit = Math.max(1, Math.min(100, c.paramAsInt("limit", 10)));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", crud.noticeInbox(p.getTenantId(), p.getUserId(), limit));
        data.put("unreadCount", crud.unreadNoticeCount(p.getTenantId(), p.getUserId()));
        Map<String, Object> r = base();
        r.put("data", data);
        c.render(r);
    }

    private void markNoticeRead(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        long id = Long.parseLong(c.pathMap("/system/notice/{id}/read").get("id"));
        if (!crud.markNoticeRead(p.getTenantId(), p.getUserId(), id))
            throw new IllegalArgumentException("通知公告不存在或已关闭");
        c.render(base());
    }

    private void markAllNoticesRead(Context c) throws Throwable {
        SecurityPrincipal p = principal(c);
        crud.markAllNoticesRead(p.getTenantId(), p.getUserId());
        c.render(base());
    }

    private Map<String, String> filters(Context c) {
        Map<String, String> f = new LinkedHashMap<String, String>();
        for (String k : Arrays.asList("keyword", "username", "nickname", "phone", "status", "deptId", "roleName", "roleKey", "tenantName", "tenantCode", "menuName", "moduleId", "deptName", "label", "moduleName", "moduleCode", "dictName", "dictType", "dictLabel", "configName", "configKey", "noticeTitle", "noticeType", "beginTime", "endTime")) {
            String v = c.param(k);
            if (v != null) f.put(k, v);
        }
        return f;
    }

    private void guard(Context c, Action action) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable failure = null;
        captureAuditRequest(c);
        try {
            action.run();
        } catch (SecurityException e) {
            failure = e;
            c.status(403);
            c.render(error(403, e.getMessage()));
        } catch (IllegalArgumentException e) {
            failure = e;
            c.status(400);
            c.render(error(400, e.getMessage()));
        } catch (Throwable e) {
            failure = e;
            recordError(c, e);
            c.status(500);
            c.render(error(500, "系统处理失败"));
        } finally {
            audit(c, failure, System.currentTimeMillis() - start);
        }
    }

    private void recordError(Context c, Throwable e) {
        SecurityPrincipal p = c.attr("security.principal");
        crud.errorAudit(p == null ? 1 : p.getTenantId(), p == null ? null : p.getUserId(), p == null ? "system" : p.getUsername(), "权限插件", c.method(), c.path(), c.remoteIp(), 500, e);
    }

    private void audit(Context c, Throwable failure, long cost) {
        String method = c.method();
        if ("GET".equalsIgnoreCase(method)) return;
        SecurityPrincipal p = c.attr("security.principal");
        if (p == null) return;
        Map<String, Object> u = crud.get("user", p.getTenantId(), p.getUserId());
        String dept = u == null ? "" : String.valueOf(u.get("deptName")), path = c.path();
        int type = "POST".equalsIgnoreCase(method) ? 1 : "DELETE".equalsIgnoreCase(method) ? (path.endsWith("/clean") ? 9 : 3) : path.contains("auth") || path.contains("dataScope") ? 4 : 2;
        String title = path.startsWith("/system/user") ? "用户管理" : path.startsWith("/system/role") ? "角色管理" : path.startsWith("/system/menu") ? "菜单管理" : path.startsWith("/system/module") ? "模块管理" : path.startsWith("/system/dept") ? "部门管理" : path.startsWith("/system/tenant") ? "租户管理" : path.startsWith("/system/dict") ? "字典管理" : path.startsWith("/system/config") ? "参数管理" : path.startsWith("/system/notice") ? "通知公告" : "日志管理";
        boolean success = failure == null && c.status() < 400;
        String parameters = c.attr(AUDIT_REQUEST_BODY);
        String result = success
                ? "{\"code\":" + (c.status() > 0 ? c.status() : 200) + ",\"msg\":\"操作成功\"}"
                : "{\"code\":" + (c.status() > 0 ? c.status() : 500) + ",\"msg\":\"" + jsonText(failure == null ? "操作失败" : failure.getMessage()) + "\"}";
        crud.audit(p.getTenantId(), p.getUsername(), dept, title, type, "permission-plugin", method, path,
                c.remoteIp(), parameters == null ? "" : parameters, result, success,
                failure == null ? "" : failure.getMessage(), cost);
    }

    private void captureAuditRequest(Context c) {
        String value = "";
        try {
            String body = c.body();
            if (body != null && !body.trim().isEmpty()) value = body.trim();
        } catch (Throwable ignored) {
            // Auditing must never prevent the business request from running.
        }
        if (value.isEmpty() && c.queryString() != null) value = c.queryString();
        c.attrSet(AUDIT_REQUEST_BODY, cutAudit(AUDIT_SECRET.matcher(value).replaceAll("$1***")));
    }

    private static String jsonText(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String cutAudit(String value) {
        return value == null || value.length() <= 2000 ? (value == null ? "" : value) : value.substring(0, 2000);
    }

    private interface Action {
        void run() throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Context c) throws Exception {
        return json.readValue(c.body(), Map.class);
    }

    private static long number(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private static List<Long> parseIds(String value) {
        List<Long> ids = new ArrayList<Long>();
        for (String v : value.split(",")) ids.add(Long.parseLong(v));
        return ids;
    }

    private static Map<String, Object> base() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("code", 200);
        r.put("msg", "操作成功");
        return r;
    }

    private static Map<String, Object> error(int code, String msg) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("code", code);
        r.put("msg", msg);
        return r;
    }
}
