package yh.hotplugin.system.application;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpUtil;
import yh.hotplugin.security.api.*;
import yh.hotplugin.system.domain.model.AuthorizationSnapshot;
import yh.hotplugin.system.domain.repository.AuthorizationRepository;
import yh.hotplugin.system.security.GmPasswordEncoder;
import java.util.*;
import yh.hotplugin.security.tenant.TenantContext;
import yh.hotplugin.security.RequestPerformance;

/** PermissionProvider compatibility facade backed by Sa-Token and mandatory Redis. */
public final class SystemPermissionProvider implements PermissionProvider, AutoCloseable {
 private static final long LOCK_SECONDS=600; private static final String FAIL="plugin-system:login-failure:",CAPTCHA="plugin-system:captcha:";
 private final AuthorizationRepository repository; private final SaTokenDao dao; private final GmPasswordEncoder passwords=new GmPasswordEncoder(); private volatile boolean accepting=true;
 public SystemPermissionProvider(AuthorizationRepository repository,SaTokenDao dao){if(repository==null||dao==null)throw new IllegalArgumentException("repository and SaTokenDao are required");this.repository=repository;this.dao=dao;}
 public LoginResult login(LoginCommand c){if(!accepting)throw new IllegalStateException("权限服务正在停止");validate(c);try(TenantContext.Scope ignored=TenantContext.open(c.getTenantId(),0,c.getUsername())){String key=failureKey(c.getTenantId(),c.getUsername());int failures=failureCount(key);if(failures>=5)throw locked(key);AuthorizationSnapshot s=repository.findByUsername(c.getTenantId(),c.getUsername());if(s==null||!s.isEnabled()||!passwords.matches(c.getPassword(),s.getPasswordHash())){failures++;dao.set(key,String.valueOf(failures),LOCK_SECONDS);repository.recordLogin(c.getTenantId(),c.getUsername(),false,"用户名或密码错误");if(failures>=5)throw locked(key);throw new SecurityException("用户名或密码错误，还可尝试 "+(5-failures)+" 次");}if(passwords.needsUpgrade(s.getPasswordHash())&&!repository.updatePasswordHash(s.getTenantId(),s.getUserId(),passwords.encode(c.getPassword())))throw new IllegalStateException("密码国密迁移失败");dao.delete(key);String id=loginId(s.getTenantId(),s.getUserId());StpUtil.login(id,8*60*60L);String token=StpUtil.getTokenValueByLoginId(id);SecurityPrincipal p=principal(s);repository.recordLogin(c.getTenantId(),c.getUsername(),true,"登录成功");return new LoginResult("satoken",token,p);}}
 public SecurityPrincipal principal(String token){if(token==null||token.trim().isEmpty())return null;long satokenStarted=RequestPerformance.begin();long[]ids=parseLoginId(StpUtil.getLoginIdByToken(token));RequestPerformance.recordCurrent("satoken",satokenStarted);if(ids==null)return null;try(TenantContext.Scope ignored=TenantContext.open(ids[0],ids[1],"token")){long snapshotStarted=RequestPerformance.begin();AuthorizationSnapshot s=repository.findById(ids[0],ids[1]);RequestPerformance.recordCurrent("authorization",snapshotStarted);return s==null||!s.isEnabled()?null:principal(s);}}
 public void logout(SecurityPrincipal p){if(p!=null)StpUtil.logout(loginId(p.getTenantId(),p.getUserId()));}
 public boolean isAllowed(SecurityPrincipal p,String permission){if(p==null||permission==null||permission.trim().isEmpty())return false;String wanted=permission.trim();Set<String>all=permissions(p);if(wanted.startsWith("platform:"))return all.contains(wanted);return all.contains("*")||all.contains(wanted);}
 public Set<String> permissions(SecurityPrincipal p){if(p!=null&&p.isAuthorizationLoaded())return p.getPermissions();AuthorizationSnapshot s=snapshot(p);return s==null?Collections.<String>emptySet():s.getPermissions();}
 public Set<String> roles(SecurityPrincipal p){if(p!=null&&p.isAuthorizationLoaded())return p.getRoles();AuthorizationSnapshot s=snapshot(p);return s==null?Collections.<String>emptySet():s.getRoles();}
 public void invalidate(long tenantId,long userId){/* permissions are read through the repository on every check */}
 public void invalidateTenantCache(long tenantId){/* authorization is repository-backed; deliberately keep Sa-Token login relations */}
 public void kickout(long tenantId,long userId){StpUtil.logout(loginId(tenantId,userId));}
 public void invalidateTenant(long tenantId){for(String token:StpUtil.searchTokenValue("",0,-1,false)){Object id=StpUtil.getLoginIdByToken(token);long[]ids=parseLoginId(id);if(ids!=null&&ids[0]==tenantId)StpUtil.logout(id);}}
 public void unlock(long tenantId,String username){dao.delete(failureKey(tenantId,username));}
 public long onlineCount(long tenantId){long count=0;for(String token:StpUtil.searchTokenValue("",0,-1,false)){long[]ids=parseLoginId(StpUtil.getLoginIdByToken(token));if(ids!=null&&ids[0]==tenantId)count++;}return count;}
 public void putCaptcha(String uuid,String code,long seconds){dao.set(CAPTCHA+uuid,code,seconds);}
 public boolean consumeCaptcha(String uuid,String code){if(uuid==null||code==null)return false;String key=CAPTCHA+uuid,expected=dao.get(key);dao.delete(key);return expected!=null&&expected.equalsIgnoreCase(code.trim());}
 public void stopAccepting(){accepting=false;} public void close(){accepting=false;}
 private AuthorizationSnapshot snapshot(SecurityPrincipal p){if(p==null)return null;try(TenantContext.Scope ignored=TenantContext.open(p)){AuthorizationSnapshot s=repository.findById(p.getTenantId(),p.getUserId());return s==null||!s.isEnabled()?null:s;}}
 private SecurityPrincipal principal(AuthorizationSnapshot s){return new SecurityPrincipal(s.getUserId(),s.getTenantId(),s.getUsername(),s.isPasswordChangeRequired(),s.getRoles(),s.getPermissions());}
 private int failureCount(String key){try{String v=dao.get(key);return v==null?0:Integer.parseInt(v);}catch(NumberFormatException e){return 0;}}
 private SecurityException locked(String key){long seconds=dao.getTimeout(key);long minutes=seconds<=0?10:(seconds+59)/60;return new SecurityException("账号已临时锁定，请约 "+minutes+" 分钟后重试，或由管理员在登录日志中解锁");}
 private void validate(LoginCommand c){if(c==null||c.getTenantId()<=0||c.getUsername()==null||c.getUsername().trim().isEmpty()||c.getPassword()==null)throw new SecurityException("登录参数不完整");}
 private static String failureKey(long tenant,String user){return FAIL+tenant+":"+user.toLowerCase(Locale.ROOT);}
 public static String loginId(long tenant,long user){return tenant+":"+user;}
 public static long[] parseLoginId(Object value){if(value==null)return null;String[]p=String.valueOf(value).split(":",2);if(p.length!=2)return null;try{return new long[]{Long.parseLong(p[0]),Long.parseLong(p[1])};}catch(NumberFormatException e){return null;}}
}
