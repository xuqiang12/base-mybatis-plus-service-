package com.base.business;

import com.base.common.exception.BusinessException;
import com.base.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessCustomerService {
    private final JdbcTemplate jdbc;

    @Transactional
    public BusinessResponses.Customer create(int userId, BusinessRequests.CustomerCreate request) {
        lockAccount(userId);
        var existing = jdbc.query("SELECT * FROM business.biz_customer WHERE owner_id=? AND request_id=?",
                this::readCustomer, userId, request.getRequestId());
        if (!existing.isEmpty()) {
            var old = existing.getFirst();
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT deleted_at IS NOT NULL FROM business.biz_customer WHERE owner_id=? AND id=?",
                    Boolean.class, userId, Long.parseLong(old.getId()))))
                throw new BusinessException(409, "该请求创建的客户已删除，请重新新增");
            if (!old.getName().equals(request.getName().trim())
                    || !old.getAppellation().equals(request.getAppellation().trim())
                    || !old.getPhone().equals(request.getPhone().trim()) || !old.getRemark().equals(request.getRemark().trim()))
                throw new BusinessException(409, "该创建请求已使用，请重新提交");
            return detail(userId, Long.parseLong(old.getId()));
        }
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO business.biz_customer(owner_id,type,name,appellation,phone,remark,request_id,created_by)
                VALUES (?,?,?,?,?,?,?,?)
                """, new String[]{"id"});
            statement.setLong(1, userId);
            statement.setString(2, "farmer");
            statement.setString(3, request.getName().trim());
            statement.setString(4, request.getAppellation().trim());
            statement.setString(5, request.getPhone().trim());
            statement.setString(6, request.getRemark().trim());
            statement.setString(7, request.getRequestId());
            statement.setInt(8, userId);
            return statement;
        }, keys);
        long id = java.util.Objects.requireNonNull(keys.getKey()).longValue();
        return detail(userId, id);
    }

    @Transactional
    public BusinessResponses.Customer update(int userId, BusinessRequests.CustomerUpdate request) {
        lockAccount(userId);
        ownedCustomer(userId, request.getId());
        jdbc.update("""
                UPDATE business.biz_customer SET name=?,appellation=?,phone=?,remark=?,updated_at=CURRENT_TIMESTAMP
                WHERE owner_id=? AND id=?
                """, request.getName().trim(), request.getAppellation().trim(), request.getPhone().trim(),
                request.getRemark().trim(), userId, request.getId());
        return detail(userId, request.getId());
    }

    public BusinessResponses.Customer detail(int userId, long id) {

        var customer = ownedCustomer(userId, id);
        addBalances(userId, List.of(customer));
        return customer;
    }

    @Transactional
    public void delete(int userId, long id) {
        lockAccount(userId);
        var deleted = jdbc.query("SELECT deleted_at FROM business.biz_customer WHERE owner_id=? AND id=? FOR UPDATE",
                (r, index) -> r.getTimestamp("deleted_at") != null, userId, id);
        if (deleted.isEmpty()) throw new BusinessException(404, "客户不存在或无访问权限");
        if (deleted.getFirst()) return;
        var customer = detail(userId, id);
        if (!customer.isSettled())
            throw new BusinessException(409, "客户仍有未结清的应收或应付款，请结清后再删除");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_grain_weighing WHERE owner_id=? AND customer_id=? AND trade_id IS NULL",Long.class,userId,id)>0)
            throw new BusinessException(409, "客户仍有待计价粮食，请结账后再删除");
        // 仅隐藏档案，保留客户及其买卖、结算和分配记录。
        jdbc.update("UPDATE business.biz_customer SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND id=?",
                userId, id);
    }

    public PageResult<BusinessResponses.Customer> list(int userId, BusinessRequests.CustomerQuery query) {

        var args = new ArrayList<Object>();
        args.add(userId);
        String where = " WHERE owner_id=? AND deleted_at IS NULL";
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            // POSITION 按普通文字包含匹配，搜索百分号和下划线也不会变成通配符。
            where += " AND (POSITION(? IN name)>0 OR POSITION(? IN appellation)>0)";
            args.add(query.getKeyword().trim()); args.add(query.getKeyword().trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_customer" + where, Long.class, args.toArray());
        args.add(query.getPageSize()); args.add(((long) query.getPageNum() - 1) * query.getPageSize());
        var rows = jdbc.query("SELECT * FROM business.biz_customer" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                this::readCustomer, args.toArray());
        addBalances(userId, rows);
        return new PageResult<>(rows, total == null ? 0 : total, query.getPageNum(), query.getPageSize());
    }

    private void lockAccount(int userId) {
        jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE id=? FOR UPDATE", Integer.class, userId);
    }

    private BusinessResponses.Customer ownedCustomer(int ownerId, long id) {
        var rows = jdbc.query("SELECT * FROM business.biz_customer WHERE owner_id=? AND id=? AND deleted_at IS NULL", this::readCustomer, ownerId, id);
        if (rows.isEmpty()) throw new BusinessException(404, "客户不存在或无访问权限");
        return rows.getFirst();
    }

    private BusinessResponses.Customer readCustomer(ResultSet r, int index) throws SQLException {
        var c = new BusinessResponses.Customer();
        c.setId(r.getString("id"));
        c.setName(r.getString("name")); c.setAppellation(r.getString("appellation"));
        c.setPhone(r.getString("phone")); c.setRemark(r.getString("remark"));
        c.setCreatedAt(r.getTimestamp("created_at").toInstant()); c.setUpdatedAt(r.getTimestamp("updated_at").toInstant());
        return c;
    }

    /** 只汇总当前页，先聚合结算明细再连接原单，避免一对多连接重复计算成交额。 */
    private void addBalances(int ownerId, List<BusinessResponses.Customer> customers) {
        if (customers.isEmpty()) return;
        String ids = String.join(",", Collections.nCopies(customers.size(), "?"));
        var args = new ArrayList<Object>();
        args.add(ownerId); customers.forEach(c -> args.add(Long.parseLong(c.getId())));
        args.add(ownerId); customers.forEach(c -> args.add(Long.parseLong(c.getId())));
        var byId = customers.stream().collect(Collectors.toMap(BusinessResponses.Customer::getId, c -> c));
        String sql = """
                WITH settled AS (
                  SELECT s.trade_item_id,SUM(s.amount_cents) AS amount_cents
                  FROM business.biz_settlement_item s JOIN business.biz_settlement h
                    ON h.id=s.settlement_id AND h.owner_id=s.owner_id AND h.customer_id=s.customer_id
                  WHERE h.status='posted' AND s.owner_id=? AND s.customer_id IN (%s)
                  GROUP BY s.trade_item_id
                ), remaining AS (
                  SELECT i.customer_id,i.category,t.direction,i.amount_cents-COALESCE(s.amount_cents,0) AS amount_cents
                  FROM business.biz_trade_item i JOIN business.biz_trade t
                    ON t.id=i.trade_id AND t.owner_id=i.owner_id AND t.customer_id=i.customer_id
                  LEFT JOIN settled s ON s.trade_item_id=i.id
                  WHERE t.status='posted' AND i.owner_id=? AND i.customer_id IN (%s)
                )
                SELECT customer_id,
                  SUM(CASE WHEN category='fertilizer' THEN CASE WHEN direction='sale' THEN amount_cents ELSE -amount_cents END ELSE 0 END) AS fertilizer,
                  SUM(CASE WHEN category='grain' THEN CASE WHEN direction='sale' THEN amount_cents ELSE -amount_cents END ELSE 0 END) AS grain,
                  SUM(CASE WHEN direction='sale' THEN amount_cents ELSE 0 END) AS receivable,
                  SUM(CASE WHEN direction='purchase' THEN amount_cents ELSE 0 END) AS payable
                FROM remaining GROUP BY customer_id
                """.formatted(ids, ids);
        jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            var c = byId.get(r.getString("customer_id"));
            c.setFertilizerBalanceCents(r.getLong("fertilizer")); c.setGrainBalanceCents(r.getLong("grain"));
            c.setBalanceCents(Math.addExact(c.getFertilizerBalanceCents(), c.getGrainBalanceCents()));
            c.setReceivableCents(r.getLong("receivable")); c.setPayableCents(r.getLong("payable"));
            c.setSettled(c.getReceivableCents() == 0 && c.getPayableCents() == 0);
        }, args.toArray());
    }
}
