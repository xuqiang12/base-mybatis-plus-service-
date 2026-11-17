package db.system;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.SQLException;
import java.util.ArrayList;

// 旧 SQL 的自动约束名在 PostgreSQL/H2 中不同，通过元数据找到原唯一约束。
public class V3_19__shared_menu_permissions extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        var names = new ArrayList<String>();
        try (var statement = connection.createStatement()) {
            try (var constraints = statement.executeQuery("""
                    SELECT t.constraint_name FROM information_schema.table_constraints t
                    JOIN information_schema.key_column_usage k
                      ON t.constraint_schema=k.constraint_schema AND t.constraint_name=k.constraint_name
                    WHERE t.table_schema='relations' AND t.table_name='sys_menu_button' AND t.constraint_type IN ('UNIQUE','FOREIGN KEY')
                    GROUP BY t.constraint_name,t.constraint_type
                    HAVING COUNT(*)=1 AND MIN(k.column_name)='permission_id'
                    ORDER BY t.constraint_type
                    """)) {
                while (constraints.next()) names.add(constraints.getString(1));
            }
            for (String name : names) {
                statement.execute("ALTER TABLE relations.sys_menu_button DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
            }
            // H2 的外键会复用原唯一索引，先重建外键才能完整移除旧唯一性。
            statement.execute("ALTER TABLE relations.sys_menu_button ADD CONSTRAINT fk_menu_button_permission FOREIGN KEY(permission_id) REFERENCES relations.sys_permission(id)");
            statement.execute("ALTER TABLE relations.sys_menu_button ADD CONSTRAINT uq_menu_button_permission UNIQUE(menu_id,permission_id)");
            statement.execute("COMMENT ON COLUMN relations.sys_menu_button.permission_id IS '操作权限ID，关联权限表，同一权限可被多个页面复用'");
        }
    }
}
