package yh.hotplugin.system.integration;

public final class LoginRequest {
    private Long tenantId;
    private String username;
    private String password;
    private String uuid;
    private String code;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long v) {
        tenantId = v;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String v) {
        username = v;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String v) {
        password = v;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String v) {
        uuid = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        code = v;
    }
}
