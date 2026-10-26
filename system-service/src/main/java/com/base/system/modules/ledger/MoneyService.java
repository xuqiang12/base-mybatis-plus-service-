package com.base.system.modules.ledger;

import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.ledger.dto.LedgerRequests;
import com.base.system.modules.ledger.dto.LedgerResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/** 资金项目和调拨；余额统一从现有业务流水派生，避免月表补填时重复记账。 */
@Service
@RequiredArgsConstructor
public class MoneyService {
    private final JdbcTemplate jdbc;
    private final FamilyMapper familyMapper;

    @Transactional
    public LedgerResponses.MoneySummary summary(int userId) {
        return familySummary(lockFamily(userId));
    }

    @Transactional
    public LedgerResponses.MoneySummary transfer(int userId, LedgerRequests.MoneyTransfer request) {
        String familyId = lockFamily(userId);
        Long from = request.getFromProjectId();
        Long to = request.getToProjectId();
        requireProject(familyId, from);
        requireProject(familyId, to);
        if (to == null && request.getName() != null && !request.getName().isBlank()) {
            if (from != null) throw new BusinessException(400, "只能从剩余现金拆分新项目");
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
        }
        if (Objects.equals(from, to)) throw new BusinessException(400, "请选择不同的来源和目标");
        jdbc.update("INSERT INTO relations.ledger_money_transfer(family_id,from_project_id,to_project_id,amount_cents,note,created_by) VALUES (?,?,?,?,?,?)",
                familyId, from, to, request.getAmountCents(), value(request.getNote()), userId);
        return validateBalances(familyId);
    }

    // 项目编号必须属于持锁家庭，不能只凭外键存在就跨家庭使用。
    void requireProject(String familyId, Long projectId) {
        if (projectId != null && jdbc.queryForObject(
                "SELECT COUNT(*) FROM relations.ledger_money_project WHERE family_id=? AND id=?",
                Long.class, familyId, projectId) == 0)
            throw new BusinessException(404, "资金项目不存在");
    }

    /** 调用方已持家庭行锁；全部写完后统一检查，失败由原事务回滚。 */
    LedgerResponses.MoneySummary validateBalances(String familyId) {
        LedgerResponses.MoneySummary summary = familySummary(familyId);
        if (summary.getCashCents() < 0) throw new BusinessException(409, "现金余额不足，请调整金额或资金来源");
        for (LedgerResponses.MoneyProject project : summary.getProjects()) {
            if (project.getBalanceCents() < 0)
                throw new BusinessException(409, project.getName() + "余额不足，请调整金额或资金来源");
        }
        return summary;
    }

    private LedgerResponses.MoneySummary familySummary(String familyId) {
        var projects = jdbc.query("SELECT id,type,name,note FROM relations.ledger_money_project WHERE family_id=? ORDER BY id",
                (row, index) -> {
                    var project = new LedgerResponses.MoneyProject();
                    project.setId(row.getLong("id"));
                    project.setType(row.getString("type"));
                    project.setName(row.getString("name"));
                    project.setNote(row.getString("note"));
                    return project;
                }, familyId);
        var byId = projects.stream().collect(java.util.stream.Collectors.toMap(LedgerResponses.MoneyProject::getId, item -> item));
        long household = 0;
        // saving 是旧版月表的独立统计项，不是第二份资金。
        for (var item : jdbc.queryForList("SELECT project_id,type,amount_cents FROM relations.ledger_household_item WHERE family_id=? AND type IN ('income','expense')", familyId)) {
            long amount = ((Number) item.get("amount_cents")).longValue();
            if ("expense".equals(item.get("type"))) amount = -amount;
            household += amount;
            if (item.get("project_id") != null) {
                var project = byId.get(((Number) item.get("project_id")).longValue());
                project.setBalanceCents(project.getBalanceCents() + amount);
            }
        }
        for (var item : jdbc.queryForList("SELECT from_project_id,to_project_id,amount_cents FROM relations.ledger_money_transfer WHERE family_id=?", familyId)) {
            long amount = ((Number) item.get("amount_cents")).longValue();
            if (item.get("from_project_id") != null) {
                var project = byId.get(((Number) item.get("from_project_id")).longValue());
                project.setBalanceCents(project.getBalanceCents() - amount);
            }
            if (item.get("to_project_id") != null) {
                var project = byId.get(((Number) item.get("to_project_id")).longValue());
                project.setBalanceCents(project.getBalanceCents() + amount);
            }
        }
        long lent = 0;
        long debt = 0;
        for (var item : jdbc.queryForList("SELECT direction,amount_cents FROM relations.ledger_loan WHERE family_id=?", familyId)) {
            long amount = ((Number) item.get("amount_cents")).longValue();
            switch ((String) item.get("direction")) {
                case "lend" -> lent += amount;
                case "receive" -> lent -= amount;
                case "borrow" -> debt += amount;
                case "pay" -> debt -= amount;
            }
        }
        long held = household - lent + debt;
        var result = new LedgerResponses.MoneySummary();
        result.setCashCents(held - projects.stream().mapToLong(LedgerResponses.MoneyProject::getBalanceCents).sum());
        result.setHeldCents(held);
        result.setLentCents(lent);
        result.setDebtCents(debt);
        result.setTotalCents(held + lent - debt);
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
