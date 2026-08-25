package yh.hotplugin.system.infrastructure.mybatis;

import java.util.*;

public final class DynamicSqlProvider {
    private DynamicSqlProvider() {
    }

    public static String sql(Map<String, Object> p) {
        String s = String.valueOf(p.get("sql"));
        int n = 0;
        StringBuilder o = new StringBuilder();
        for (char c : s.toCharArray())
            if (c == '?') o.append("#{args[").append(n++).append("]}");
            else o.append(c);
        return o.toString();
    }
}
