package yh.hotplugin.security.api;

public interface AuditProvider {
    void operation(OperationAuditEvent event);

    void error(ErrorAuditEvent event);
}
