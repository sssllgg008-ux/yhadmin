package yh.hotplugin.system.integration;

import cn.dev33.satoken.stp.StpInterface;
import yh.hotplugin.system.application.SystemPermissionProvider;
import yh.hotplugin.system.domain.model.AuthorizationSnapshot;
import yh.hotplugin.system.domain.repository.AuthorizationRepository;

import java.util.*;

/**
 * Solon-owned Sa-Token role and permission source.
 */
public final class SystemStpInterface implements StpInterface {
    private final AuthorizationRepository repository;

    public SystemStpInterface(AuthorizationRepository repository) {
        this.repository = repository;
    }

    public List<String> getPermissionList(Object loginId, String loginType) {
        AuthorizationSnapshot s = snapshot(loginId);
        return s == null ? Collections.<String>emptyList() : new ArrayList<String>(s.getPermissions());
    }

    public List<String> getRoleList(Object loginId, String loginType) {
        AuthorizationSnapshot s = snapshot(loginId);
        return s == null ? Collections.<String>emptyList() : new ArrayList<String>(s.getRoles());
    }

    private AuthorizationSnapshot snapshot(Object id) {
        long[] v = SystemPermissionProvider.parseLoginId(id);
        if (v == null) return null;
        AuthorizationSnapshot s = repository.findById(v[0], v[1]);
        return s == null || !s.isEnabled() ? null : s;
    }
}
