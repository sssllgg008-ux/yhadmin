package yh.hotplugin.security.api;

public final class ErrorAuditEvent {
    private final String pluginName, feature, requestId, requestMethod, requestUri, clientIp, exceptionType, errorMessage, stackTrace;
    private final SecurityPrincipal principal;
    private final int httpStatus;

    public ErrorAuditEvent(String pluginName, String feature, String requestId, String requestMethod, String requestUri,
                           String clientIp, SecurityPrincipal principal, int httpStatus, Throwable error) {
        this.pluginName = pluginName;
        this.feature = feature;
        this.requestId = requestId;
        this.requestMethod = requestMethod;
        this.requestUri = requestUri;
        this.clientIp = clientIp;
        this.principal = principal;
        this.httpStatus = httpStatus;
        this.exceptionType = error == null ? "unknown" : error.getClass().getName();
        this.errorMessage = error == null ? "" : String.valueOf(error.getMessage());
        java.io.StringWriter out = new java.io.StringWriter();
        if (error != null) error.printStackTrace(new java.io.PrintWriter(out));
        this.stackTrace = out.toString();
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getFeature() {
        return feature;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getClientIp() {
        return clientIp;
    }

    public SecurityPrincipal getPrincipal() {
        return principal;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }
}
