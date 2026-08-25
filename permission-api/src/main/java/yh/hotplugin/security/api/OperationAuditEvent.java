package yh.hotplugin.security.api;

public final class OperationAuditEvent {
    private final String pluginName,feature,permission,requestId,requestMethod,requestUri,clientIp,parameters,errorMessage;
    private final SecurityPrincipal principal;
    private final BusinessType businessType;
    private final boolean success;
    private final long costTime;
    public OperationAuditEvent(String pluginName,String feature,String permission,BusinessType businessType,
            String requestId,String requestMethod,String requestUri,String clientIp,SecurityPrincipal principal,
            String parameters,boolean success,String errorMessage,long costTime){
        this.pluginName=pluginName;this.feature=feature;this.permission=permission;this.businessType=businessType;
        this.requestId=requestId;this.requestMethod=requestMethod;this.requestUri=requestUri;this.clientIp=clientIp;
        this.principal=principal;this.parameters=parameters;this.success=success;this.errorMessage=errorMessage;this.costTime=costTime;
    }
    public String getPluginName(){return pluginName;} public String getFeature(){return feature;} public String getPermission(){return permission;}
    public BusinessType getBusinessType(){return businessType;} public String getRequestId(){return requestId;}
    public String getRequestMethod(){return requestMethod;} public String getRequestUri(){return requestUri;} public String getClientIp(){return clientIp;}
    public SecurityPrincipal getPrincipal(){return principal;} public String getParameters(){return parameters;} public boolean isSuccess(){return success;}
    public String getErrorMessage(){return errorMessage;} public long getCostTime(){return costTime;}
}
