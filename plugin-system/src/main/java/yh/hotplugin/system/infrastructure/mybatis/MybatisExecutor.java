package yh.hotplugin.system.infrastructure.mybatis;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import net.sf.jsqlparser.expression.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.util.*;
import java.util.function.Function;
import yh.hotplugin.security.tenant.TenantContext;
import yh.hotplugin.system.infrastructure.ApiTimeFormatter;

/**
 * Solon plugin-owned MyBatis-Plus runtime.
 */
public final class MybatisExecutor implements AutoCloseable {
    private final DruidDataSource ds;
    private final SqlSessionFactory factory;
    private boolean closed;

    public MybatisExecutor(String driver, String url, String user, String password) {
        if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("datasource url is required");
        ds = new DruidDataSource();
        ds.setDriverClassName(driver);
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setInitialSize(1);
        ds.setMinIdle(1);
        ds.setMaxActive(10);
        ds.setTestWhileIdle(false);
        // Druid resolves this class lazily while closing pooled connections. A hot-plugin
        // JAR may already be moving to quarantine at that point, so resolve it while the
        // plugin classloader and JAR are guaranteed to be intact.
        preloadShutdownClasses();
        MybatisConfiguration c = new MybatisConfiguration();
        c.setMapUnderscoreToCamelCase(true);
        c.addMapper(DynamicSqlMapper.class);
        MybatisPlusInterceptor x = new MybatisPlusInterceptor();
        x.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() {
                return new LongValue(TenantContext.requiredTenantId());
            }

            public String getTenantIdColumn() {
                return "tenant_id";
            }

            public boolean ignoreTable(String t) {
                if (TenantContext.isPlatform()) return true;
                boolean global = "sys_menu".equalsIgnoreCase(t) || "sys_module".equalsIgnoreCase(t) || "sys_tenant".equalsIgnoreCase(t)
                        || "sys_tenant_lifecycle".equalsIgnoreCase(t)
                        || "sys_plan".equalsIgnoreCase(t) || "sys_plan_quota".equalsIgnoreCase(t)
                        || "sys_plan_feature".equalsIgnoreCase(t);
                if (global) return true;
                TenantContext.requiredTenantId();
                return false;
            }
        }));
        x.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        x.addInnerInterceptor(new CompatibleBlockAttackInnerInterceptor());
        x.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        c.addInterceptor(x);
        c.setEnvironment(new Environment("pluginSystem", new JdbcTransactionFactory(), ds));
        factory = new SqlSessionFactoryBuilder().build(c);
    }

    public List<Map<String, Object>> query(String sql, List<Object> a) {
        return session(true, m -> camel(m.select(sql, a), sql.contains("u.password")));
    }

    public Map<String, Object> one(String sql, List<Object> a) {
        List<Map<String, Object>> r = query(sql, a);
        return r.isEmpty() ? null : r.get(0);
    }

    public long count(String sql, List<Object> a) {
        Map<String, Object> r = one(sql, a);
        if (r == null || r.isEmpty()) return 0;
        Object v = r.values().iterator().next();
        return v == null ? 0 : ((Number) v).longValue();
    }

    public int update(String sql, List<Object> a) {
        return session(true, m -> m.update(sql, a));
    }

    public long insert(String sql, List<Object> a) {
        return session(true, m -> {
            Map<String, Object> h = new HashMap<String, Object>();
            m.insert(sql, a, h);
            Object id = h.get("id");
            return id == null ? 0 : ((Number) id).longValue();
        });
    }

    public <T> T transaction(Function<DynamicSqlMapper, T> w) {
        try (SqlSession s = factory.openSession(false)) {
            try {
                T r = w.apply(s.getMapper(DynamicSqlMapper.class));
                s.commit();
                return r;
            } catch (RuntimeException e) {
                s.rollback();
                throw e;
            }
        }
    }

    private <T> T session(boolean auto, Function<DynamicSqlMapper, T> w) {
        try (SqlSession s = factory.openSession(auto)) {
            return w.apply(s.getMapper(DynamicSqlMapper.class));
        }
    }

    private static List<Map<String, Object>> camel(List<Map<String, Object>> rows, boolean authentication) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> c = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> e : r.entrySet()) {
                String key = camel(e.getKey());
                if ("password".equals(key)) {
                    if (authentication) c.put("passwordHash", e.getValue());
                } else c.put(key, ApiTimeFormatter.normalize(key, e.getValue()));
            }
            out.add(c);
        }
        return out;
    }

    private static String camel(String s) {
        StringBuilder b = new StringBuilder();
        boolean u = false;
        for (char c : s.toCharArray())
            if (c == '_') u = true;
            else {
                b.append(u ? Character.toUpperCase(c) : Character.toLowerCase(c));
                u = false;
            }
        return b.toString();
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        ds.close();
    }

    private static void preloadShutdownClasses() {
        try {
            Class.forName("com.alibaba.druid.pool.PreparedStatementPool", true,
                    MybatisExecutor.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Druid shutdown classes are incomplete", e);
        }
    }
}
