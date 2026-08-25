package yh.hotplugin.security.api;

public final class TokenResult {
    private final String tokenName;
    private final String token;
    private final long timeoutSeconds;
    public TokenResult(String tokenName, String token, long timeoutSeconds) { this.tokenName=tokenName; this.token=token; this.timeoutSeconds=timeoutSeconds; }
    public String getTokenName() { return tokenName; }
    public String getToken() { return token; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
}
