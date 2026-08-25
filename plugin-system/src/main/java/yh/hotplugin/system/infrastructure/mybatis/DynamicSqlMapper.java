package yh.hotplugin.system.infrastructure.mybatis;

import org.apache.ibatis.annotations.*;

import java.util.*;

public interface DynamicSqlMapper {
    @SelectProvider(type = DynamicSqlProvider.class, method = "sql")
    List<Map<String, Object>> select(@Param("sql") String sql, @Param("args") List<Object> args);

    @UpdateProvider(type = DynamicSqlProvider.class, method = "sql")
    int update(@Param("sql") String sql, @Param("args") List<Object> args);

    @InsertProvider(type = DynamicSqlProvider.class, method = "sql")
    @Options(useGeneratedKeys = true, keyProperty = "holder.id")
    int insert(@Param("sql") String sql, @Param("args") List<Object> args, @Param("holder") Map<String, Object> holder);
}
