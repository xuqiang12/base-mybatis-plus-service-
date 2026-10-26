package db.system;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** PostgreSQL 和测试库的自动约束名不同，按约束列定位后移除，原有收支不变。 */
public class V3_9__allow_repeated_household_categories extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        String constraint;
        try (var statement = context.getConnection().createStatement();
             var rows = statement.executeQuery("SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                     + "JOIN information_schema.key_column_usage kc ON tc.constraint_catalog=kc.constraint_catalog "
                     + "AND tc.constraint_schema=kc.constraint_schema AND tc.constraint_name=kc.constraint_name "
                     + "WHERE tc.table_schema='relations' AND tc.table_name='ledger_household_item' "
                     + "AND tc.constraint_type='UNIQUE' GROUP BY tc.constraint_name "
                     + "HAVING COUNT(*)=2 AND SUM(CASE WHEN kc.column_name IN ('month_id','category_id') THEN 1 ELSE 0 END)=2")) {
            if (!rows.next()) throw new IllegalStateException("未找到收支月分类唯一约束");
            constraint = rows.getString(1);
        }
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE relations.ledger_household_item DROP CONSTRAINT \"" + constraint.replace("\"", "\"\"") + "\"");
            statement.execute("CREATE INDEX idx_ledger_household_item_month_category ON relations.ledger_household_item(month_id,category_id)");
            statement.execute("COMMENT ON TABLE relations.ledger_household_item IS '家庭收支独立记录，同月同分类允许多笔'");
            statement.execute("COMMENT ON COLUMN relations.ledger_household_item.id IS '收支记录编号，自增主键'");
        }
    }
}
