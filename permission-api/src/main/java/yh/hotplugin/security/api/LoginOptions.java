package yh.hotplugin.security.api;

/** Host-owned session options. Values are deliberately framework neutral. */
public final class LoginOptions {
    public static final long DEFAULT_TIMEOUT_SECONDS = 8L * 60L * 60L;
    private final long timeoutSeconds;
    private final String device;

    public LoginOptions(long timeoutSeconds, String device) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.device = device == null || device.trim().isEmpty() ? "web" : device.trim();
    }

    public static LoginOptions defaults() { return new LoginOptions(DEFAULT_TIMEOUT_SECONDS, "web"); }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public String getDevice() { return device; }
}
