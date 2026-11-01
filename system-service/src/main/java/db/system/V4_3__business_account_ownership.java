package db.system;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 生意改为一账号一本总账；只迁移生意表，保留原客户、单据及结算关联。 */
public class V4_3__business_account_ownership extends BaseJavaMigration {
    private static final List<String> TABLES = List.of(
            "biz_customer", "biz_trade", "biz_trade_item", "biz_settlement", "biz_settlement_item");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        for (String table : TABLES) {
            execute(connection, "ALTER TABLE business." + table + " ADD COLUMN owner_id INTEGER REFERENCES relations.sys_user(id)");
            execute(connection, "UPDATE business." + table + " SET owner_id=(SELECT b.created_by FROM business.biz_book b WHERE b.id=book_id)");
            execute(connection, "ALTER TABLE business." + table + " ALTER COLUMN owner_id SET NOT NULL");
            execute(connection, "COMMENT ON COLUMN business." + table + ".owner_id IS '所属登录账号，一账号一本生意总账，与家庭无关'");
        }
        // 多个旧账套属于同一账号时合并数据。原先跨账套可重复的创建编号保留首笔，其余换为迁移编号。
        Set<String> usedRequests = new HashSet<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id,owner_id,request_id FROM business.biz_customer ORDER BY owner_id,id");
             var update = connection.prepareStatement("UPDATE business.biz_customer SET request_id=? WHERE id=?")) {
            while (rows.next()) {
                String prefix = rows.getInt("owner_id") + ":";
                String request = rows.getString("request_id");
                if (!usedRequests.add(prefix + request)) {
                    request = "migrated-" + rows.getLong("id");
                    while (!usedRequests.add(prefix + request)) request += "-";
                    update.setString(1, request);
                    update.setLong(2, rows.getLong("id"));
                    update.executeUpdate();
                }
            }
        }
        // PostgreSQL 与 H2 的自动约束名不同，按涉及 book_id 的列定位，不依赖生成名称。
        for (String kind : List.of("FOREIGN KEY", "UNIQUE")) {
            for (String table : TABLES) {
                var constraints = new ArrayList<String>();
                try (var query = connection.prepareStatement("""
                        SELECT DISTINCT tc.constraint_name FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kc
                        ON tc.constraint_catalog=kc.constraint_catalog AND tc.constraint_schema=kc.constraint_schema
                          AND tc.constraint_name=kc.constraint_name
                        WHERE tc.table_schema='business' AND tc.table_name=? AND tc.constraint_type=? AND kc.column_name='book_id'
                        """)) {
                    query.setString(1, table); query.setString(2, kind);
                    try (var rows = query.executeQuery()) { while (rows.next()) constraints.add(rows.getString(1)); }
                }
                for (String constraint : constraints) execute(connection,
                        "ALTER TABLE business." + table + " DROP CONSTRAINT \"" + constraint.replace("\"", "\"\"") + "\"");
            }
        }
        for (String index : List.of("idx_biz_customer_book_type", "idx_biz_trade_customer",
                "idx_biz_trade_item_customer", "idx_biz_settlement_item_trade")) {
            execute(connection, "DROP INDEX business." + index);
        }
        for (String table : TABLES) execute(connection, "ALTER TABLE business." + table + " DROP COLUMN book_id");
        execute(connection, "DROP TABLE business.biz_member");
        execute(connection, "DROP TABLE business.biz_book");
        execute(connection, "ALTER TABLE business.biz_customer ADD CONSTRAINT uq_biz_customer_owner_id UNIQUE(owner_id,id)");
        execute(connection, "ALTER TABLE business.biz_customer ADD CONSTRAINT uq_biz_customer_request UNIQUE(owner_id,request_id)");
        for (String table : List.of("biz_trade", "biz_trade_item", "biz_settlement")) execute(connection,
                "ALTER TABLE business." + table + " ADD CONSTRAINT uq_" + table + "_owner_customer UNIQUE(owner_id,customer_id,id)");
        execute(connection, "ALTER TABLE business.biz_trade ADD CONSTRAINT fk_biz_trade_customer FOREIGN KEY(owner_id,customer_id) REFERENCES business.biz_customer(owner_id,id)");
        execute(connection, "ALTER TABLE business.biz_trade_item ADD CONSTRAINT fk_biz_item_trade FOREIGN KEY(owner_id,customer_id,trade_id) REFERENCES business.biz_trade(owner_id,customer_id,id)");
        execute(connection, "ALTER TABLE business.biz_settlement ADD CONSTRAINT fk_biz_settlement_customer FOREIGN KEY(owner_id,customer_id) REFERENCES business.biz_customer(owner_id,id)");
        execute(connection, "ALTER TABLE business.biz_settlement_item ADD CONSTRAINT fk_biz_allocation_settlement FOREIGN KEY(owner_id,customer_id,settlement_id) REFERENCES business.biz_settlement(owner_id,customer_id,id)");
        execute(connection, "ALTER TABLE business.biz_settlement_item ADD CONSTRAINT fk_biz_allocation_trade FOREIGN KEY(owner_id,customer_id,trade_item_id) REFERENCES business.biz_trade_item(owner_id,customer_id,id)");
        execute(connection, "CREATE INDEX idx_biz_customer_owner_type ON business.biz_customer(owner_id,type,created_at,id)");
        execute(connection, "CREATE INDEX idx_biz_trade_owner_customer ON business.biz_trade(owner_id,customer_id,biz_date,id)");
        execute(connection, "CREATE INDEX idx_biz_item_owner_customer ON business.biz_trade_item(owner_id,customer_id,trade_id)");
        execute(connection, "CREATE INDEX idx_biz_allocation_owner_trade ON business.biz_settlement_item(owner_id,customer_id,trade_item_id)");
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
