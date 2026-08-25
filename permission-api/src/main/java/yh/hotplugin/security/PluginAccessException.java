package yh.hotplugin.security;

public final class PluginAccessException extends SecurityException {
    private final int status;
    public PluginAccessException(int status,String message){super(message);this.status=status;}
    public int getStatus(){return status;}
}
