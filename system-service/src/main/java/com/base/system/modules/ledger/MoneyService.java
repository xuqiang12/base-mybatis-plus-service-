package com.base.system.modules.ledger;

import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.ledger.dto.LedgerRequests;
import com.base.system.modules.ledger.dto.LedgerResponses;
import com.base.system.modules.ledger.entity.HouseholdItemEntity;
import com.base.system.modules.ledger.entity.LoanEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.time.Instant;

/** 资金项目和调拨；余额统一从现有业务流水派生，避免月表补填时重复记账。 */
@Service
@RequiredArgsConstructor
public class MoneyService {
    private final JdbcTemplate jdbc;
    private final FamilyMapper familyMapper;

    /** 新家庭创建事务内初始化；读取资金时不再创建项目。 */
    @Transactional
    public void initializeCash(String familyId) {
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key) "
                + "SELECT ?, 'other', '现金', 'cash' WHERE NOT EXISTS "
                + "(SELECT 1 FROM relations.ledger_money_project WHERE family_id=? AND cash_key='cash')", familyId, familyId);
    }

    @Transactional
    public LedgerResponses.MoneySummary summary(int userId) {
        return familySummary(lockFamily(userId));
    }

    @Transactional
    public LedgerResponses.MoneySummary createProject(int userId, LedgerRequests.MoneyProject request) {
        String familyId = lockFamily(userId);
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,note,initial_balance_cents) VALUES (?,?,?,?,?)",
                familyId, request.getType(), request.getName().trim(), value(request.getNote()), request.getInitialBalanceCents());
        if (request.getInitialBalanceCents() > 0)
            appendTimeline(familyId, "opening", "create", request.getInitialBalanceCents(),
                    request.getName().trim(), value(request.getNote()), "期初余额 → " + request.getName().trim());
        return familySummary(familyId);
    }

    @Transactional
    public LedgerResponses.MoneySummary updateProject(int userId, LedgerRequests.MoneyProjectUpdate request) {
        String familyId = lockFamily(userId);
        Long id = resolveProject(familyId, request.getId());
        if (id.equals(cashId(familyId))) throw new BusinessException(400, "现金为默认账户，不能修改");
        jdbc.update("UPDATE relations.ledger_money_project SET type=?,name=?,note=? WHERE family_id=? AND id=?",
                request.getType(), request.getName().trim(), value(request.getNote()), familyId, id);
        return familySummary(familyId);
    }

    @Transactional
    public LedgerResponses.MoneySummary deleteProject(int userId, Long projectId) {
        String familyId = lockFamily(userId);
        Long id = resolveProject(familyId, projectId);
        if (id.equals(cashId(familyId))) throw new BusinessException(400, "现金为默认账户，不能删除");
        long openingBalance = jdbc.queryForObject("SELECT initial_balance_cents FROM relations.ledger_money_project WHERE family_id=? AND id=?",
                Long.class, familyId, id);
        if (openingBalance > 0) throw new BusinessException(409, "账户有期初余额，不能删除");
        long references = jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item WHERE family_id=? AND project_id=?",
                Long.class, familyId, id);
        references += jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_transfer WHERE family_id=? AND (from_project_id=? OR to_project_id=?)",
                Long.class, familyId, id, id);
        references += jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_loan WHERE family_id=? AND project_id=?",
                Long.class, familyId, id);
        if (references > 0) throw new BusinessException(409, "账户已有收支、转账或借贷记录，不能删除");
        jdbc.update("DELETE FROM relations.ledger_money_project WHERE family_id=? AND id=?", familyId, id);
        return familySummary(familyId);
    }

    @Transactional
    public LedgerResponses.MoneySummary transfer(int userId, LedgerRequests.MoneyTransfer request) {
        String familyId = lockFamily(userId);
        Long from = resolveProject(familyId, request.getFromProjectId());
        Long to = request.getToProjectId();
        if (to == null && request.getName() != null && !request.getName().isBlank()) {
            if (!from.equals(cashId(familyId))) throw new BusinessException(400, "只能从剩余现金拆分新项目");
            if (!Set.of("bank", "stock", "other").contains(request.getType() == null ? "" : request.getType()))
                throw new BusinessException(400, "请选择有效的项目类型");
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO relations.ledger_money_project(family_id,type,name,note) VALUES (?,?,?,?)",
                        new String[]{"id"});
                statement.setString(1, familyId);
                statement.setString(2, request.getType());
                statement.setString(3, request.getName().trim());
                statement.setString(4, value(request.getNote()));
                return statement;
            }, key);
            to = key.getKey().longValue();
        } else {
            to = resolveProject(familyId, to);
        }
        if (Objects.equals(from, to)) throw new BusinessException(400, "请选择不同的来源和目标");
        jdbc.update("INSERT INTO relations.ledger_money_transfer(family_id,from_project_id,to_project_id,amount_cents,note,created_by) VALUES (?,?,?,?,?,?)",
                familyId, from, to, request.getAmountCents(), value(request.getNote()), userId);
        LedgerResponses.MoneySummary summary = validateBalances(familyId);
        appendTimeline(familyId, "transfer", "create", request.getAmountCents(), "账户转账", value(request.getNote()),
                projectName(familyId, from) + " → " + projectName(familyId, to));
        return summary;
    }

    @Transactional
    public LedgerResponses.MoneyTimeline timeline(int userId, Long beforeId, String month, String keyword) {
        String familyId = lockFamily(userId);
        String sql = "SELECT * FROM relations.ledger_money_log WHERE family_id=?";
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(familyId);
        sql += timelineSearch(keyword, parameters);
        if (month != null && !month.isBlank()) {
            java.time.YearMonth selected;
            try {
                selected = java.time.YearMonth.parse(month);
            } catch (java.time.DateTimeException error) {
                throw new BusinessException(400, "请选择有效月份");
            }
            var zone = java.time.ZoneId.of("Asia/Shanghai");
            sql += " AND created_at >= ? AND created_at < ?";
            parameters.add(java.sql.Timestamp.from(selected.atDay(1).atStartOfDay(zone).toInstant()));
            parameters.add(java.sql.Timestamp.from(selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()));
        }
        if (beforeId != null) {
            sql += " AND id < ?";
            parameters.add(beforeId);
        }
        sql += " ORDER BY id DESC LIMIT 21";
        var logs = jdbc.query(sql, (row, index) -> {
            var log = new LedgerResponses.MoneyLog();
            log.setId(row.getLong("id"));
            log.setType(row.getString("type"));
            log.setAction(row.getString("action"));
            log.setAmountCents(row.getLong("amount_cents"));
            log.setTitle(row.getString("title"));
            log.setNote(row.getString("note"));
            log.setFlow(row.getString("flow"));
            log.setTotalCents(row.getLong("total_cents"));
            log.setSavingsCents(row.getLong("savings_cents"));
            log.setAccountCents(row.getLong("account_cents"));
            log.setCreatedAt(row.getTimestamp("created_at").toInstant());
            return log;
        }, parameters.toArray());
        var result = new LedgerResponses.MoneyTimeline();
        boolean hasMore = logs.size() > 20;
        result.setItems(hasMore ? logs.subList(0, 20) : logs);
        result.setNextCursor(hasMore ? logs.get(19).getId() : null);
        return result;
    }

    @Transactional
    public java.util.List<LedgerResponses.MoneyMonth> timelineMonths(int userId, String keyword) {
        String familyId = lockFamily(userId);
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(familyId);
        String sql = "SELECT EXTRACT(YEAR FROM created_at AT TIME ZONE 'Asia/Shanghai') AS log_year, "
                + "EXTRACT(MONTH FROM created_at AT TIME ZONE 'Asia/Shanghai') AS log_month, COUNT(*) AS record_count "
                + "FROM relations.ledger_money_log WHERE family_id=?";
        sql += timelineSearch(keyword, parameters);
        sql += " GROUP BY log_year, log_month ORDER BY log_year DESC, log_month DESC";
        return jdbc.query(sql, (row, index) -> {
            var result = new LedgerResponses.MoneyMonth();
            result.setMonth(String.format("%04d-%02d", row.getInt("log_year"), row.getInt("log_month")));
            result.setCount(row.getLong("record_count"));
            return result;
        }, parameters.toArray());
    }

    private String timelineSearch(String keyword, java.util.List<Object> parameters) {
        String text = value(keyword).toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty()) return "";
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        parameters.add(pattern);
        parameters.add(pattern);
        parameters.add(pattern);
        String filter = " AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(note) LIKE ? ESCAPE '!' OR LOWER(flow) LIKE ? ESCAPE '!'";
        String amount = text.replace(",", "");
        if (amount.matches("\\d{1,14}(\\.\\d{1,2})?")) {
            filter += " OR amount_cents=?";
            parameters.add(new java.math.BigDecimal(amount).movePointRight(2).longValueExact());
        }
        return filter + ")";
    }

    // 调用方已持家庭锁；日志与原业务在同一事务提交或回滚。
    void recordHousehold(String familyId, HouseholdItemEntity item, String categoryName, String action) {
        String project = projectName(familyId, item.getProjectId());
        String flow = switch (item.getType()) {
            case "income" -> "收入 → " + project;
            case "expense" -> project + " → 消费";
            case "saving" -> Boolean.TRUE.equals(item.getSavingTransfer()) ? "现金 → " + project : "仅存款统计，不变动账户";
            default -> "";
        };
        appendTimeline(familyId, item.getType(), action, item.getAmountCents(), categoryName, item.getNote(), flow);
    }

    void recordLoan(String familyId, LoanEntity loan) {
        String project = projectName(familyId, loan.getProjectId());
        boolean outgoing = "lend".equals(loan.getDirection()) || "pay".equals(loan.getDirection());
        String flow = outgoing ? project + " → " + loan.getName() : loan.getName() + " → " + project;
        appendTimeline(familyId, loan.getDirection(), "create", loan.getAmountCents(), loan.getName(), loan.getNote(), flow);
    }

    private String projectName(String familyId, Long projectId) {
        if (projectId == null) return "现金";
        return jdbc.queryForObject("SELECT name FROM relations.ledger_money_project WHERE family_id=? AND id=?",
                String.class, familyId, projectId);
    }

    private void appendTimeline(String familyId, String type, String action, long amount, String title, String note, String flow) {
        LedgerResponses.MoneySummary summary = familySummary(familyId);
        jdbc.update("INSERT INTO relations.ledger_money_log(family_id,type,action,amount_cents,title,note,flow,"
                        + "total_cents,savings_cents,account_cents,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                familyId, type, action, amount, title, value(note), flow,
                summary.getTotalCents(), summary.getSavingsCents(), summary.getHeldCents() - summary.getCashCents(),
                java.sql.Timestamp.from(Instant.now()));
    }

    // 项目编号必须属于持锁家庭，不能只凭外键存在就跨家庭使用。
    Long resolveProject(String familyId, Long projectId) {
        if (projectId == null) return cashId(familyId);
        if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM relations.ledger_money_project WHERE family_id=? AND id=?",
                Long.class, familyId, projectId) == 0)
            throw new BusinessException(404, "资金项目不存在");
        return projectId;
    }

    Long resolveHouseholdProject(String familyId, String type, Long projectId) {
        Long resolved = resolveProject(familyId, projectId);
        if ("income".equals(type)) {
            Long cash = cashId(familyId);
            if (projectId != null && !cash.equals(projectId))
                throw new BusinessException(400, "收入只能进入现金账户");
            return cash;
        }
        if ("saving".equals(type) && resolved.equals(cashId(familyId)))
            throw new BusinessException(400, "请选择现金以外的存款去向");
        return resolved;
    }

    private Long cashId(String familyId) {
        return jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id=? AND cash_key='cash'", Long.class, familyId);
    }

    /** 调用方已持家庭行锁；全部写完后统一检查，失败由原事务回滚。 */
    LedgerResponses.MoneySummary validateBalances(String familyId) {
        LedgerResponses.MoneySummary summary = familySummary(familyId);
        for (LedgerResponses.MoneyProject project : summary.getProjects()) {
            if (project.getBalanceCents() < 0)
                throw new BusinessException(409, project.getName() + "余额不足，请调整金额或资金来源",
                        java.util.Map.of("reason", "INSUFFICIENT_BALANCE", "projectId", project.getId(),
                                "projectName", project.getName(), "shortfallCents", -project.getBalanceCents()));
        }
        return summary;
    }

    private LedgerResponses.MoneySummary familySummary(String familyId) {
        var projects = jdbc.query("SELECT id,type,name,note,cash_key,initial_balance_cents FROM relations.ledger_money_project WHERE family_id=? "
                        + "ORDER BY CASE WHEN cash_key='cash' THEN 0 ELSE 1 END, id",
                (row, index) -> {
                    var project = new LedgerResponses.MoneyProject();
                    project.setId(row.getLong("id"));
                    project.setType("cash".equals(row.getString("cash_key")) ? "cash" : row.getString("type"));
                    project.setName(row.getString("name"));
                    project.setNote(row.getString("note"));
                    project.setBalanceCents(row.getLong("initial_balance_cents"));
                    return project;
                }, familyId);
        var byId = projects.stream().collect(java.util.stream.Collectors.toMap(LedgerResponses.MoneyProject::getId, item -> item));
        var cash = projects.stream().filter(project -> "cash".equals(project.getType())).findFirst().orElseThrow();
        // 新存款在现金与目标项目之间转移，旧存款仅保留报表统计。
        for (var item : jdbc.queryForList("SELECT project_id,type,amount_cents FROM relations.ledger_household_item WHERE family_id=? "
                + "AND (type IN ('income','expense') OR (type='saving' AND saving_transfer=TRUE))", familyId)) {
            long amount = ((Number) item.get("amount_cents")).longValue();
            if ("expense".equals(item.get("type"))) amount = -amount;
            if ("saving".equals(item.get("type"))) cash.setBalanceCents(cash.getBalanceCents() - amount);
            var project = byId.get(((Number) item.get("project_id")).longValue());
            project.setBalanceCents(project.getBalanceCents() + amount);
        }
        for (var item : jdbc.queryForList("SELECT from_project_id,to_project_id,amount_cents FROM relations.ledger_money_transfer WHERE family_id=?", familyId)) {
            long amount = ((Number) item.get("amount_cents")).longValue();
            var from = byId.get(((Number) item.get("from_project_id")).longValue());
            var to = byId.get(((Number) item.get("to_project_id")).longValue());
            from.setBalanceCents(from.getBalanceCents() - amount);
            to.setBalanceCents(to.getBalanceCents() + amount);
        }
        long lent = 0;
        long debt = 0;
        for (var item : jdbc.queryForList("SELECT direction,amount_cents,project_id FROM relations.ledger_loan WHERE family_id=?", familyId)) {
            long amount = ((Number) item.get("amount_cents")).longValue();
            var project = item.get("project_id") == null ? cash : byId.get(((Number) item.get("project_id")).longValue());
            switch ((String) item.get("direction")) {
                case "lend" -> { lent += amount; project.setBalanceCents(project.getBalanceCents() - amount); }
                case "receive" -> { lent -= amount; project.setBalanceCents(project.getBalanceCents() + amount); }
                case "borrow" -> { debt += amount; project.setBalanceCents(project.getBalanceCents() + amount); }
                case "pay" -> { debt -= amount; project.setBalanceCents(project.getBalanceCents() - amount); }
            }
        }
        long held = projects.stream().mapToLong(LedgerResponses.MoneyProject::getBalanceCents).sum();
        var result = new LedgerResponses.MoneySummary();
        result.setCashCents(cash.getBalanceCents());
        result.setHeldCents(held);
        result.setLentCents(lent);
        result.setDebtCents(debt);
        result.setTotalCents(held + lent - debt);
        result.setSavingsCents(held - cash.getBalanceCents() + lent - debt);
        result.setProjects(projects);
        return result;
    }

    private String lockFamily(int userId) {
        String familyId = familyMapper.currentFamily(userId);
        FamilyModels.Family family = familyId == null ? null : familyMapper.lockFamily(familyId);
        // 持锁后再次核对成员归属，避免成员刚退出时写入旧家庭。
        if (family == null || family.getHiddenOwner() != null || !familyId.equals(familyMapper.currentFamily(userId)))
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        return familyId;
    }

    private String value(String text) { return text == null ? "" : text.trim(); }
}
