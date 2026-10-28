package com.base.system;

import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** H2 的自动约束名与 PostgreSQL 不同，测试中对齐名称以执行相同的后续 SQL。 */
@Component
public class PostgresConstraintNames extends BaseCallback {
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_EACH_MIGRATE
                && "3.13".equals(context.getMigrationInfo().getVersion().getVersion());
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            if (!"H2".equals(context.getConnection().getMetaData().getDatabaseProductName())) return;
            String constraint;
            try (var statement = context.getConnection().createStatement();
                 var rows = statement.executeQuery("SELECT constraint_name FROM information_schema.constraint_column_usage "
                         + "WHERE table_schema='relations' AND table_name='ledger_money_log' AND column_name='type'")) {
                if (!rows.next()) throw new IllegalStateException("未找到资金流水类型约束");
                constraint = rows.getString(1);
            }
            try (var statement = context.getConnection().createStatement()) {
                statement.execute("ALTER TABLE relations.ledger_money_log RENAME CONSTRAINT \""
                        + constraint.replace("\"", "\"\"") + "\" TO ledger_money_log_type_check");
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("无法对齐 PostgreSQL 资金流水类型约束名", exception);
        }
    }
}
