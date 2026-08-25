package yh.hotplugin.system.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import yh.hotplugin.security.tenant.*;
import yh.hotplugin.system.infrastructure.mybatis.MybatisExecutor;
import java.io.*;
import java.util.*;

/** pluginSystem's idempotent contribution to the host-wide tenant lifecycle. */
public final class SystemTenantContributor implements TenantLifecycleContributor,TenantDataContributor,TenantQuotaContributor {
    private static final Set<String> TENANT_TABLES=Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList("sys_dept","sys_role","sys_user","sys_user_role","sys_role_menu","sys_role_dept","sys_dict_type","sys_dict_data","sys_config","sys_notice","sys_notice_read","sys_ext_field","sys_ext_value","sys_oper_log","sys_logininfor","sys_error_log","sys_tenant_subscription","sys_tenant_quota_override","sys_tenant_usage","sys_tenant_backup","sys_tenant_plan_change","sys_tenant_rate_policy","sys_tenant_limit_event")));
    private static final Set<String> GLOBAL_TABLES=Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList("sys_menu","sys_module","sys_tenant","sys_tenant_lifecycle","sys_plan","sys_plan_quota","sys_plan_feature")));
    private final JdbcManagementRepository management; private final MybatisExecutor db; private final ObjectMapper json=new ObjectMapper();
    public SystemTenantContributor(JdbcManagementRepository management,JdbcAuthorizationRepository repository){this.management=management;this.db=repository.executor();}
    public String pluginName(){return "pluginSystem";} public String dataVersion(){return "1.0.5";}
    public Set<String> tenantTables(){return TENANT_TABLES;} public Set<String> globalTables(){return GLOBAL_TABLES;}
    public void initialize(long tenantId){} public void disable(long tenantId){management.setTenantEnabled(tenantId,false);} public void restore(long tenantId){management.setTenantEnabled(tenantId,true);} public void cleanup(long tenantId){management.cleanupTenant(tenantId);} public boolean hasResidualData(long tenantId){return management.hasTenantResidualData(tenantId);}
    public Set<String> supportedQuotaKeys(){return new LinkedHashSet<String>(Arrays.asList("users.max","roles.max","departments.max","notices.max","dicts.max","configs.max"));}
    public Map<String,Long> currentUsage(long tenantId){Map<String,Long> out=new LinkedHashMap<String,Long>();out.put("users.max",count("sys_user",tenantId));out.put("roles.max",count("sys_role",tenantId));out.put("departments.max",count("sys_dept",tenantId));out.put("notices.max",count("sys_notice",tenantId));out.put("dicts.max",count("sys_dict_type",tenantId));out.put("configs.max",count("sys_config",tenantId));return out;}
    private long count(String table,long tenant){return db.count("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Collections.<Object>singletonList(tenant));}
    public void exportTenant(long tenantId,OutputStream output) throws Exception {Map<String,Object> bundle=new LinkedHashMap<String,Object>();bundle.put("plugin",pluginName());bundle.put("version",dataVersion());Map<String,Object> tables=new LinkedHashMap<String,Object>();for(String table:TENANT_TABLES)if(!"sys_tenant_backup".equals(table))tables.put(table,db.query("SELECT * FROM "+table+" WHERE tenant_id=?",Collections.<Object>singletonList(tenantId)));bundle.put("tables",tables);json.writeValue(output,bundle);}
    public void importTenant(long tenantId,InputStream input) throws Exception {
        final Map<String,Object> bundle=json.readValue(input,new TypeReference<Map<String,Object>>(){});
        if(!dataVersion().equals(String.valueOf(bundle.get("version"))))throw new IllegalArgumentException("TENANT_BACKUP_VERSION_MISMATCH");
        @SuppressWarnings("unchecked") final Map<String,List<Map<String,Object>>> tables=(Map<String,List<Map<String,Object>>>)bundle.get("tables");
        db.transaction(m->{
            List<String> reverse=new ArrayList<String>(TENANT_TABLES);Collections.reverse(reverse);
            for(String table:reverse)if(!"sys_tenant_backup".equals(table))m.update("DELETE FROM "+table+" WHERE tenant_id=?",Collections.<Object>singletonList(tenantId));
            for(String table:TENANT_TABLES){if("sys_tenant_backup".equals(table))continue;List<Map<String,Object>> rows=tables.get(table);if(rows==null)continue;for(Map<String,Object> row:rows){List<String> columns=new ArrayList<String>();List<Object> values=new ArrayList<Object>();for(Map.Entry<String,Object> entry:row.entrySet()){String column="passwordHash".equals(entry.getKey())?"password":snake(entry.getKey());if("tenant_id".equals(column)){columns.add(column);values.add(tenantId);}else if(!"password".equals(column)||entry.getValue()!=null){columns.add(column);values.add(value(column,entry.getValue()));}}if(!columns.contains("tenant_id")){columns.add(0,"tenant_id");values.add(0,tenantId);}m.insert("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+marks(columns.size())+")",values,new HashMap<String,Object>());}}
            return null;
        });
    }
    private static String snake(String value){StringBuilder out=new StringBuilder();for(char c:value.toCharArray()){if(Character.isUpperCase(c))out.append('_').append(Character.toLowerCase(c));else out.append(c);}return out.toString();}
    private static Object value(String column,Object value){if(value instanceof Number&&(column.endsWith("_time")||column.endsWith("_date")))return new java.sql.Timestamp(((Number)value).longValue());return value;}
    private static String marks(int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++){if(i>0)out.append(',');out.append('?');}return out.toString();}
}
