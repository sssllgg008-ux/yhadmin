package yh.hotplugin.security.api;

import java.util.*;

public final class DataScopeResult {
    public enum Scope {ALL, CUSTOM, DEPT, DEPT_AND_BELOW, SELF, DENY}

    private final Scope scope;
    private final Set<Long> departmentIds;
    private final Long ownerUserId;

    public DataScopeResult(Scope s, Set<Long> d, Long o) {
        scope = s;
        departmentIds = d == null ? Collections.<Long>emptySet() : Collections.unmodifiableSet(new LinkedHashSet<Long>(d));
        ownerUserId = o;
    }

    public static DataScopeResult deny() {
        return new DataScopeResult(Scope.DENY, null, null);
    }

    public Scope getScope() {
        return scope;
    }

    public Set<Long> getDepartmentIds() {
        return departmentIds;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }
}
