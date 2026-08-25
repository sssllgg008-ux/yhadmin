package yh.hotplugin.security.api;

public final class LoginCommand {
    private final long tenantId;
    private final String username, password;

    public LoginCommand(long t, String u, String p) {
        tenantId = t;
        username = u;
        password = p;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
