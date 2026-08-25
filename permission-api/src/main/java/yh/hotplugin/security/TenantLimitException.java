package yh.hotplugin.security;

public final class TenantLimitException extends RuntimeException {
    private final int status;
    private final String quotaKey;
    private final long limit;
    private final long current;
    public TenantLimitException(int status,String message){this(status,message,null,-1,-1);}
    public TenantLimitException(int status,String message,String quotaKey,long limit,long current){super(message);this.status=status;this.quotaKey=quotaKey;this.limit=limit;this.current=current;}
    public int getStatus(){return status;}
    public String getQuotaKey(){return quotaKey;}
    public long getLimit(){return limit;}
    public long getCurrent(){return current;}
}
