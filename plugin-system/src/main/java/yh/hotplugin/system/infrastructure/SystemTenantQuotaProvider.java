package yh.hotplugin.system.infrastructure;

import org.noear.redisx.RedisClient;
import yh.hotplugin.security.RequestPerformance;
import yh.hotplugin.security.TenantLimitException;
import yh.hotplugin.security.api.*;
import yh.hotplugin.system.infrastructure.mybatis.MybatisExecutor;
import java.time.*;
import java.util.*;

public final class SystemTenantQuotaProvider implements TenantQuotaProvider {
    private static final String LUA="local m=redis.call('INCR',KEYS[1]); if m==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; local ml=tonumber(ARGV[3]); if ml>=0 and m>ml then return {m,-1} end; local d=redis.call('INCR',KEYS[2]); if d==1 then redis.call('EXPIRE',KEYS[2],ARGV[2]) end; return {m,d}";
    static final String LIMITS_SQL="SELECT "+
            "COALESCE(MAX(CASE WHEN pq.quota_key='api.minute' THEN COALESCE(o.quota_limit,pq.quota_limit) END),600) minute_default,"+
            "COALESCE(MAX(CASE WHEN pq.quota_key='api.day' THEN COALESCE(o.quota_limit,pq.quota_limit) END),100000) day_default,"+
            "SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN (? LIKE REPLACE(rp.route_pattern,'*','%')) AND rp.minute_limit IS NOT NULL THEN CONCAT(rp.id,':',rp.minute_limit) END ORDER BY LENGTH(rp.route_pattern) DESC,rp.id DESC SEPARATOR ','),',',1) minute_policy,"+
            "SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN (? LIKE REPLACE(rp.route_pattern,'*','%')) AND rp.day_limit IS NOT NULL THEN CONCAT(rp.id,':',rp.day_limit) END ORDER BY LENGTH(rp.route_pattern) DESC,rp.id DESC SEPARATOR ','),',',1) day_policy,"+
            "CASE WHEN s.end_time IS NOT NULL AND s.end_time<CURRENT_TIMESTAMP THEN 1 ELSE 0 END expired "+
            "FROM (SELECT ? tenant_id) tenant "+
            "LEFT JOIN sys_tenant_subscription s ON s.id=(SELECT s2.id FROM sys_tenant_subscription s2 WHERE s2.tenant_id=tenant.tenant_id AND s2.status='ACTIVE' ORDER BY s2.id DESC LIMIT 1) "+
            "LEFT JOIN sys_plan_quota pq ON pq.plan_id=s.plan_id AND pq.quota_key IN ('api.minute','api.day') "+
            "LEFT JOIN sys_tenant_quota_override o ON o.tenant_id=tenant.tenant_id AND o.quota_key=pq.quota_key "+
            "LEFT JOIN sys_tenant_rate_policy rp ON rp.tenant_id=tenant.tenant_id AND rp.status='0' "+
            "GROUP BY s.id,s.end_time";
    private final MybatisExecutor db; private final RedisClient redis;
    public SystemTenantQuotaProvider(JdbcAuthorizationRepository repository,RedisClient redis){this.db=repository.executor();this.redis=redis;}
    public void checkRequest(SecurityPrincipal p,String method,String path){
        if(path.startsWith("/auth/")||path.equals("/logout")||path.equals("/health"))return;
        long stage=RequestPerformance.begin();
        Limits limits=limits(p.getTenantId(),path);
        RequestPerformance.recordCurrent("quotaConfig",stage);
        long now=System.currentTimeMillis(),minuteBucket=now/60000,dayBucket=LocalDate.now(ZoneOffset.UTC).toEpochDay();
        stage=RequestPerformance.begin();
        List<Long>current=incrementBoth(
                "plugin-system:quota:"+p.getTenantId()+":"+limits.minute.key+":minute:"+minuteBucket,
                "plugin-system:quota:"+p.getTenantId()+":"+limits.day.key+":day:"+dayBucket,limits.minute.limit);
        RequestPerformance.recordCurrent("quotaRedis",stage);
        long minuteCurrent=current.get(0),dayCurrent=current.get(1);
        if(limits.minute.limit>=0&&minuteCurrent>limits.minute.limit){event(p.getTenantId(),"api.minute",limits.minute.limit,minuteCurrent,path);throw new TenantLimitException(429,"TENANT_RATE_LIMIT_EXCEEDED","api.minute",limits.minute.limit,minuteCurrent);}
        stage=RequestPerformance.begin();
        usage(p.getTenantId(),"api.day",String.valueOf(dayBucket),dayCurrent);
        RequestPerformance.recordCurrent("quotaUsage",stage);
        if(limits.day.limit>=0&&dayCurrent>limits.day.limit){event(p.getTenantId(),"api.day",limits.day.limit,dayCurrent,path);throw new TenantLimitException(429,"TENANT_RATE_LIMIT_EXCEEDED","api.day",limits.day.limit,dayCurrent);}
        if(!("GET".equalsIgnoreCase(method)||"HEAD".equalsIgnoreCase(method))&&limits.expired)throw new TenantLimitException(409,"TENANT_SUBSCRIPTION_EXPIRED");
    }
    public void checkResource(SecurityPrincipal p,String key,long current,long add){long max=limit(p.getTenantId(),key,Long.MAX_VALUE);if(max>=0&&current+add>max){event(p.getTenantId(),key,max,current+add,null);throw new TenantLimitException(409,"TENANT_QUOTA_EXCEEDED",key,max,current+add);}}
    public Map<String,Object> currentPlan(SecurityPrincipal p){
        Map<String,Object> plan=db.one("SELECT p.id,p.plan_name,p.plan_code,p.version,p.display_price,p.currency,p.billing_cycle,s.start_time,s.end_time,s.status FROM sys_tenant_subscription s JOIN sys_plan p ON p.id=s.plan_id WHERE s.tenant_id=? AND s.status='ACTIVE' ORDER BY s.id DESC LIMIT 1",Collections.<Object>singletonList(p.getTenantId()));
        if(plan==null)plan=new LinkedHashMap<String,Object>();
        Map<String,Long> quotas=new LinkedHashMap<String,Long>();
        for(String k:Arrays.asList("users.max","roles.max","departments.max","notices.max","dicts.max","configs.max","api.minute","api.day","backups.max"))quotas.put(k,limit(p.getTenantId(),k,-1));
        plan.put("quotas",quotas);plan.put("features",db.query("SELECT f.feature_key,f.feature_name,f.enabled,f.description FROM sys_tenant_subscription s JOIN sys_plan_feature f ON f.plan_id=s.plan_id WHERE s.tenant_id=? AND s.status='ACTIVE' ORDER BY f.feature_key",Collections.<Object>singletonList(p.getTenantId())));return plan;
    }
    public boolean hasFeature(SecurityPrincipal p,String featureKey){return db.count("SELECT COUNT(*) FROM sys_tenant_subscription s JOIN sys_plan_feature f ON f.plan_id=s.plan_id AND f.feature_key=? AND f.enabled='Y' WHERE s.tenant_id=? AND s.status='ACTIVE'",Arrays.<Object>asList(featureKey,p.getTenantId()))>0;}
    @SuppressWarnings("unchecked")private List<Long> incrementBoth(String minuteKey,String dayKey,long minuteLimit){Object value=redis.jedis().eval(LUA,Arrays.asList(minuteKey,dayKey),Arrays.asList("120","172800",String.valueOf(minuteLimit)));if(!(value instanceof List)||((List<?>)value).size()!=2)throw new IllegalStateException("Redis quota script returned an invalid result");List<?>raw=(List<?>)value;return Arrays.asList(((Number)raw.get(0)).longValue(),((Number)raw.get(1)).longValue());}
    private long limit(long tenant,String key,long fallback){Map<String,Object> row=db.one("SELECT COALESCE(o.quota_limit,pq.quota_limit) quota_limit FROM sys_tenant_subscription s JOIN sys_plan_quota pq ON pq.plan_id=s.plan_id AND pq.quota_key=? LEFT JOIN sys_tenant_quota_override o ON o.tenant_id=s.tenant_id AND o.quota_key=pq.quota_key WHERE s.tenant_id=? AND s.status='ACTIVE' ORDER BY s.id DESC LIMIT 1",Arrays.<Object>asList(key,tenant));if(row==null||row.get("quotaLimit")==null)return fallback;return ((Number)row.get("quotaLimit")).longValue();}
    private Limits limits(long tenant,String path){
        Map<String,Object>row=db.one(LIMITS_SQL,Arrays.<Object>asList(path,path,tenant));
        long minuteDefault=number(row,"minuteDefault",600),dayDefault=number(row,"dayDefault",100000);
        return new Limits(policy(row==null?null:row.get("minutePolicy"),minuteDefault),policy(row==null?null:row.get("dayPolicy"),dayDefault),row!=null&&truth(row.get("expired")));
    }
    static RouteLimit policy(Object value,long fallback){if(value==null)return new RouteLimit("plan-default",fallback);String text=String.valueOf(value);int split=text.indexOf(':');if(split<=0)return new RouteLimit("plan-default",fallback);try{return new RouteLimit("policy-"+text.substring(0,split),Long.parseLong(text.substring(split+1)));}catch(NumberFormatException e){throw new IllegalStateException("Invalid tenant rate policy: "+text,e);}}
    private static long number(Map<String,Object>row,String key,long fallback){if(row==null||row.get(key)==null)return fallback;return ((Number)row.get(key)).longValue();}
    static boolean truth(Object value){return value instanceof Boolean?(Boolean)value:value instanceof Number&&((Number)value).longValue()!=0;}
    private void event(long tenant,String key,long limit,long current,String path){db.insert("INSERT INTO sys_tenant_limit_event(tenant_id,quota_key,limit_value,current_value,request_path) VALUES(?,?,?,?,?)",Arrays.<Object>asList(tenant,key,limit,current,path));}
    private void usage(long tenant,String key,String period,long value){db.update("INSERT INTO sys_tenant_usage(tenant_id,usage_key,usage_value,period_key,update_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE usage_value=VALUES(usage_value),update_time=CURRENT_TIMESTAMP",Arrays.<Object>asList(tenant,key,value,period));}
    static final class RouteLimit{final String key;final long limit;RouteLimit(String key,long limit){this.key=key;this.limit=limit;}}
    private static final class Limits{final RouteLimit minute,day;final boolean expired;Limits(RouteLimit minute,RouteLimit day,boolean expired){this.minute=minute;this.day=day;this.expired=expired;}}
}
