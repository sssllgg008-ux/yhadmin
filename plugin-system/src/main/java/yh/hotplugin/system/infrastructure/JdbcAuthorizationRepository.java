package yh.hotplugin.system.infrastructure;

import yh.hotplugin.security.api.DataScopeResult;
import yh.hotplugin.system.domain.model.*;
import yh.hotplugin.system.domain.repository.AuthorizationRepository;
import yh.hotplugin.system.infrastructure.mybatis.MybatisExecutor;

import java.util.*;

/**
 * MyBatis-Plus authorization repository. Name retained for binary compatibility.
 */
public final class JdbcAuthorizationRepository implements AuthorizationRepository, AutoCloseable {
    private final MybatisExecutor db;
    private final long defaultTenantId;

    public JdbcAuthorizationRepository(String driver, String url, String username, String password, long tenant) {
        db = new MybatisExecutor(driver, url, username, password);
        defaultTenantId = tenant;
    }

    public MybatisExecutor executor() {
        return db;
    }

    public User findUser(long id) {
        AuthorizationSnapshot s = findById(defaultTenantId, id);
        return s == null ? null : new User(s.getUserId(), s.getUsername(), s.isEnabled());
    }

    public Role findRole(long id) {
        return null;
    }

    public AuthorizationSnapshot findById(long t, long id) {
        return query(t, "u.id = ?", id);
    }

    public AuthorizationSnapshot findByUsername(long t, String name) {
        return query(t, "u.username = ?", name);
    }

    public boolean updatePasswordHash(long tenantId, long userId, String passwordHash) {
        return db.update("UPDATE sys_user SET password=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
                Arrays.<Object>asList(passwordHash, tenantId, userId)) == 1;
    }

    private AuthorizationSnapshot query(long t, String predicate, Object value) {
        String sql = "SELECT u.id,u.username,u.password,u.status user_status,u.password_change_required,t.status tenant_status,r.role_key,m.perms FROM sys_user u JOIN sys_tenant t ON t.id=u.tenant_id LEFT JOIN sys_tenant_lifecycle tl ON tl.tenant_id=t.id LEFT JOIN sys_user_role ur ON ur.user_id=u.id AND ur.tenant_id=u.tenant_id LEFT JOIN sys_role r ON r.id=ur.role_id AND r.tenant_id=u.tenant_id AND r.status='0' LEFT JOIN sys_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=u.tenant_id LEFT JOIN sys_menu m ON m.id=rm.menu_id AND m.status='0' WHERE u.tenant_id=? AND " + predicate + " AND COALESCE(tl.lifecycle_status,'ACTIVE')='ACTIVE' AND (t.expire_time IS NULL OR t.expire_time>CURRENT_TIMESTAMP) AND (u.password_change_required<>'1' OR u.temp_password_expire_time IS NULL OR u.temp_password_expire_time>CURRENT_TIMESTAMP)";
        List<Map<String, Object>> rows = db.query(sql, Arrays.<Object>asList(t, value));
        if (rows.isEmpty()) return null;
        Map<String, Object> first = rows.get(0);
        Set<String> roles = new LinkedHashSet<String>(), perms = new LinkedHashSet<String>();
        for (Map<String, Object> r : rows) {
            String role = text(r, "roleKey");
            if (role != null) {
                roles.add(role);
                if ("admin".equals(role)) perms.add("*");
            }
            String perm = text(r, "perms");
            if (perm != null && !perm.trim().isEmpty()) perms.add(perm);
        }
        return new AuthorizationSnapshot(number(first, "id"), t, text(first, "username"), text(first, "password"), "0".equals(text(first, "userStatus")), "0".equals(text(first, "tenantStatus")), "1".equals(text(first,"passwordChangeRequired")), roles, perms);
    }

    public List<MenuItem> findMenus(long t, long user) {
        AuthorizationSnapshot s = findById(t, user);
        if (s == null || !s.isEnabled()) return Collections.emptyList();
        boolean admin = s.getRoles().contains("admin");
        String sql = "SELECT DISTINCT m.id,m.parent_id,m.module_id,m.menu_name,m.menu_type,m.order_num,m.path,m.component,m.icon,m.perms,m.visible FROM sys_menu m " + (admin ? "" : "JOIN sys_role_menu rm ON rm.menu_id=m.id AND rm.tenant_id=? JOIN sys_user_role ur ON ur.role_id=rm.role_id AND ur.tenant_id=? AND ur.user_id=? JOIN sys_role r ON r.id=ur.role_id AND r.tenant_id=? AND r.status='0' ") + "WHERE m.status='0' ORDER BY m.order_num,m.id";
        List<Object> a = admin ? Collections.<Object>emptyList() : Arrays.<Object>asList(t, t, user, t);
        List<MenuItem> out = new ArrayList<MenuItem>();
        for (Map<String, Object> r : db.query(sql, a))
            out.add(new MenuItem(number(r, "id"), number(r, "parentId"), number(r, "moduleId"), text(r, "menuName"), text(r, "menuType"), (int) number(r, "orderNum"), text(r, "path"), text(r, "component"), text(r, "icon"), text(r, "perms"), text(r, "visible")));
        return out;
    }

    public List<Map<String, Object>> findRouterTree(long t, long user) {
        List<MenuItem> all = findMenus(t, user);
        Map<Long, List<MenuItem>> by = new LinkedHashMap<Long, List<MenuItem>>();
        for (MenuItem m : all) {
            if (!("M".equals(m.getMenuType()) || "C".equals(m.getMenuType()) || "I".equals(m.getMenuType())) || !"0".equals(m.getVisible()))
                continue;
            by.computeIfAbsent(m.getModuleId(), k -> new ArrayList<MenuItem>()).add(m);
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        List<MenuItem> unbound = by.remove(0L);
        if (unbound != null) out.addAll(children(unbound, 0, ""));
        for (Map<String, Object> r : db.query("SELECT id,module_name,module_code,icon FROM sys_module WHERE status='0' ORDER BY order_num,id", Collections.<Object>emptyList())) {
            long id = number(r, "id");
            List<MenuItem> menus = by.get(id);
            if (menus == null) continue;
            List<Map<String, Object>> c = children(menus, 0, text(r, "moduleCode"));
            if (c.isEmpty()) continue;
            Map<String, Object> n = new LinkedHashMap<String, Object>();
            n.put("id", "module-" + id);
            n.put("name", text(r, "moduleName"));
            n.put("path", text(r, "moduleCode"));
            n.put("component", null);
            n.put("icon", text(r, "icon"));
            n.put("menuType", "M");
            n.put("perms", null);
            n.put("children", c);
            out.add(n);
        }
        return out;
    }

    private List<Map<String, Object>> children(List<MenuItem> menus, long parent, String base) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (MenuItem m : menus)
            if (m.getParentId() == parent) {
                String part = m.getPath() == null ? "" : m.getPath(), path = base == null || base.isEmpty() ? part : part.isEmpty() ? base : base + "/" + part;
                Map<String, Object> n = new LinkedHashMap<String, Object>();
                n.put("id", m.getId());
                n.put("name", m.getMenuName());
                n.put("path", path);
                n.put("component", m.getComponent());
                n.put("icon", m.getIcon());
                n.put("menuType", m.getMenuType());
                n.put("perms", m.getPerms());
                List<Map<String, Object>> c = children(menus, m.getId(), path);
                if (!c.isEmpty()) n.put("children", c);
                out.add(n);
            }
        return out;
    }

    public void recordLogin(long t, String u, boolean ok, String msg) {
        try {
            db.insert("INSERT INTO sys_logininfor(tenant_id,username,status,msg,login_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP)", Arrays.<Object>asList(t, u, ok ? "0" : "1", msg));
        } catch (RuntimeException e) {
            System.err.println("[permission-audit] " + e.getMessage());
        }
    }

    public void enrichLatestLogin(long t, String u, String ip, String loc, String browser, String os) {
        try {
            db.update("UPDATE sys_logininfor SET ipaddr=?,login_location=?,browser=?,os=? WHERE tenant_id=? AND username=? ORDER BY id DESC LIMIT 1", Arrays.<Object>asList(ip, loc, browser, os, t, u));
        } catch (RuntimeException e) {
            System.err.println("[permission-audit] " + e.getMessage());
        }
    }

    public DataScopeResult resolveDataScope(long t, long user) {
        String sql = "SELECT u.dept_id,r.role_key,r.data_scope,rd.dept_id custom_dept FROM sys_user u JOIN sys_tenant t ON t.id=u.tenant_id AND t.status='0' LEFT JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id LEFT JOIN sys_role r ON r.tenant_id=u.tenant_id AND r.id=ur.role_id AND r.status='0' LEFT JOIN sys_role_dept rd ON rd.tenant_id=r.tenant_id AND rd.role_id=r.id WHERE u.tenant_id=? AND u.id=? AND u.status='0'";
        List<Map<String, Object>> rows = db.query(sql, Arrays.<Object>asList(t, user));
        if (rows.isEmpty()) return DataScopeResult.deny();
        Long own = nullable(rows.get(0), "deptId");
        Set<Long> deps = new LinkedHashSet<Long>();
        boolean dept = false, below = false, self = false;
        for (Map<String, Object> r : rows) {
            int scope = (int) number(r, "dataScope");
            if ("admin".equals(text(r, "roleKey")) || scope == 1)
                return new DataScopeResult(DataScopeResult.Scope.ALL, null, null);
            if (scope == 2 && nullable(r, "customDept") != null) deps.add(nullable(r, "customDept"));
            else if (scope == 3) dept = true;
            else if (scope == 4) below = true;
            else if (scope == 5) self = true;
        }
        if ((dept || below) && own != null) deps.add(own);
        if (below && own != null) {
            for (Map<String, Object> r : db.query("SELECT id,ancestors FROM sys_dept WHERE tenant_id=? AND status='0'", Collections.<Object>singletonList(t))) {
                String a = text(r, "ancestors");
                if (a != null && Arrays.asList(a.split(",")).contains(String.valueOf(own))) deps.add(number(r, "id"));
            }
            return new DataScopeResult(DataScopeResult.Scope.DEPT_AND_BELOW, deps, null);
        }
        if (!deps.isEmpty())
            return new DataScopeResult(dept && deps.size() == 1 ? DataScopeResult.Scope.DEPT : DataScopeResult.Scope.CUSTOM, deps, null);
        if (self) return new DataScopeResult(DataScopeResult.Scope.SELF, null, user);
        return DataScopeResult.deny();
    }

    private static String text(Map<String, Object> r, String k) {
        Object v = r.get(k);
        if (v == null && "password".equals(k)) v = r.get("passwordHash");
        return v == null ? null : String.valueOf(v);
    }

    private static long number(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static Long nullable(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v == null ? null : ((Number) v).longValue();
    }

    public void close() {
        db.close();
    }
}
