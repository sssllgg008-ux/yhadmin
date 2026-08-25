package yh.hotplugin.system.application;

import yh.hotplugin.system.domain.repository.AuthorizationRepository;
import yh.hotplugin.system.domain.model.AuthorizationSnapshot;
import java.util.Set;

/** Permission use-case facade; persistence details remain behind the domain port. */
public final class PermissionApplicationService {
    private final AuthorizationRepository repository;
    public PermissionApplicationService(AuthorizationRepository repository) { this.repository = repository; }
    public boolean check(long userId, String permission) { Set<String> p=permissions(userId); return permission!=null&&(p.contains("*")||p.contains(permission)); }
    public Set<String> permissions(long userId) { AuthorizationSnapshot s=repository.findById(1L,userId); return s==null||!s.isEnabled()?java.util.Collections.<String>emptySet():s.getPermissions(); }
}
