package yh.hotplugin.security;

public final class DataResourcePolicy {
    private final boolean department,creator,user;
    private DataResourcePolicy(boolean d,boolean c,boolean u){department=d;creator=c;user=u;}
    public static DataResourcePolicy departmentAndCreator(){return new DataResourcePolicy(true,true,false);}
    public static DataResourcePolicy creatorOnly(){return new DataResourcePolicy(false,true,false);}
    public static DataResourcePolicy tenantOnly(){return new DataResourcePolicy(false,false,false);}
    public boolean supportsDepartment(){return department;} public boolean supportsCreator(){return creator;} public boolean supportsUser(){return user;}
}
