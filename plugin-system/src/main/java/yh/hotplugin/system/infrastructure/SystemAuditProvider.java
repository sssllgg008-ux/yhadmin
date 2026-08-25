package yh.hotplugin.system.infrastructure;

import yh.hotplugin.security.api.*;

public final class SystemAuditProvider implements AuditProvider {
    private final JdbcCrudRepository repository;

    public SystemAuditProvider(JdbcCrudRepository repository) {
        this.repository = repository;
    }

    public void operation(OperationAuditEvent e) {
        SecurityPrincipal p = e.getPrincipal();
        repository.audit(p == null ? 1 : p.getTenantId(), p == null ? "system" : p.getUsername(), "", e.getFeature(), e.getBusinessType().getCode(), e.getPluginName() + ":" + e.getPermission(), e.getRequestMethod(), e.getRequestUri(), e.getClientIp(), e.getParameters(), e.isSuccess(), e.getErrorMessage(), e.getCostTime());
    }

    public void error(ErrorAuditEvent e) {
        repository.errorAudit(e);
    }
}
