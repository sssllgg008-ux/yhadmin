package yh.hotplugin.system.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import yh.hotplugin.security.tenant.*;
import yh.hotplugin.system.infrastructure.mybatis.MybatisExecutor;
import yh.hotplugin.system.application.SystemPermissionProvider;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Database-only tenant backup orchestration. Files are always host-managed. */
public final class TenantBackupService {
    private final MybatisExecutor db;
    private final Path root;
    private final ObjectMapper json = new ObjectMapper();
    private final TenantUsageCalibrationService usage;
    private final SystemPermissionProvider permissions;

    public TenantBackupService(JdbcAuthorizationRepository authorization, String directory) {
        this(authorization, directory, null, null);
    }

    public TenantBackupService(JdbcAuthorizationRepository authorization, String directory,
                               TenantUsageCalibrationService usage, SystemPermissionProvider permissions) {
        db = authorization.executor();
        this.usage = usage;
        this.permissions = permissions;
        root = Paths.get(directory == null || directory.trim().isEmpty() ? "data/tenant-backups" : directory).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException e) { throw new IllegalStateException("BACKUP_DIRECTORY_UNAVAILABLE", e); }
    }

    public Map<String,Object> create(long tenantId, String actor) {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, actor)) {
            long id = db.insert("INSERT INTO sys_tenant_backup(tenant_id,status,create_time) VALUES(?,'RUNNING',CURRENT_TIMESTAMP)", Collections.<Object>singletonList(tenantId));
            Path temp = root.resolve("tenant-" + tenantId + "-" + id + ".tmp");
            Path target = root.resolve("tenant-" + tenantId + "-" + id + ".zip");
            try {
                if (TenantContributorRegistry.data().isEmpty()) throw new IllegalStateException("TENANT_BACKUP_CONTRIBUTOR_MISSING");
                Map<String,Object> manifest = new LinkedHashMap<String,Object>();
                manifest.put("formatVersion", 1); manifest.put("tenantId", tenantId); manifest.put("createdBy", actor);
                List<Map<String,Object>> plugins = new ArrayList<Map<String,Object>>();
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                    for (TenantDataContributor contributor : TenantContributorRegistry.data()) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        contributor.exportTenant(tenantId, bytes);
                        String entry = "plugins/" + safe(contributor.pluginName()) + ".json";
                        zip.putNextEntry(new ZipEntry(entry)); zip.write(bytes.toByteArray()); zip.closeEntry();
                        Map<String,Object> plugin = new LinkedHashMap<String,Object>();
                        plugin.put("name", contributor.pluginName()); plugin.put("version", contributor.dataVersion()); plugin.put("entry", entry);
                        plugin.put("size", bytes.size()); plugin.put("sha256", sha256(bytes.toByteArray()));
                        plugins.add(plugin);
                    }
                    manifest.put("plugins", plugins);
                    zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(json.writeValueAsBytes(manifest)); zip.closeEntry();
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                String checksum = sha256(target);
                db.update("UPDATE sys_tenant_backup SET status='SUCCEEDED',file_name=?,file_path=?,checksum=?,finish_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", Arrays.<Object>asList(target.getFileName().toString(), target.toString(), checksum, id, tenantId));
                enforceRetention(tenantId);
                return get(tenantId, id);
            } catch (Exception e) {
                try { Files.deleteIfExists(temp); } catch (IOException ignoredDelete) { }
                db.update("UPDATE sys_tenant_backup SET status='FAILED',error_message=?,finish_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", Arrays.<Object>asList(cut(e.getMessage()), id, tenantId));
                throw new IllegalStateException("TENANT_BACKUP_FAILED", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> restore(long tenantId, long backupId, String actor) {
        try (TenantContext.Scope ignored = TenantContext.openPlatform(0, actor)) {
            if (db.count("SELECT COUNT(*) FROM sys_tenant WHERE id=? AND status='1'", Collections.<Object>singletonList(tenantId)) != 1)
                throw new IllegalArgumentException("仅允许恢复到已停用租户");
            long taskId=db.insert("INSERT INTO sys_tenant_restore_task(tenant_id,backup_id,status,actor,create_time) VALUES(?,?,'PENDING',?,CURRENT_TIMESTAMP)",Arrays.<Object>asList(tenantId,backupId,actor));
            Map<String,Object> backup = get(tenantId, backupId);
            if (backup == null || !"SUCCEEDED".equals(String.valueOf(backup.get("status")))) { finish(taskId,"FAILED","备份不可用"); return task(taskId); }
            Path file = checkedPath(String.valueOf(backup.get("filePath")));
            List<RestoreUnit> applied=new ArrayList<RestoreUnit>();
            try (ZipFile zip = new ZipFile(file.toFile())) {
                state(taskId,"VALIDATING",null);
                if (!sha256(file).equals(String.valueOf(backup.get("checksum")))) throw new IllegalArgumentException("备份校验失败");
                ZipEntry manifestEntry=requiredEntry(zip,"manifest.json");
                Map<String,Object> manifest = json.readValue(zip.getInputStream(manifestEntry), Map.class);
                if (((Number) manifest.get("tenantId")).longValue() != tenantId) throw new IllegalArgumentException("备份租户不匹配");
                Map<String,TenantDataContributor> available = new LinkedHashMap<String,TenantDataContributor>();
                for (TenantDataContributor c : TenantContributorRegistry.data()) available.put(c.pluginName(), c);
                List<RestoreUnit> prepared=new ArrayList<RestoreUnit>();int sequence=0;
                for (Map<String,Object> plugin : (List<Map<String,Object>>) manifest.get("plugins")) {
                    String name=String.valueOf(plugin.get("name"));TenantDataContributor contributor = available.get(name);
                    if (contributor == null) throw new IllegalStateException("TENANT_RESTORE_CONTRIBUTOR_MISSING: " + plugin.get("name"));
                    if (!Objects.equals(contributor.dataVersion(), String.valueOf(plugin.get("version")))) throw new IllegalStateException("TENANT_BACKUP_VERSION_MISMATCH: " + plugin.get("name"));
                    String entry=checkedEntry(String.valueOf(plugin.get("entry")));byte[]payload=read(zip,requiredEntry(zip,entry));
                    if(plugin.get("sha256")!=null&&!sha256(payload).equals(String.valueOf(plugin.get("sha256"))))throw new IllegalArgumentException("备份条目校验失败: "+name);
                    ByteArrayOutputStream snapshot=new ByteArrayOutputStream();contributor.exportTenant(tenantId,snapshot);
                    prepared.add(new RestoreUnit(name,contributor,payload,snapshot.toByteArray()));detail(taskId,tenantId,name,++sequence,"VALIDATED",null);
                }
                state(taskId,"RUNNING",null);
                for(RestoreUnit unit:prepared){unit.contributor.importTenant(tenantId,new ByteArrayInputStream(unit.payload));applied.add(unit);detail(taskId,tenantId,unit.name,0,"SUCCEEDED",null);}
                if(usage!=null)usage.calibrate(tenantId);if(permissions!=null)permissions.invalidateTenantCache(tenantId);finish(taskId,"SUCCEEDED",null);return task(taskId);
            } catch (Exception e) {
                if(applied.isEmpty()){finish(taskId,"FAILED",e.getMessage());return task(taskId);}
                state(taskId,"COMPENSATING",e.getMessage());Collections.reverse(applied);boolean ok=true;String compensation=null;
                for(RestoreUnit unit:applied)try{unit.contributor.importTenant(tenantId,new ByteArrayInputStream(unit.snapshot));detail(taskId,tenantId,unit.name,0,"ROLLED_BACK",null);}catch(Exception x){ok=false;compensation=x.getMessage();detail(taskId,tenantId,unit.name,0,"COMPENSATION_FAILED",x.getMessage());}
                finish(taskId,ok?"ROLLED_BACK":"MANUAL_RECOVERY_REQUIRED",ok?e.getMessage():e.getMessage()+"; compensation: "+compensation);return task(taskId);
            }
        }
    }

    public Map<String,Object> retry(long tenantId,long taskId,String actor){Map<String,Object>old=task(taskId);if(old==null||number(old.get("tenantId"))!=tenantId)throw new IllegalArgumentException("恢复任务不存在");String status=String.valueOf(old.get("status"));if(!"FAILED".equals(status)&&!"ROLLED_BACK".equals(status))throw new IllegalArgumentException("当前恢复任务不可重试");return restore(tenantId,number(old.get("backupId")),actor);}
    public Map<String,Object> task(long taskId){Map<String,Object>row=db.one("SELECT * FROM sys_tenant_restore_task WHERE id=?",Collections.<Object>singletonList(taskId));if(row!=null)row.put("contributors",db.query("SELECT * FROM sys_tenant_restore_detail WHERE task_id=? ORDER BY sequence_no,id",Collections.<Object>singletonList(taskId)));return row;}

    public List<Map<String,Object>> list(long tenantId) { return db.query("SELECT * FROM sys_tenant_backup WHERE tenant_id=? ORDER BY id DESC", Collections.<Object>singletonList(tenantId)); }
    public Map<String,Object> get(long tenantId, long id) { return db.one("SELECT * FROM sys_tenant_backup WHERE tenant_id=? AND id=?", Arrays.<Object>asList(tenantId, id)); }
    public Path file(long tenantId, long id) { Map<String,Object> row=get(tenantId,id); if(row==null)throw new IllegalArgumentException("备份不存在"); return checkedPath(String.valueOf(row.get("filePath"))); }
    public void delete(long tenantId,long id){Path file=file(tenantId,id);try{Files.deleteIfExists(file);}catch(IOException e){throw new IllegalStateException("BACKUP_DELETE_FAILED",e);}db.update("DELETE FROM sys_tenant_backup WHERE tenant_id=? AND id=?",Arrays.<Object>asList(tenantId,id));}

    private void enforceRetention(long tenantId) throws IOException {
        Map<String,Object> q=db.one("SELECT COALESCE(o.quota_limit,pq.quota_limit) quota_limit FROM sys_tenant_subscription s JOIN sys_plan_quota pq ON pq.plan_id=s.plan_id AND pq.quota_key='backups.max' LEFT JOIN sys_tenant_quota_override o ON o.tenant_id=s.tenant_id AND o.quota_key=pq.quota_key WHERE s.tenant_id=? AND s.status='ACTIVE' ORDER BY s.id DESC LIMIT 1",Collections.<Object>singletonList(tenantId));
        int keep=q==null||q.get("quotaLimit")==null?5:((Number)q.get("quotaLimit")).intValue();
        List<Map<String,Object>> old=db.query("SELECT id,file_path FROM sys_tenant_backup WHERE tenant_id=? AND status='SUCCEEDED' ORDER BY id DESC LIMIT 1000 OFFSET ?",Arrays.<Object>asList(tenantId,Math.max(0,keep)));
        for(Map<String,Object> row:old){Files.deleteIfExists(checkedPath(String.valueOf(row.get("filePath"))));db.update("DELETE FROM sys_tenant_backup WHERE id=? AND tenant_id=?",Arrays.<Object>asList(row.get("id"),tenantId));}
    }
    private Path checkedPath(String value){Path p=Paths.get(value).toAbsolutePath().normalize();if(!p.startsWith(root))throw new SecurityException("INVALID_BACKUP_PATH");return p;}
    private static String checkedEntry(String value){Path p=Paths.get(value).normalize();if(p.isAbsolute()||value.contains("..")||!value.startsWith("plugins/"))throw new SecurityException("INVALID_BACKUP_ENTRY");return value;}
    private static ZipEntry requiredEntry(ZipFile zip,String name){ZipEntry entry=zip.getEntry(name);if(entry==null||entry.isDirectory())throw new IllegalArgumentException("备份条目缺失: "+name);return entry;}
    private static byte[] read(ZipFile zip,ZipEntry entry)throws IOException{try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]buffer=new byte[8192];for(int n;(n=in.read(buffer))>0;)out.write(buffer,0,n);return out.toByteArray();}}
    private void state(long id,String status,String error){db.update("UPDATE sys_tenant_restore_task SET status=?,error_message=?,start_time=COALESCE(start_time,CURRENT_TIMESTAMP) WHERE id=?",Arrays.<Object>asList(status,cut(error),id));}
    private void finish(long id,String status,String error){db.update("UPDATE sys_tenant_restore_task SET status=?,error_message=?,finish_time=CURRENT_TIMESTAMP WHERE id=?",Arrays.<Object>asList(status,cut(error),id));}
    private void detail(long task,long tenant,String plugin,int sequence,String status,String error){db.update("INSERT INTO sys_tenant_restore_detail(task_id,tenant_id,plugin_name,sequence_no,status,error_message,update_time) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE status=VALUES(status),error_message=VALUES(error_message),update_time=CURRENT_TIMESTAMP",Arrays.<Object>asList(task,tenant,plugin,sequence,status,cut(error)));}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():Long.parseLong(String.valueOf(value));}
    private static String safe(String v){return v.replaceAll("[^A-Za-z0-9._-]","_");}
    private static String cut(String v){if(v==null)return "unknown";return v.length()<=1000?v:v.substring(0,1000);}
    private static String sha256(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[8192];for(int n;(n=in.read(b))>0;)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02x",b));return s.toString();}
    private static String sha256(byte[] bytes)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(bytes);StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02x",b));return s.toString();}
    private static final class RestoreUnit{final String name;final TenantDataContributor contributor;final byte[]payload,snapshot;RestoreUnit(String name,TenantDataContributor contributor,byte[]payload,byte[]snapshot){this.name=name;this.contributor=contributor;this.payload=payload;this.snapshot=snapshot;}}
}
