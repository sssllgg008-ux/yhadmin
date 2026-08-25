package yh.hotplugin.system.infrastructure;

import yh.hotplugin.security.api.*;
import yh.hotplugin.system.infrastructure.mybatis.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Whitelisted CRUD repository implemented through MyBatis-Plus.
 */
public final class JdbcCrudRepository {
    private static final Map<String, Spec> SPECS = new LinkedHashMap<String, Spec>();

    static {
        SPECS.put("user", new Spec("sys_user", true, "dept_id,dept_name,username,nickname,phone,email,status,password,remark"));
        SPECS.put("role", new Spec("sys_role", true, "role_name,role_key,sort,status,data_scope,remark"));
        SPECS.put("dept", new Spec("sys_dept", true, "parent_id,ancestors,dept_name,order_num,leader,phone,email,status,remark"));
        SPECS.put("menu", new Spec("sys_menu", false, "parent_id,module_id,menu_name,menu_type,order_num,path,component,icon,perms,status,visible,remark"));
        SPECS.put("tenant", new Spec("sys_tenant", false, "tenant_name,tenant_code,contact,phone,email,expire_time,status,remark"));
        SPECS.put("module", new Spec("sys_module", false, "module_name,module_code,icon,order_num,status,remark"));
        SPECS.put("dict", new Spec("sys_dict_type", true, "dict_name,dict_type,status,is_system,remark"));
        SPECS.put("dictData", new Spec("sys_dict_data", true, "dict_type_id,dict_type,dict_label,dict_value,dict_sort,list_class,css_class,is_default,status,remark"));
        SPECS.put("config", new Spec("sys_config", true, "config_name,config_key,config_value,config_type,remark"));
        SPECS.put("notice", new Spec("sys_notice", true, "notice_title,notice_type,notice_content,status,remark"));
        SPECS.put("extField", new Spec("sys_ext_field", true, "entity_type,field_key,field_label,field_type,dict_type,sort,status,remark"));
    }

    private final MybatisExecutor db;

    public JdbcCrudRepository(JdbcAuthorizationRepository authorization) {
        db = authorization.executor();
        ensureErrorAuditTable();
        ensureNoticeReadTable();
    }

    private void ensureErrorAuditTable() {
        db.update("CREATE TABLE IF NOT EXISTS sys_error_log (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,user_id BIGINT NULL,username VARCHAR(64) NOT NULL DEFAULT 'system',feature VARCHAR(200) NOT NULL DEFAULT '未分类',request_id VARCHAR(64) NOT NULL,request_method VARCHAR(16) NOT NULL,request_uri VARCHAR(1000) NOT NULL,request_ip VARCHAR(64) NULL,http_status INT NOT NULL,error_code VARCHAR(64) NULL,exception_type VARCHAR(255) NOT NULL,error_message VARCHAR(2000) NULL,stack_trace MEDIUMTEXT NULL,error_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),KEY idx_error_log_tenant_time(tenant_id,error_time,id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
    }

    private void ensureNoticeReadTable() {
        db.update("CREATE TABLE IF NOT EXISTS sys_notice_read (id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,notice_id BIGINT NOT NULL,read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(id),UNIQUE KEY uk_notice_read_tenant_user_notice(tenant_id,user_id,notice_id),KEY idx_notice_read_notice(tenant_id,notice_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", Collections.<Object>emptyList());
    }

    public List<Map<String, Object>> noticeInbox(long tenantId, long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return db.query("SELECT n.*,CASE WHEN r.read_time IS NOT NULL AND r.read_time>=COALESCE(n.update_time,n.create_time) THEN TRUE ELSE FALSE END is_read FROM sys_notice n LEFT JOIN sys_notice_read r ON r.tenant_id=n.tenant_id AND r.user_id=? AND r.notice_id=n.id WHERE n.tenant_id=? AND n.status='0' ORDER BY COALESCE(n.update_time,n.create_time) DESC,n.id DESC LIMIT ?", Arrays.<Object>asList(userId, tenantId, safeLimit));
    }

    public long unreadNoticeCount(long tenantId, long userId) {
        return db.count("SELECT COUNT(*) FROM sys_notice n LEFT JOIN sys_notice_read r ON r.tenant_id=n.tenant_id AND r.user_id=? AND r.notice_id=n.id WHERE n.tenant_id=? AND n.status='0' AND (r.read_time IS NULL OR r.read_time<COALESCE(n.update_time,n.create_time))", Arrays.<Object>asList(userId, tenantId));
    }

    public boolean markNoticeRead(long tenantId, long userId, long noticeId) {
        if (db.count("SELECT COUNT(*) FROM sys_notice WHERE tenant_id=? AND id=? AND status='0'", Arrays.<Object>asList(tenantId, noticeId)) == 0)
            return false;
        db.insert("INSERT INTO sys_notice_read(tenant_id,user_id,notice_id,read_time) VALUES(?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE read_time=CURRENT_TIMESTAMP", Arrays.<Object>asList(tenantId, userId, noticeId));
        return true;
    }

    public int markAllNoticesRead(long tenantId, long userId) {
        return (int) db.insert("INSERT INTO sys_notice_read(tenant_id,user_id,notice_id,read_time) SELECT tenant_id,?,id,CURRENT_TIMESTAMP FROM sys_notice WHERE tenant_id=? AND status='0' ON DUPLICATE KEY UPDATE read_time=CURRENT_TIMESTAMP", Arrays.<Object>asList(userId, tenantId));
    }

    public List<Map<String, Object>> list(String r, long t, int o, int l) {
        return listFiltered(r, t, o, l, Collections.<String, String>emptyMap());
    }

    public List<Map<String, Object>> listFiltered(String r, long t, int o, int l, Map<String, String> f) {
        Spec s = spec(r);
        List<Object> a = new ArrayList<Object>();
        StringBuilder q = "tenant".equals(r)
                ? new StringBuilder("SELECT sys_tenant.*,tenant_lifecycle.lifecycle_status,tenant_lifecycle.stage lifecycle_stage,tenant_lifecycle.error_message lifecycle_error,tenant_lifecycle.retry_count lifecycle_retry_count,tenant_plan.plan_name,tenant_plan.version plan_version,tenant_subscription.status subscription_status,tenant_subscription.end_time subscription_end_time FROM sys_tenant LEFT JOIN sys_tenant_lifecycle tenant_lifecycle ON tenant_lifecycle.tenant_id=sys_tenant.id LEFT JOIN sys_tenant_subscription tenant_subscription ON tenant_subscription.tenant_id=sys_tenant.id AND tenant_subscription.status='ACTIVE' LEFT JOIN sys_plan tenant_plan ON tenant_plan.id=tenant_subscription.plan_id WHERE 1=1")
                : new StringBuilder("SELECT * FROM ").append(s.table).append(" WHERE 1=1");
        if (s.tenant) {
            q.append(" AND tenant_id=?");
            a.add(t);
        }
        appendVisibleResourcePredicate(r, q);
        filters(q, a, r, f);
        q.append(" ORDER BY ").append(order(r)).append(" LIMIT ? OFFSET ?");
        a.add(l);
        a.add(o);
        return db.query(q.toString(), a);
    }

    public long countFiltered(String r, long t, Map<String, String> f) {
        Spec s = spec(r);
        List<Object> a = new ArrayList<Object>();
        StringBuilder q = new StringBuilder("SELECT COUNT(*) FROM ").append(s.table).append(" WHERE 1=1");
        if (s.tenant) {
            q.append(" AND tenant_id=?");
            a.add(t);
        }
        appendVisibleResourcePredicate(r, q);
        filters(q, a, r, f);
        return db.count(q.toString(), a);
    }

    public void ensureCaptchaConfig() {
        db.update("INSERT INTO sys_config(tenant_id,config_name,config_key,config_value,config_type,remark,create_by,create_time) " +
                        "SELECT t.id,'登录验证码开关','sys.account.captchaEnabled','false','Y','是否启用登录验证码','system',CURRENT_TIMESTAMP " +
                        "FROM sys_tenant t WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.tenant_id=t.id AND c.config_key='sys.account.captchaEnabled')",
                Collections.<Object>emptyList());
    }

    public String configValue(long tenantId, String configKey, String defaultValue) {
        List<Map<String, Object>> rows = db.query(
                "SELECT config_value FROM sys_config WHERE tenant_id=? AND config_key=? ORDER BY id DESC LIMIT 1",
                Arrays.<Object>asList(tenantId, configKey));
        if (rows.isEmpty() || rows.get(0).get("configValue") == null) return defaultValue;
        String value = String.valueOf(rows.get(0).get("configValue")).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    public List<Map<String, Object>> listUsers(long t, int o, int l, DataScopeResult s) {
        return listUsers(t, o, l, s, Collections.<String, String>emptyMap());
    }

    public List<Map<String, Object>> listUsers(long t, int o, int l, DataScopeResult s, Map<String, String> f) {
        StringBuilder q = new StringBuilder("SELECT u.* FROM sys_user u WHERE u.tenant_id=?");
        List<Object> a = new ArrayList<Object>();
        a.add(t);
        scope(q, a, s, "u.dept_id", "u.id");
        userFilters(q, a, f.get("username") != null ? f.get("username") : f.get("keyword"), f.get("phone"), lng(f.get("deptId")), f.get("status"));
        like(q, a, "u.nickname", f.get("nickname"));
        q.append(" ORDER BY u.id LIMIT ? OFFSET ?");
        a.add(l);
        a.add(o);
        return db.query(q.toString(), a);
    }

    public long countUsers(long t, DataScopeResult s) {
        return countUsers(t, s, Collections.<String, String>emptyMap());
    }

    public long countUsers(long t, DataScopeResult s, Map<String, String> f) {
        StringBuilder q = new StringBuilder("SELECT COUNT(*) FROM sys_user u WHERE u.tenant_id=?");
        List<Object> a = new ArrayList<Object>();
        a.add(t);
        scope(q, a, s, "u.dept_id", "u.id");
        userFilters(q, a, f.get("username") != null ? f.get("username") : f.get("keyword"), f.get("phone"), lng(f.get("deptId")), f.get("status"));
        like(q, a, "u.nickname", f.get("nickname"));
        return db.count(q.toString(), a);
    }

    public List<Map<String, Object>> listRoleUsers(long t, long role, boolean assigned, String keyword, String phone, Long dept, String status, int o, int l) {
        StringBuilder q = new StringBuilder("SELECT u.* FROM sys_user u WHERE u.tenant_id=? AND ").append(assigned ? "EXISTS" : "NOT EXISTS").append(" (SELECT 1 FROM sys_user_role ur WHERE ur.tenant_id=u.tenant_id AND ur.user_id=u.id AND ur.role_id=?)");
        List<Object> a = new ArrayList<Object>(Arrays.<Object>asList(t, role));
        userFilters(q, a, keyword, phone, dept, status);
        q.append(" ORDER BY u.id LIMIT ? OFFSET ?");
        a.add(l);
        a.add(o);
        return db.query(q.toString(), a);
    }

    public long countRoleUsers(long t, long role, boolean assigned, String keyword, String phone, Long dept, String status) {
        StringBuilder q = new StringBuilder("SELECT COUNT(*) FROM sys_user u WHERE u.tenant_id=? AND ").append(assigned ? "EXISTS" : "NOT EXISTS").append(" (SELECT 1 FROM sys_user_role ur WHERE ur.tenant_id=u.tenant_id AND ur.user_id=u.id AND ur.role_id=?)");
        List<Object> a = new ArrayList<Object>(Arrays.<Object>asList(t, role));
        userFilters(q, a, keyword, phone, dept, status);
        return db.count(q.toString(), a);
    }

    public List<Map<String, Object>> listOperationLogs(long t, int o, int l, DataScopeResult s, String u) {
        return listLogs("oper", t, o, l, s, u, Collections.<String, String>emptyMap());
    }

    public List<Map<String, Object>> listLoginLogs(long t, int o, int l, DataScopeResult s, String u) {
        return listLogs("login", t, o, l, s, u, Collections.<String, String>emptyMap());
    }

    public List<Map<String, Object>> listLogs(String k, long t, int o, int l, DataScopeResult s, String u, Map<String, String> f) {
        StringBuilder q = new StringBuilder("SELECT l.* FROM ").append(logTable(k)).append(" l WHERE l.tenant_id=?");
        List<Object> a = new ArrayList<Object>();
        a.add(t);
        logScope(q, a, s, u, "oper".equals(k) ? "oper_name" : "username");
        logFilters(q, a, k, f);
        q.append(" ORDER BY ").append("oper".equals(k) ? "l.oper_time" : "login".equals(k) ? "l.login_time" : "l.error_time").append(" DESC,l.id DESC LIMIT ? OFFSET ?");
        a.add(l);
        a.add(o);
        return db.query(q.toString(), a);
    }

    public long countLogs(String k, long t, DataScopeResult s, String u, Map<String, String> f) {
        StringBuilder q = new StringBuilder("SELECT COUNT(*) FROM ").append(logTable(k)).append(" l WHERE l.tenant_id=?");
        List<Object> a = new ArrayList<Object>();
        a.add(t);
        logScope(q, a, s, u, "oper".equals(k) ? "oper_name" : "username");
        logFilters(q, a, k, f);
        return db.count(q.toString(), a);
    }

    public Map<String, Object> getLog(String k, long t, long id) {
        return db.one("SELECT * FROM " + logTable(k) + " WHERE tenant_id=? AND id=?", Arrays.<Object>asList(t, id));
    }

    public Map<String, Object> getLogScoped(String k, long t, long id, DataScopeResult s, String u) {
        StringBuilder q = new StringBuilder("SELECT l.* FROM ").append(logTable(k)).append(" l WHERE l.tenant_id=? AND l.id=?");
        List<Object> a = new ArrayList<Object>(Arrays.<Object>asList(t, id));
        logScope(q, a, s, u, "oper".equals(k) ? "oper_name" : "username");
        return db.one(q.toString(), a);
    }

    public int deleteLogs(String k, long t, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Object> a = new ArrayList<Object>();
        a.add(t);
        a.addAll(ids);
        return db.update("DELETE FROM " + logTable(k) + " WHERE tenant_id=? AND id IN (" + marks(ids.size()) + ")", a);
    }

    public int clearLogs(String k, long t) {
        return db.update("DELETE FROM " + logTable(k) + " WHERE tenant_id=?", Collections.<Object>singletonList(t));
    }

    public void audit(long t, String u, String dept, String title, int type, String method, String request, String url, String ip, String params, boolean ok, String error, long cost) {
        audit(t, u, dept, title, type, method, request, url, ip, params,
                ok ? "{\"code\":200,\"msg\":\"操作成功\"}" : "",
                ok, error, cost);
    }

    public void audit(long t, String u, String dept, String title, int type, String method, String request, String url, String ip, String params, String result, boolean ok, String error, long cost) {
        try {
            db.insert("INSERT INTO sys_oper_log(tenant_id,title,business_type,method,request_method,operator_type,oper_name,dept_name,oper_url,oper_ip,oper_location,oper_param,json_result,status,error_msg,cost_time,oper_time,create_by,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)", Arrays.<Object>asList(t, cut(title, 50), type, cut(method, 200), request, 1, u, cut(dept, 50), cut(url, 255), cut(ip, 50), "未知", cut(params, 2000), cut(result, 2000), ok ? "0" : "1", cut(error, 2000), cost, u));
        } catch (RuntimeException e) {
            System.err.println("[permission-audit] " + e.getMessage());
        }
    }

    public void errorAudit(long t, Long uid, String u, String feature, String method, String uri, String ip, int status, Throwable e) {
        java.io.StringWriter w = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(w));
        error(t, uid, u, feature, UUID.randomUUID().toString().replace("-", ""), method, uri, ip, status, e.getClass().getName(), e.getMessage(), w.toString());
    }

    public void errorAudit(ErrorAuditEvent e) {
        SecurityPrincipal p = e.getPrincipal();
        error(p == null ? 1 : p.getTenantId(), p == null ? null : p.getUserId(), p == null ? "system" : p.getUsername(), e.getPluginName() + ":" + e.getFeature(), e.getRequestId(), e.getRequestMethod(), e.getRequestUri(), e.getClientIp(), e.getHttpStatus(), e.getExceptionType(), e.getErrorMessage(), e.getStackTrace());
    }

    private void error(long t, Long uid, String u, String f, String req, String m, String uri, String ip, int status, String type, String msg, String stack) {
        try {
            db.insert("INSERT INTO sys_error_log(tenant_id,user_id,username,feature,request_id,request_method,request_uri,request_ip,http_status,error_code,exception_type,error_message,stack_trace,error_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", Arrays.<Object>asList(t, uid, u, cut(f, 200), cut(req, 64), m, cut(uri, 1000), cut(ip, 64), status, "PLUGIN_INTERNAL_ERROR", cut(type, 255), cut(msg, 2000), cut(stack, 16000)));
        } catch (RuntimeException e) {
            System.err.println("[permission-error-audit] " + e.getMessage());
        }
    }

    public boolean updateProfile(long t, long id, Map<String, Object> b, String actor) {
        return db.update("UPDATE sys_user SET nickname=?,phone=?,email=?,remark=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", Arrays.<Object>asList(String.valueOf(b.get("nickname")), b.get("phone"), b.get("email"), b.get("remark"), actor, t, id)) == 1;
    }

    public String passwordHash(long t, long id) {
        Map<String, Object> r = db.one("SELECT password password_hash FROM sys_user WHERE tenant_id=? AND id=?", Arrays.<Object>asList(t, id));
        return r == null ? null : String.valueOf(r.get("passwordHash"));
    }

    public boolean updateOwnPassword(long t, long id, String hash, String actor) {
        return db.update("UPDATE sys_user SET password=?,password_change_required='0',temp_password_expire_time=NULL,update_by=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", Arrays.<Object>asList(hash, actor, t, id)) == 1;
    }

    public Map<String, Long> dashboard(long t) {
        Map<String, Long> o = new LinkedHashMap<String, Long>();
        for (String[] x : new String[][]{{"userCount", "sys_user"}, {"roleCount", "sys_role"}, {"menuCount", "sys_menu"}, {"deptCount", "sys_dept"}, {"operlogCount", "sys_oper_log"}, {"logininforCount", "sys_logininfor"}})
            o.put(x[0], db.count("SELECT COUNT(*) FROM " + x[1] + ("sys_menu".equals(x[1]) ? "" : " WHERE tenant_id=?"), "sys_menu".equals(x[1]) ? Collections.<Object>emptyList() : Collections.<Object>singletonList(t)));
        o.put("onlineCount", 0L);
        return o;
    }

    public List<Map<String, Object>> extValues(long t, String type, long id) {
        return db.query("SELECT f.id field_id,f.field_key,f.field_label,f.field_type,f.dict_type,f.sort,COALESCE(v.field_value,'') field_value FROM sys_ext_field f LEFT JOIN sys_ext_value v ON v.tenant_id=f.tenant_id AND v.entity_type=f.entity_type AND v.entity_id=? AND v.field_key=f.field_key WHERE f.tenant_id=? AND f.entity_type=? AND f.status='0' ORDER BY f.sort,f.id", Arrays.<Object>asList(id, t, type));
    }

    public List<Map<String, Object>> enabledExtFields(long t, String type) {
        return db.query("SELECT * FROM sys_ext_field WHERE tenant_id=? AND entity_type=? AND status='0' ORDER BY sort,id", Arrays.<Object>asList(t, type));
    }

    public void replaceExtValues(long t, String type, long id, Map<String, Object> values, String actor) {
        db.transaction(m -> {
            m.update("DELETE FROM sys_ext_value WHERE tenant_id=? AND entity_type=? AND entity_id=?", Arrays.<Object>asList(t, type, id));
            if (values != null) for (Map.Entry<String, Object> e : values.entrySet())
                if (e.getValue() != null && !String.valueOf(e.getValue()).isEmpty())
                    m.update("INSERT INTO sys_ext_value(tenant_id,entity_type,entity_id,field_key,field_value,create_by,create_time) SELECT ?,?,?,?,?,?,CURRENT_TIMESTAMP WHERE EXISTS(SELECT 1 FROM sys_ext_field WHERE tenant_id=? AND entity_type=? AND field_key=? AND status='0')", Arrays.<Object>asList(t, type, id, e.getKey(), String.valueOf(e.getValue()), actor, t, type, e.getKey()));
            return null;
        });
    }

    public int deleteExtValues(long t, String type, long id, String key) {
        List<Object> a = new ArrayList<Object>(Arrays.<Object>asList(t, type, id));
        if (key != null) a.add(key);
        return db.update("DELETE FROM sys_ext_value WHERE tenant_id=? AND entity_type=? AND entity_id=?" + (key == null ? "" : " AND field_key=?"), a);
    }

    public List<Map<String, Object>> activeTenants() {
        return db.query("SELECT id,tenant_name,tenant_code,(CASE WHEN id=1 THEN TRUE ELSE FALSE END) is_default FROM sys_tenant WHERE status='0' ORDER BY CASE WHEN id=1 THEN 0 ELSE 1 END,id", Collections.<Object>emptyList());
    }

    public Map<String, Object> defaultTenant() {
        return db.one("SELECT id,tenant_name,tenant_code,TRUE is_default FROM sys_tenant WHERE id=1 AND status='0' LIMIT 1", Collections.<Object>emptyList());
    }

    public long count(String r, long t) {
        Spec s = spec(r);
        StringBuilder q = new StringBuilder("SELECT COUNT(*) FROM ").append(s.table)
                .append(s.tenant ? " WHERE tenant_id=?" : " WHERE 1=1");
        appendVisibleResourcePredicate(r, q);
        return db.count(q.toString(), s.tenant ? Collections.<Object>singletonList(t) : Collections.<Object>emptyList());
    }

    public Map<String, Object> get(String r, long t, long id) {
        Spec s = spec(r);
        StringBuilder q = "tenant".equals(r)
                ? new StringBuilder("SELECT sys_tenant.*,tenant_lifecycle.lifecycle_status,tenant_lifecycle.stage lifecycle_stage,tenant_lifecycle.error_message lifecycle_error,tenant_lifecycle.retry_count lifecycle_retry_count FROM sys_tenant LEFT JOIN sys_tenant_lifecycle tenant_lifecycle ON tenant_lifecycle.tenant_id=sys_tenant.id WHERE sys_tenant.id=?")
                : new StringBuilder("SELECT * FROM ").append(s.table).append(" WHERE id=?").append(s.tenant ? " AND tenant_id=?" : "");
        appendVisibleResourcePredicate(r, q);
        return db.one(q.toString(), s.tenant ? Arrays.<Object>asList(id, t) : Collections.<Object>singletonList(id));
    }

    /**
     * Tenant rows are intentionally retained after cleanup as lifecycle/audit
     * metadata. Normal CRUD endpoints must nevertheless treat DELETED tenants as
     * absent; lifecycle/progress endpoints remain the dedicated audit surface.
     */
    private static void appendVisibleResourcePredicate(String resource, StringBuilder sql) {
        if ("tenant".equals(resource)) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM sys_tenant_lifecycle deleted_lifecycle")
                    .append(" WHERE deleted_lifecycle.tenant_id=sys_tenant.id")
                    .append(" AND deleted_lifecycle.lifecycle_status='DELETED')");
        }
    }

    public long insert(String r, long t, Map<String, Object> b, String actor) {
        Spec s = spec(r);
        List<String> f = present(s, b);
        List<Object> a = new ArrayList<Object>();
        if (s.tenant) {
            f.add(0, "tenant_id");
            a.add(t);
        }
        for (String x : present(s, b)) a.add(value(b, x));
        f.add("create_by");
        a.add(actor);
        f.add("create_time");
        return db.insert("INSERT INTO " + s.table + "(" + String.join(",", f) + ") VALUES(" + marks(f.size() - 1) + ",CURRENT_TIMESTAMP)", a);
    }

    public boolean update(String r, long t, long id, Map<String, Object> b, String actor) {
        Spec s = spec(r);
        List<String> f = present(s, b);
        f.remove("tenant_id");
        if (f.isEmpty()) return false;
        List<String> sets = new ArrayList<String>();
        List<Object> a = new ArrayList<Object>();
        for (String x : f) {
            sets.add(x + "=?");
            a.add(value(b, x));
        }
        sets.add("update_by=?");
        a.add(actor);
        sets.add("update_time=CURRENT_TIMESTAMP");
        a.add(id);
        if (s.tenant) a.add(t);
        return db.update("UPDATE " + s.table + " SET " + String.join(",", sets) + " WHERE id=?" + (s.tenant ? " AND tenant_id=?" : ""), a) == 1;
    }

    public int delete(String r, long t, Collection<Long> ids) {
        Spec s = spec(r);
        if (ids == null || ids.isEmpty()) return 0;
        List<Object> a = new ArrayList<Object>(ids);
        if (s.tenant) a.add(t);
        String sql = "DELETE FROM " + s.table + " WHERE id IN (" + marks(ids.size()) + ")" + (s.tenant ? " AND tenant_id=?" : "");
        if (!"notice".equals(r)) return db.update(sql, a);
        return db.transaction(m -> {
            List<Object> readArgs = new ArrayList<Object>();
            readArgs.add(t);
            readArgs.addAll(ids);
            m.update("DELETE FROM sys_notice_read WHERE tenant_id=? AND notice_id IN (" + marks(ids.size()) + ")", readArgs);
            return m.update(sql, a);
        });
    }

    private static Spec spec(String r) {
        Spec s = SPECS.get(r);
        if (s == null) throw new IllegalArgumentException("Unsupported resource");
        return s;
    }

    private static List<String> present(Spec s, Map<String, Object> b) {
        List<String> o = new ArrayList<String>();
        for (String f : s.fields) if (b.containsKey(f) || b.containsKey(camel(f))) o.add(f);
        return o;
    }

    private static Object value(Map<String, Object> body, String field) {
        Object raw = body.containsKey(field)
                ? body.get(field)
                : body.get(camel(field));

        if (field.endsWith("_time")) {
            return dateTimeValue(raw, field);
        }

        return raw;
    }

    private static Object dateTimeValue(Object raw, String field) {
        if (raw == null || raw instanceof LocalDateTime) {
            return raw;
        }

        if (raw instanceof Number) {
            long timestamp = ((Number) raw).longValue();
            return fromTimestamp(timestamp);
        }

        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            // 10 位 Unix 秒时间戳
            if (text.matches("^-?\\d{10}$")) {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(Long.parseLong(text)),
                        ZoneId.systemDefault()
                );
            }

            // 13 位 Unix 毫秒时间戳
            if (text.matches("^-?\\d{13}$")) {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Long.parseLong(text)),
                        ZoneId.systemDefault()
                );
            }

            // 同时兼容：
            // 2039-09-01T00:00:00
            // 2039-09-01 00:00:00
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException | NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid date-time value for " + field + ": " + text,
                    ex
            );
        }
    }

    private static LocalDateTime fromTimestamp(long timestamp) {
        // 小于 1000 亿按秒处理，否则按毫秒处理
        long milliseconds = Math.abs(timestamp) < 100_000_000_000L
                ? timestamp * 1000L
                : timestamp;

        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(milliseconds),
                ZoneId.systemDefault()
        );
    }

    private static void filters(StringBuilder q, List<Object> a, String r, Map<String, String> f) {
        if (f == null) return;
        Map<String, String> m = new LinkedHashMap<String, String>();
        if ("user".equals(r)) {
            m.put("username", "username");
            m.put("nickname", "nickname");
            m.put("phone", "phone");
            m.put("status", "status");
            m.put("deptId", "dept_id");
        } else if ("role".equals(r)) {
            m.put("roleName", "role_name");
            m.put("roleKey", "role_key");
            m.put("status", "status");
        } else if ("tenant".equals(r)) {
            m.put("tenantName", "tenant_name");
            m.put("tenantCode", "tenant_code");
            m.put("status", "status");
        } else if ("menu".equals(r)) {
            m.put("menuName", "menu_name");
            m.put("status", "status");
            m.put("moduleId", "module_id");
        } else if ("dict".equals(r)) {
            m.put("dictName", "dict_name");
            m.put("dictType", "dict_type");
            m.put("status", "status");
        } else if ("dictData".equals(r)) {
            m.put("dictType", "dict_type");
            m.put("dictLabel", "dict_label");
            m.put("status", "status");
        } else if ("config".equals(r)) {
            m.put("configName", "config_name");
            m.put("configKey", "config_key");
            m.put("configType", "config_type");
        }
        for (Map.Entry<String, String> e : m.entrySet()) {
            String v = f.get(e.getKey());
            if (v == null || v.trim().isEmpty()) continue;
            boolean exact = e.getKey().equals("status")
                    || e.getKey().equals("dictType")
                    || e.getKey().equals("configKey")
                    || e.getKey().equals("configType")
                    || e.getKey().endsWith("Id");
            q.append(" AND ").append(e.getValue()).append(exact ? "=?" : " LIKE ?");
            a.add(exact ? v.trim() : "%" + v.trim() + "%");
        }
    }

    private static void userFilters(StringBuilder q, List<Object> a, String k, String phone, Long dept, String status) {
        if (k != null && !k.trim().isEmpty()) {
            q.append(" AND (LOWER(u.username) LIKE ? OR LOWER(u.nickname) LIKE ?)");
            String v = "%" + k.trim().toLowerCase(Locale.ROOT) + "%";
            a.add(v);
            a.add(v);
        }
        like(q, a, "u.phone", phone);
        if (dept != null) {
            q.append(" AND u.dept_id=?");
            a.add(dept);
        }
        if (status != null && !status.trim().isEmpty()) {
            q.append(" AND u.status=?");
            a.add(status);
        }
    }

    private static void scope(StringBuilder q, List<Object> a, DataScopeResult s, String dept, String owner) {
        if (s == null || s.getScope() == DataScopeResult.Scope.DENY) {
            q.append(" AND 1=0");
            return;
        }
        if (s.getScope() == DataScopeResult.Scope.ALL) return;
        if (s.getScope() == DataScopeResult.Scope.SELF) {
            q.append(" AND ").append(owner).append("=?");
            a.add(s.getOwnerUserId());
            return;
        }
        if (s.getDepartmentIds().isEmpty()) {
            q.append(" AND 1=0");
            return;
        }
        q.append(" AND ").append(dept).append(" IN (").append(marks(s.getDepartmentIds().size())).append(")");
        a.addAll(s.getDepartmentIds());
    }

    private static void logScope(StringBuilder q, List<Object> a, DataScopeResult s, String u, String col) {
        if (s == null || s.getScope() == DataScopeResult.Scope.DENY) {
            q.append(" AND 1=0");
            return;
        }
        if (s.getScope() == DataScopeResult.Scope.ALL) return;
        if (s.getScope() == DataScopeResult.Scope.SELF) {
            q.append(" AND l.").append(col).append("=?");
            a.add(u);
            return;
        }
        if (s.getDepartmentIds().isEmpty()) {
            q.append(" AND 1=0");
            return;
        }
        q.append(" AND EXISTS(SELECT 1 FROM sys_user su WHERE su.tenant_id=l.tenant_id AND su.username=l.").append(col).append(" AND su.dept_id IN (").append(marks(s.getDepartmentIds().size())).append("))");
        a.addAll(s.getDepartmentIds());
    }

    private static void logFilters(StringBuilder q, List<Object> a, String k, Map<String, String> f) {
        if (f == null) return;
        String[][] cols = "oper".equals(k) ? new String[][]{{"title", "title"}, {"operName", "oper_name"}, {"status", "status"}} : "login".equals(k) ? new String[][]{{"username", "username"}, {"ipaddr", "ipaddr"}, {"status", "status"}} : new String[][]{{"keyword", "error_message"}, {"username", "username"}, {"feature", "feature"}, {"httpStatus", "http_status"}};
        for (String[] c : cols) {
            String v = f.get(c[0]);
            if (v == null || v.trim().isEmpty()) continue;
            boolean exact = "status".equals(c[0]) || "httpStatus".equals(c[0]);
            q.append(" AND l.").append(c[1]).append(exact ? "=?" : " LIKE ?");
            a.add(exact ? v : "%" + v + "%");
        }
    }

    private static void like(StringBuilder q, List<Object> a, String c, String v) {
        if (v != null && !v.trim().isEmpty()) {
            q.append(" AND ").append(c).append(" LIKE ?");
            a.add("%" + v.trim() + "%");
        }
    }

    private static String logTable(String k) {
        if ("oper".equals(k)) return "sys_oper_log";
        if ("login".equals(k)) return "sys_logininfor";
        if ("error".equals(k)) return "sys_error_log";
        throw new IllegalArgumentException("Unsupported log kind");
    }

    private static String order(String r) {
        if ("tenant".equals(r)) return "sys_tenant.id";
        if ("role".equals(r)) return "sort,id";
        if ("menu".equals(r) || "dept".equals(r) || "module".equals(r)) return "order_num,id";
        if ("dictData".equals(r)) return "dict_sort,id";
        return "id";
    }

    private static String marks(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static Long lng(String s) {
        try {
            return s == null || s.trim().isEmpty() ? null : Long.valueOf(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid numeric filter");
        }
    }

    private static String camel(String v) {
        StringBuilder b = new StringBuilder();
        boolean u = false;
        for (char c : v.toCharArray())
            if (c == '_') u = true;
            else {
                b.append(u ? Character.toUpperCase(c) : c);
                u = false;
            }
        return b.toString();
    }

    private static final class Spec {
        final String table;
        final boolean tenant;
        final List<String> fields;

        Spec(String t, boolean b, String f) {
            table = t;
            tenant = b;
            fields = new ArrayList<String>(Arrays.asList(f.split(",")));
        }
    }
}
