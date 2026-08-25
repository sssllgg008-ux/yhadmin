package yh.hotplugin.system.integration;

/**
 * DamiBus request DTO. Identity and tenant are recovered from the authenticated token.
 */
public final class PermissionCheck {
    private String token;
    private String permission;

    public PermissionCheck() {
    }

    public PermissionCheck(String token, String permission) {
        this.token = token;
        this.permission = permission;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }
}
