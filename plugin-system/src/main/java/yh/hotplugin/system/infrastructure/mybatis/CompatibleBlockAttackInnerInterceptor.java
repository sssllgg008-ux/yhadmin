package yh.hotplugin.system.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import org.apache.ibatis.executor.statement.StatementHandler;

import java.sql.Connection;
import java.util.Locale;

/**
 * DynamicSqlMapper.update can carry INSERT SQL. MyBatis reports that mapped
 * statement as UPDATE, so the stock block-attack interceptor attempts to
 * process an Insert AST and throws UnsupportedOperationException. Keep the
 * protection for real UPDATE/DELETE statements and ignore only INSERT SQL.
 */
public final class CompatibleBlockAttackInnerInterceptor extends BlockAttackInnerInterceptor {
    @Override
    public void beforePrepare(StatementHandler statementHandler, Connection connection, Integer transactionTimeout) {
        String sql = statementHandler.getBoundSql().getSql();
        if (sql != null && sql.trim().toUpperCase(Locale.ROOT).startsWith("INSERT")) {
            return;
        }
        super.beforePrepare(statementHandler, connection, transactionTimeout);
    }
}
