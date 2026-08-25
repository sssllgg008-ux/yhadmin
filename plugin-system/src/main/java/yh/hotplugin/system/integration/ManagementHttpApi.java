package yh.hotplugin.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.noear.solon.Solon;
import org.noear.solon.core.handle.Context;
import yh.hotplugin.security.api.SecurityPrincipal;
import yh.hotplugin.security.tenant.TenantContext;
import yh.hotplugin.system.application.SystemPermissionProvider;
import yh.hotplugin.system.infrastructure.JdbcCrudRepository;
import yh.hotplugin.system.infrastructure.JdbcManagementRepository;
import yh.hotplugin.system.security.GmPasswordEncoder;

import java.util.*;

/**
 * Compatibility write endpoints for status, credentials and RBAC assignments.
 */
final class ManagementHttpApi {
    static final String[] ROUTES = {"/system/user/changeStatus", "/system/user/resetPwd", "/system/user/authRole", "/system/user/authRole/{userId}", "/system/role/changeStatus", "/system/role/dataScope", "/system/role/authMenu", "/system/role/authMenu/{roleId}", "/system/role/authUser", "/system/role/authUser/{roleId}", "/system/role/{roleId}/users", "/system/role/authUser/change", "/system/role/authUser/add", "/system/role/authUser/{roleId}/{userId}", "/system/role/authUser/{roleId}/batch/{userIds}", "/system/menu/changeStatus", "/system/dept/changeStatus", "/system/tenant/changeStatus", "/system/tenant/resetAdminPwd"};
    private final ObjectMapper json;
    private final JdbcManagementRepository repository;
    private final JdbcCrudRepository crud;
    private final SystemPermissionProvider provider;
    private final GmPasswordEncoder passwords = new GmPasswordEncoder();

    ManagementHttpApi(ObjectMapper json, JdbcManagementRepository repository, JdbcCrudRepository crud, SystemPermissionProvider provider) {
        this.json = json;
        this.repository = repository;
        this.crud = crud;
        this.provider = provider;
    }

    void register() {
        Solon.app().router().put("/system/user/changeStatus", c -> guard(c, () -> status(c, "user", "sys_user")));
        Solon.app().router().put("/system/role/changeStatus", c -> guard(c, () -> status(c, "role", "sys_role")));
        Solon.app().router().put("/system/menu/changeStatus", c -> guard(c, () -> status(c, "menu", "sys_menu")));
        Solon.app().router().put("/system/dept/changeStatus", c -> guard(c, () -> status(c, "dept", "sys_dept")));
        Solon.app().router().put("/system/tenant/changeStatus", c -> guard(c, () -> tenantStatus(c)));
        Solon.app().router().put("/system/tenant/resetAdminPwd", c -> guard(c, () -> resetTenantAdminPassword(c)));
        Solon.app().router().put("/system/user/resetPwd", c -> guard(c, () -> resetPassword(c)));
        Solon.app().router().get("/system/user/authRole/{userId}", c -> guard(c, () -> userRoleInfo(c)));
        Solon.app().router().put("/system/user/authRole", c -> guard(c, () -> userRoles(c)));
        Solon.app().router().get("/system/role/authMenu/{roleId}", c -> guard(c, () -> roleMenuInfo(c)));
        Solon.app().router().put("/system/role/authMenu", c -> guard(c, () -> roleMenus(c)));
        Solon.app().router().get("/system/role/authUser/{roleId}", c -> guard(c, () -> authorizedUsers(c)));
        Solon.app().router().get("/system/role/{roleId}/users", c -> guard(c, () -> authorizedUsers(c)));
        Solon.app().router().put("/system/role/authUser", c -> guard(c, () -> roleUsers(c)));
        Solon.app().router().put("/system/role/authUser/change", c -> guard(c, () -> changeRoleUsers(c)));
        Solon.app().router().post("/system/role/authUser/add", c -> guard(c, () -> addRoleUsers(c)));
        Solon.app().router().delete("/system/role/authUser/{roleId}/batch/{userIds}", c -> guard(c, () -> revokeRoleUsers(c, true)));
        Solon.app().router().delete("/system/role/authUser/{roleId}/{userId}", c -> guard(c, () -> revokeRoleUsers(c, false)));
        Solon.app().router().put("/system/role/dataScope", c -> guard(c, () -> dataScope(c)));
    }

    private void status(Context c, String resource, String table) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:" + resource + ":edit");
        Map<String, Object> b = body(c);
        long id = id(b);
        if ("menu".equals(resource) && !provider.isAllowed(p, "platform:manage"))
            throw new SecurityException("PLATFORM_MANAGE_REQUIRED");
        Set<Long> roleUsers = "role".equals(resource) ? repository.usersForRole(p.getTenantId(), id) : Collections.<Long>emptySet();
        Map<Long, Set<Long>> menuUsers = "menu".equals(resource) ? repository.usersForMenu(id) : Collections.<Long, Set<Long>>emptyMap();
        repository.changeStatus(table, p.getTenantId(), id, text(b, "status"));
        if ("user".equals(resource)) provider.invalidate(p.getTenantId(), id);
        for (Long user : roleUsers) provider.invalidate(p.getTenantId(), user);
        for (Map.Entry<Long, Set<Long>> e : menuUsers.entrySet())
            for (Long user : e.getValue()) provider.invalidate(e.getKey(), user);
        ok(c);
    }

    private void tenantStatus(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:tenant:edit");
        if (!provider.isAllowed(p, "platform:manage"))
            throw new SecurityException("PLATFORM_MANAGE_REQUIRED");
        Map<String, Object> b = body(c);
        long tenant = id(b);
        repository.changeTenantStatus(tenant, text(b, "status"));
        for (Long user : repository.usersInTenant(tenant)) provider.kickout(tenant, user);
        ok(c);
    }

    private void resetPassword(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:user:resetPwd");
        Map<String, Object> b = body(c);
        long id = id(b);
        repository.resetPassword(p.getTenantId(), id, passwords.encode(text(b, "password")));
        provider.invalidate(p.getTenantId(), id);
        ok(c);
    }

    private void resetTenantAdminPassword(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:tenant:edit");
        if (!provider.isAllowed(p, "platform:manage"))
            throw new SecurityException("PLATFORM_MANAGE_REQUIRED");
        long tenantId = id(body(c));
        String password;
        long adminUserId;
        try (TenantContext.Scope ignored = TenantContext.openPlatform(p)) {
            password = repository.resetTenantAdminPassword(tenantId);
            adminUserId = repository.tenantAdminUserId(tenantId);
        }
        provider.kickout(tenantId, adminUserId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("username", "admin");
        data.put("temporaryPassword", password);
        data.put("passwordChangeRequired", true);
        Map<String, Object> result = response();
        result.put("data", data);
        c.render(result);
    }

    private void userRoles(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:user:edit");
        Map<String, Object> b = body(c);
        long user = id(b);
        repository.replaceUserRoles(p.getTenantId(), user, ids(b.get("roleIds")));
        provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private void userRoleInfo(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:user:query");
        Set<Long> ids = repository.roleIdsForUser(p.getTenantId(), Long.parseLong(c.pathMap("/system/user/authRole/{userId}").get("userId")));
        List<Map<String, Object>> all = crud.list("role", p.getTenantId(), 0, 10000), assigned = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> role : all)
            if (ids.contains(((Number) role.get("id")).longValue())) assigned.add(role);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("roles", assigned);
        data.put("total", assigned.size());
        data.put("allRoles", all);
        Map<String, Object> r = response();
        r.put("data", data);
        c.render(r);
    }

    private void roleMenus(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:edit");
        Map<String, Object> b = body(c);
        long role = id(b);
        Set<Long> users = repository.usersForRole(p.getTenantId(), role);
        repository.replaceRoleMenus(p.getTenantId(), role, ids(b.get("menuIds")));
        for (Long user : users) provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private void roleMenuInfo(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:query");
        Map<String, Object> r = response();
        r.put("data", new ArrayList<Long>(repository.menuIdsForRole(p.getTenantId(), Long.parseLong(c.pathMap("/system/role/authMenu/{roleId}").get("roleId")))));
        c.render(r);
    }

    private void roleUsers(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:edit");
        Map<String, Object> b = body(c);
        long role = id(b);
        Set<Long> before = repository.usersForRole(p.getTenantId(), role);
        Collection<Long> after = ids(b.get("userIds"));
        repository.replaceRoleUsers(p.getTenantId(), role, after);
        before.addAll(after);
        for (Long user : before) provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private void authorizedUsers(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:query");
        String pattern = c.path().contains("/authUser/") ? "/system/role/authUser/{roleId}" : "/system/role/{roleId}/users";
        long role = Long.parseLong(c.pathMap(pattern).get("roleId"));
        if (pattern.contains("/users")) {
            String assignedValue = c.param("assigned");
            boolean assigned = assignedValue == null || Boolean.parseBoolean(assignedValue);
            int page = Math.max(1, c.paramAsInt("pageNum", 1)), size = Math.min(200, Math.max(1, c.paramAsInt("pageSize", 20)));
            Long dept = c.param("deptId") == null ? null : Long.valueOf(c.param("deptId"));
            List<Map<String, Object>> users = crud.listRoleUsers(p.getTenantId(), role, assigned, c.param("keyword"), c.param("phone"), dept, c.param("status"), (page - 1) * size, size);
            Map<String, Object> r = response();
            r.put("rows", users);
            r.put("total", crud.countRoleUsers(p.getTenantId(), role, assigned, c.param("keyword"), c.param("phone"), dept, c.param("status")));
            c.render(r);
        } else {
            Set<Long> ids = repository.usersForRole(p.getTenantId(), role);
            List<Map<String, Object>> users = new ArrayList<Map<String, Object>>();
            for (Long user : ids) {
                Map<String, Object> row = crud.get("user", p.getTenantId(), user);
                if (row != null) users.add(row);
            }
            Map<String, Object> r = response();
            r.put("data", users);
            c.render(r);
        }
    }

    private void changeRoleUsers(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:edit");
        Map<String, Object> b = body(c);
        long role = id(b);
        Set<Long> before = repository.usersForRole(p.getTenantId(), role), users = new LinkedHashSet<Long>(before);
        Collection<Long> added = ids(b.get("addUserIds")), removed = ids(b.get("removeUserIds"));
        users.addAll(added);
        users.removeAll(removed);
        repository.replaceRoleUsers(p.getTenantId(), role, users);
        before.addAll(added);
        before.addAll(removed);
        for (Long user : before) provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private void addRoleUsers(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:edit");
        Map<String, Object> b = body(c);
        long role = id(b);
        Set<Long> users = repository.usersForRole(p.getTenantId(), role);
        Collection<Long> added = ids(b.get("userIds"));
        users.addAll(added);
        repository.replaceRoleUsers(p.getTenantId(), role, users);
        for (Long user : added) provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private void revokeRoleUsers(Context c, boolean batch) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:edit");
        String pattern = batch ? "/system/role/authUser/{roleId}/batch/{userIds}" : "/system/role/authUser/{roleId}/{userId}";
        Map<String, String> path = c.pathMap(pattern);
        long role = Long.parseLong(path.get("roleId"));
        Set<Long> removed = new LinkedHashSet<Long>();
        for (String value : (batch ? path.get("userIds") : path.get("userId")).split(","))
            if (!value.trim().isEmpty()) removed.add(Long.parseLong(value.trim()));
        Set<Long> users = repository.usersForRole(p.getTenantId(), role);
        users.removeAll(removed);
        repository.replaceRoleUsers(p.getTenantId(), role, users);
        for (Long user : removed) provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private void dataScope(Context c) throws Throwable {
        SecurityPrincipal p = allowed(c, "system:role:edit");
        Map<String, Object> b = body(c);
        long role = id(b);
        repository.updateRoleDataScope(p.getTenantId(), role, ((Number) b.get("dataScope")).intValue());
        repository.replaceRoleDepartments(p.getTenantId(), role, ids(b.get("deptIds")));
        for (Long user : repository.usersForRole(p.getTenantId(), role)) provider.invalidate(p.getTenantId(), user);
        ok(c);
    }

    private SecurityPrincipal allowed(Context c, String permission) {
        SecurityPrincipal p = c.attr("security.principal");
        if (p == null || !provider.isAllowed(p, permission)) throw new SecurityException("FORBIDDEN");
        return p;
    }

    private void guard(Context c, Action action) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable failure = null;
        try {
            action.run();
        } catch (SecurityException e) {
            failure = e;
            c.status(403);
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("code", 403);
            r.put("msg", e.getMessage());
            c.render(r);
        } catch (IllegalArgumentException e) {
            failure = e;
            c.status(400);
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("code", 400);
            r.put("msg", e.getMessage());
            c.render(r);
        } catch (Throwable e) {
            failure = e;
            SecurityPrincipal p = c.attr("security.principal");
            crud.errorAudit(p == null ? 1 : p.getTenantId(), p == null ? null : p.getUserId(), p == null ? "system" : p.getUsername(), "权限管理", c.method(), c.path(), c.remoteIp(), 500, e);
            c.status(500);
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("code", 500);
            r.put("msg", "系统处理失败");
            c.render(r);
        } finally {
            audit(c, failure, System.currentTimeMillis() - start);
        }
    }

    private void audit(Context c, Throwable failure, long cost) {
        if ("GET".equalsIgnoreCase(c.method())) return;
        SecurityPrincipal p = c.attr("security.principal");
        if (p == null) return;
        Map<String, Object> u = crud.get("user", p.getTenantId(), p.getUserId());
        String dept = u == null ? "" : String.valueOf(u.get("deptName"));
        String path = c.path();
        String title = path.startsWith("/system/user") ? "用户管理" : path.startsWith("/system/role") ? "角色管理" : path.startsWith("/system/menu") ? "菜单管理" : path.startsWith("/system/dept") ? "部门管理" : "租户管理";
        int type = path.contains("auth") || path.contains("dataScope") ? 4 : 2;
        crud.audit(p.getTenantId(), p.getUsername(), dept, title, type, "ManagementHttpApi", c.method(), path, c.remoteIp(), "[request body omitted]", failure == null, failure == null ? "" : failure.getMessage(), cost);
    }

    private interface Action {
        void run() throws Throwable;
    }

    private Map<String, Object> response() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("code", 200);
        r.put("msg", "操作成功");
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Context c) throws Exception {
        return json.readValue(c.body(), Map.class);
    }

    private long id(Map<String, Object> b) {
        Object v = b.containsKey("id") ? b.get("id") : b.get("userId");
        if (v == null) v = b.get("roleId");
        return ((Number) v).longValue();
    }

    private String text(Map<String, Object> b, String key) {
        Object v = b.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private Collection<Long> ids(Object value) {
        List<Long> out = new ArrayList<Long>();
        if (value instanceof Collection) for (Object v : (Collection<Object>) value) out.add(((Number) v).longValue());
        return out;
    }

    private void ok(Context c) throws Throwable {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("code", 200);
        r.put("msg", "操作成功");
        c.render(r);
    }
}
