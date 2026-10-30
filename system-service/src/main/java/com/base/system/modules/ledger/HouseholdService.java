package com.base.system.modules.ledger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.ledger.dto.LedgerRequests;
import com.base.system.modules.ledger.dto.LedgerQueries;
import com.base.system.modules.ledger.dto.LedgerReadResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import com.base.system.modules.ledger.dto.LedgerResponses;
import com.base.system.modules.ledger.entity.CategoryEntity;
import com.base.system.modules.ledger.entity.GiftEntity;
import com.base.system.modules.ledger.entity.HouseholdItemEntity;
import com.base.system.modules.ledger.entity.HouseholdMonthEntity;
import com.base.system.modules.ledger.entity.LoanEntity;
import com.base.system.modules.ledger.mapper.CategoryMapper;
import com.base.system.modules.ledger.mapper.HouseholdItemMapper;
import com.base.system.modules.ledger.mapper.HouseholdMonthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;

/** 月度家庭收支和分类业务。 */
@Service
@RequiredArgsConstructor
public class HouseholdService {
    private static final Set<String> TYPES = Set.of("expense", "income", "saving");
    private final CategoryMapper categoryMapper;
    private final HouseholdMonthMapper monthMapper;
    private final HouseholdItemMapper itemMapper;
    private final FamilyMapper familyMapper;
    private final MoneyService moneyService;
    private final JdbcTemplate jdbc;

    private static final String EFFECTIVE_DATE = "COALESCE(h.record_date, CAST(m.month_value || '-01' AS DATE))";
    private static final String READ_FROM = " FROM relations.ledger_household_item h "
            + "JOIN relations.ledger_household_month m ON m.id=h.month_id AND m.family_id=h.family_id "
            + "LEFT JOIN relations.ledger_category c ON c.id=h.category_id AND c.family_id=h.family_id "
            + "LEFT JOIN relations.ledger_money_project p ON p.id=h.project_id AND p.family_id=h.family_id "
            + "LEFT JOIN relations.ledger_gift g ON g.id=h.gift_id AND g.family_id=h.family_id "
            + "LEFT JOIN relations.ledger_book b ON b.id=g.book_id AND b.family_id=h.family_id ";
    private static final String READ_SELECT = "SELECT h.*, " + EFFECTIVE_DATE + " AS effective_date, "
            + "c.name AS category_name,c.icon AS category_icon,c.active AS category_active,c.deleted_at AS category_deleted_at,"
            + "p.name AS project_name,CASE WHEN p.cash_key='cash' THEN 'cash' ELSE p.type END AS project_type,"
            + "g.name AS gift_name,g.appellation AS gift_appellation,g.event_type AS gift_event_type,b.name AS book_name";
    private static final String SUM_SELECT = "COUNT(*) AS count,"
            + "COALESCE(SUM(CASE WHEN h.type='income' THEN h.amount_cents ELSE 0 END),0) AS income_cents,"
            + "COALESCE(SUM(CASE WHEN h.type='expense' THEN h.amount_cents ELSE 0 END),0) AS expense_cents,"
            + "COALESCE(SUM(CASE WHEN h.type='saving' THEN CASE WHEN h.saving_effect='subtract' THEN -h.amount_cents ELSE h.amount_cents END "
            + "WHEN h.type='income' AND p.cash_key IS NULL AND p.id IS NOT NULL THEN h.amount_cents "
            + "WHEN h.type='expense' AND p.cash_key IS NULL AND p.id IS NOT NULL THEN -h.amount_cents ELSE 0 END),0) AS saving_cents";

    public LedgerReadResponses.Household detail(int userId, long id) {
        String familyId = currentFamily(userId, false);
        var rows = jdbc.query(READ_SELECT + READ_FROM + " WHERE h.family_id=? AND h.id=?", this::readHousehold, familyId, id);
        if (rows.isEmpty()) throw new BusinessException(404, "收支记录不存在");
        return rows.getFirst();
    }

    public LedgerReadResponses.HouseholdPage list(int userId, LedgerQueries.Records query) {
        return listForFamily(currentFamily(userId, false), query);
    }

    LedgerReadResponses.HouseholdPage listForFamily(String familyId, LedgerQueries.Records query) {
        var args = new java.util.ArrayList<Object>();
        String where = householdWhere(familyId, query, args);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + READ_FROM + where, Long.class, args.toArray());
        args.add(query.getPageSize()); args.add(query.offset());
        var rows = jdbc.query(READ_SELECT + READ_FROM + where + " ORDER BY effective_date DESC,h.id DESC LIMIT ? OFFSET ?", this::readHousehold, args.toArray());
        var result = new LedgerReadResponses.HouseholdPage();
        result.setList(rows); result.setTotal(total); result.setPageNum(query.getPageNum()); result.setPageSize(query.getPageSize());
        result.setMonthSummaries(List.of());
        if (!rows.isEmpty()) {
            var selectedMonths = rows.stream().map(row -> row.getDate().substring(0, 7)).distinct().toList();
            var monthArgs = new java.util.ArrayList<Object>();
            String monthWhere = householdWhere(familyId, query, monthArgs);
            String month = "SUBSTRING(CAST(" + EFFECTIVE_DATE + " AS VARCHAR),1,7)";
            monthWhere += " AND " + month + " IN (" + String.join(",", java.util.Collections.nCopies(selectedMonths.size(), "?")) + ")";
            monthArgs.addAll(selectedMonths);
            result.setMonthSummaries(jdbc.query("SELECT " + month + " AS selected_month," + SUM_SELECT + READ_FROM + monthWhere
                    + " GROUP BY " + month + " ORDER BY selected_month DESC", (row, index) -> {
                var item = new LedgerReadResponses.MonthSummary(); item.setMonth(row.getString("selected_month")); readSummary(row, item); return item;
            }, monthArgs.toArray()));
        }
        return result;
    }

    public LedgerReadResponses.HouseholdSummary summary(int userId, LedgerQueries.Records query) {
        return summaryForFamily(currentFamily(userId, false), query);
    }

    private LedgerReadResponses.HouseholdSummary summaryForFamily(String familyId, LedgerQueries.Records query) {
        var args = new java.util.ArrayList<Object>();
        String where = householdWhere(familyId, query, args);
        return jdbc.queryForObject("SELECT " + SUM_SELECT + READ_FROM + where, (row, index) -> {
            var result = new LedgerReadResponses.HouseholdSummary();
            readSummary(row, result);
            return result;
        }, args.toArray());
    }

    @Transactional
    public List<LedgerResponses.Category> categories(int userId) {
        String familyId = currentFamily(userId, true);
        ensureCategories(familyId);
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId).orderByAsc(CategoryEntity::getSortOrder).orderByAsc(CategoryEntity::getId))
                .stream().map(this::category).toList();
    }

    public LedgerReadResponses.Statistics statistics(int userId, String period) {
        if (period == null) throw new BusinessException(400, "请选择有效期间");
        String familyId = currentFamily(userId, false);
        var query = new LedgerQueries.Records(); query.setPeriod(period);
        var args = new java.util.ArrayList<Object>();
        String where = householdWhere(familyId, query, args);
        var result = new LedgerReadResponses.Statistics();
        result.setSummary(summaryForFamily(familyId, query));
        String month = "SUBSTRING(CAST(" + EFFECTIVE_DATE + " AS VARCHAR),1,7)";
        var months = jdbc.query("SELECT " + month + " AS selected_month," + SUM_SELECT + READ_FROM + where + " GROUP BY " + month + " ORDER BY selected_month", (row, index) -> {
            var item = new LedgerReadResponses.MonthSummary(); item.setMonth(row.getString("selected_month")); readSummary(row, item); return item;
        }, args.toArray());
        if (period.length() == 4) {
            var byMonth = months.stream().collect(Collectors.toMap(LedgerReadResponses.MonthSummary::getMonth, Function.identity()));
            months = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(number -> {
                String key = period + String.format("-%02d", number);
                var item = byMonth.get(key);
                if (item == null) { item = new LedgerReadResponses.MonthSummary(); item.setMonth(key); }
                return item;
            }).toList();
        }
        result.setMonths(months);
        result.setCategories(jdbc.query("SELECT h.type,h.category_id,CASE WHEN h.gift_id IS NOT NULL THEN 'gift' ELSE NULL END AS source,"
                + "c.name AS category_name,c.icon AS category_icon,COUNT(*) AS count,SUM(CASE WHEN h.type='saving' AND h.saving_effect='subtract' THEN -h.amount_cents ELSE h.amount_cents END) AS amount_cents" + READ_FROM + where
                + " GROUP BY h.type,h.category_id,CASE WHEN h.gift_id IS NOT NULL THEN 'gift' ELSE NULL END,c.name,c.icon,c.sort_order"
                + " ORDER BY h.type,c.sort_order,h.category_id", (row, index) -> {
            var item = new LedgerReadResponses.CategorySummary(); item.setType(row.getString("type"));
            item.setCategoryId(row.getObject("category_id", Long.class)); item.setSource(row.getString("source"));
            item.setCategoryName(row.getString("category_name")); item.setCategoryIcon(row.getString("category_icon"));
            item.setAmountCents(row.getLong("amount_cents")); item.setCount(row.getLong("count")); return item;
        }, args.toArray()));
        return result;
    }

    private String householdWhere(String familyId, LedgerQueries.Records query, List<Object> args) {
        args.add(familyId);
        String where = " WHERE h.family_id=? AND h.loan_id IS NULL";
        if (query.getPeriod() != null) { where += " AND " + EFFECTIVE_DATE + ">=? AND " + EFFECTIVE_DATE + "<?"; args.add(LedgerQueries.start(query.getPeriod())); args.add(LedgerQueries.end(query.getPeriod())); }
        if (query.getType() != null) { where += " AND h.type=?"; args.add(query.getType()); }
        if (query.getCategoryId() != null) { where += " AND h.category_id=? AND h.gift_id IS NULL"; args.add(query.getCategoryId()); }
        if (query.getSource() != null) where += " AND h.gift_id IS NOT NULL";
        return where;
    }

    private void readSummary(java.sql.ResultSet row, LedgerReadResponses.HouseholdSummary item) throws java.sql.SQLException {
        item.setCount(row.getLong("count")); item.setIncomeCents(row.getLong("income_cents")); item.setExpenseCents(row.getLong("expense_cents")); item.setSavingCents(row.getLong("saving_cents"));
        item.setBalanceCents(item.getIncomeCents() - item.getExpenseCents() - item.getSavingCents());
    }

    private LedgerReadResponses.Household readHousehold(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var item = new LedgerReadResponses.Household();
        item.setId(row.getLong("id")); item.setType(row.getString("type")); item.setCategoryId(row.getObject("category_id", Long.class));
        item.setAmountCents(row.getLong("amount_cents")); item.setDate(row.getDate("effective_date").toString()); item.setNote(row.getString("note"));
        item.setProjectId(row.getObject("project_id", Long.class)); item.setProjectType(row.getString("project_type"));
        item.setGiftId(row.getObject("gift_id", Long.class)); item.setLoanId(row.getObject("loan_id", Long.class)); item.setSavingEffect(row.getString("saving_effect"));
        item.setCreatedBy(row.getString("created_by")); item.setCreatedByName(row.getString("created_by_name"));
        item.setCreatedAt(row.getTimestamp("created_at").toInstant()); item.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        item.setCategoryName(row.getString("category_name")); item.setCategoryIcon(row.getString("category_icon")); item.setCategoryActive(row.getObject("category_active", Boolean.class));
        item.setCategoryDeleted(row.getTimestamp("category_deleted_at") != null); item.setProjectName(row.getString("project_name"));
        item.setBookName(row.getString("book_name")); item.setGiftName(row.getString("gift_name")); item.setGiftAppellation(row.getString("gift_appellation")); item.setGiftEventType(row.getString("gift_event_type"));
        return item;
    }

    /** 返回当前家庭分类和月度分类合计。 */
    @Transactional
    public Snapshot snapshot(int userId) {
        String familyId = currentFamily(userId, true);
        ensureCategories(familyId);
        return historySnapshot(familyId);
    }

    /** 读取快照只接收已校验的家庭 ID，避免重复查询成员归属。 */
    Snapshot snapshot(String familyId) {
        long categoryCount = categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId));
        if (categoryCount == 0) {
            // 只有首次初始化默认分类时才锁家庭；已有分类的读取不阻塞写请求。
            FamilyModels.Family family = familyMapper.lockFamily(familyId);
            if (family == null || family.getHiddenOwner() != null)
                throw new BusinessException(409, "家庭已变化，请刷新后重试");
            ensureCategories(familyId);
        }
        return historySnapshot(familyId);
    }

    // 只由已校验归属的业务读取，历史家庭不会初始化分类或执行任何写入。
    Snapshot historySnapshot(String familyId) {
        Map<Long, String> projectTypes = moneyService.projectTypes(familyId);
        List<LedgerResponses.Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId)
                .orderByAsc(CategoryEntity::getSortOrder)
                .orderByAsc(CategoryEntity::getId)).stream().map(this::category).toList();
        Map<Long, String> months = monthMapper.selectList(new LambdaQueryWrapper<HouseholdMonthEntity>()
                .eq(HouseholdMonthEntity::getFamilyId, familyId)).stream()
                .collect(Collectors.toMap(HouseholdMonthEntity::getId, month -> month.getMonthValue() + "-01"));
        List<LedgerResponses.Household> records = itemMapper.selectList(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId)
                .orderByDesc(HouseholdItemEntity::getId)).stream().map(item -> household(item, months.get(item.getMonthId()), projectTypes)).toList();
        return new Snapshot(categories, records);
    }

    @Transactional
    public LedgerResponses.Category createCategory(int userId, LedgerRequests.Category request) {
        String familyId = currentFamily(userId, true);
        validateCategory(request.getType(), request.getName());
        if ("saving".equals(request.getType())) throw new BusinessException(400, "存款分类不可新增");
        duplicate(familyId, request.getType(), request.getName(), null);
        CategoryEntity entity = new CategoryEntity();
        entity.setFamilyId(familyId);
        entity.setType(request.getType());
        entity.setName(request.getName().trim());
        entity.setIcon(request.getIcon());
        entity.setActive(true);
        entity.setSortOrder(nextSort(familyId, request.getType()));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        categoryMapper.insert(entity);
        return category(entity);
    }

    @Transactional
    public LedgerResponses.Category updateCategory(int userId, LedgerRequests.CategoryUpdate request) {
        String familyId = currentFamily(userId, true);
        CategoryEntity entity = ownedCategory(familyId, request.getId());
        validateCategory(entity.getType(), request.getName());
        duplicate(familyId, entity.getType(), request.getName(), entity.getId());
        entity.setName(request.getName().trim());
        entity.setIcon(request.getIcon());
        entity.setActive(request.getActive());
        if (entity.getDeletedAt() != null) throw new BusinessException(409, "分类已删除");
        entity.setUpdatedAt(Instant.now());
        categoryMapper.updateById(entity);
        return category(entity);
    }

    @Transactional
    public void deleteCategory(int userId, long id) {
        String familyId = currentFamily(userId, true);
        CategoryEntity entity = ownedCategory(familyId, id);
        if ("saving".equals(entity.getType())) throw new BusinessException(409, "存款分类不可删除");
        if (entity.getDeletedAt() != null) throw new BusinessException(409, "分类已删除");
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        categoryMapper.updateById(entity);
    }

    @Transactional
    public List<LedgerResponses.Category> moveCategory(int userId, long id) {
        String familyId = currentFamily(userId, true);
        CategoryEntity entity = ownedCategory(familyId, id);
        if ("saving".equals(entity.getType())) throw new BusinessException(409, "存款分类不可排序");
        if (entity.getDeletedAt() != null) throw new BusinessException(409, "分类已删除");
        List<CategoryEntity> sameType = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId).eq(CategoryEntity::getType, entity.getType())
                .isNull(CategoryEntity::getDeletedAt)
                .orderByAsc(CategoryEntity::getSortOrder));
        int index = -1;
        for (int i = 0; i < sameType.size(); i++) {
            if (sameType.get(i).getId().equals(entity.getId())) {
                index = i;
                break;
            }
        }
        if (index > 0) {
            CategoryEntity previous = sameType.get(index - 1);
            int sort = entity.getSortOrder();
            entity.setSortOrder(previous.getSortOrder());
            previous.setSortOrder(sort);
            categoryMapper.updateById(entity);
            categoryMapper.updateById(previous);
        }
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId)
                .orderByAsc(CategoryEntity::getSortOrder)
                .orderByAsc(CategoryEntity::getId)).stream().map(this::category).toList();
    }

    @Transactional
    public LedgerResponses.Month readMonth(int userId, String value) {
        String familyId = currentFamily(userId, true);
        return monthResponse(familyId, monthDate(value));
    }

    /** 在家庭行锁内追加本次收支；同月同分类允许多笔，余额校验失败时整批回滚。 */
    @Transactional
    public LedgerResponses.Month saveMonth(int userId, LedgerRequests.Month request) {
        String familyId = currentFamily(userId, true);
        LocalDate date = monthDate(request.getMonth());
        Map<Long, CategoryEntity> categories = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId)).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
        HouseholdMonthEntity month = month(familyId, date, true);
        String createdByName = value(familyMapper.userName(userId));
        for (LedgerRequests.MonthItem item : request.getItems()) {
            CategoryEntity category = categories.get(item.getCategoryId());
            if (category == null) throw new BusinessException(404, "分类不存在");
            if (!category.getType().equals(item.getType())) throw new BusinessException(400, "收支方向与分类不一致");
            if (!category.getActive() || category.getDeletedAt() != null) throw new BusinessException(409, "分类不可用");
            Long projectId = moneyService.resolveHouseholdProject(familyId, item.getType(), item.getProjectId());
            HouseholdItemEntity record = new HouseholdItemEntity();
            record.setFamilyId(familyId);
            record.setMonthId(month.getId());
            record.setCategoryId(category.getId());
            record.setType(category.getType());
            record.setCreatedBy(userId);
            record.setCreatedByName(createdByName);
            record.setCreatedAt(Instant.now());
            record.setAmountCents(item.getAmountCents());
            record.setProjectId(projectId);
            record.setSavingTransfer("saving".equals(item.getType()));
            record.setNote(value(item.getNote()));
            record.setUpdatedAt(Instant.now());
            itemMapper.insert(record);
            moneyService.recordHousehold(familyId, record, category.getName(), "create");
        }
        month.setUpdatedAt(Instant.now());
        monthMapper.updateById(month);
        moneyService.validateBalances(familyId);
        return monthResponse(familyId, date);
    }

    private LocalDate monthDate(String value) {
        try { return java.time.YearMonth.parse(value).atDay(1); }
        catch (java.time.DateTimeException | NullPointerException error) { throw new BusinessException(400, "请选择有效月份"); }
    }

    private LedgerResponses.Month monthResponse(String familyId, LocalDate date) {
        HouseholdMonthEntity month = month(familyId, date, false);
        Map<Long, String> projectTypes = moneyService.projectTypes(familyId);
        LedgerResponses.Month result = new LedgerResponses.Month();
        result.setId(month == null ? null : month.getId());
        result.setMonth(date.toString().substring(0, 7));
        var query = new LedgerQueries.Records(); query.setPeriod(result.getMonth());
        result.setSummary(summaryForFamily(familyId, query));
        result.setItems(month == null ? List.of() : itemMapper.selectList(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId).eq(HouseholdItemEntity::getMonthId, month.getId()))
                .stream().map(item -> household(item, date.toString(), projectTypes)).toList());
        return result;
    }

    @Transactional
    public LedgerResponses.Household saveRecord(int userId, LedgerRequests.Household request) {
        String familyId = currentFamily(userId, true);
        Long projectId = moneyService.resolveHouseholdProject(familyId, request.getType(), request.getProjectId());
        CategoryEntity category = ownedCategory(familyId, request.getCategoryId());
        if (!category.getType().equals(request.getType())) throw new BusinessException(400, "收支方向与分类不一致");
        if (!Boolean.TRUE.equals(category.getActive()) || category.getDeletedAt() != null) throw new BusinessException(409, "分类不可用");
        HouseholdMonthEntity month = month(familyId, request.getDate(), true);
        HouseholdItemEntity existing = new HouseholdItemEntity();
        existing.setMonthId(month.getId());
        existing.setFamilyId(familyId);
        existing.setCategoryId(category.getId());
        existing.setType(request.getType());
        existing.setCreatedBy(userId);
        existing.setCreatedByName(value(familyMapper.userName(userId)));
        existing.setCreatedAt(Instant.now());
        existing.setAmountCents(request.getAmountCents());
        existing.setProjectId(projectId);
        existing.setSavingTransfer("saving".equals(request.getType()));
        existing.setNote(value(request.getNote()));
        existing.setUpdatedAt(Instant.now());
        itemMapper.insert(existing);
        moneyService.validateBalances(familyId);
        moneyService.recordHousehold(familyId, existing, category.getName(), "create");
        return household(existing, request.getDate().withDayOfMonth(1).toString(), moneyService.projectTypes(familyId));
    }

    @Transactional
    public LedgerResponses.Household updateRecord(int userId, LedgerRequests.HouseholdUpdate request) {
        String familyId = currentFamily(userId, true);
        HouseholdItemEntity existing = itemMapper.selectOne(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getId, request.getId()).eq(HouseholdItemEntity::getFamilyId, familyId));
        if (existing == null) throw new BusinessException(404, "收支记录不存在");
        if (existing.getLoanId() != null) throw new BusinessException(409, "借贷关联存款记录不能单独修改");
        if (existing.getGiftId() != null) throw new BusinessException(409, "人情关联收支不能单独修改");
        HouseholdMonthEntity month = monthMapper.selectById(existing.getMonthId());
        if (!month.getMonthValue().equals(request.getDate().toString().substring(0, 7)))
            throw new BusinessException(400, "月份不可修改，请删除后在其他月份新增");
        // 旧存款始终只改统计，不因编辑金额或备注而重放现金转账。
        boolean linkedSaving = "saving".equals(request.getType())
                && (!"saving".equals(existing.getType()) || Boolean.TRUE.equals(existing.getSavingTransfer()));
        Long projectId = linkedSaving || "income".equals(request.getType())
                ? moneyService.resolveHouseholdProject(familyId, request.getType(), request.getProjectId())
                : moneyService.resolveProject(familyId, request.getProjectId());
        CategoryEntity category = ownedCategory(familyId, request.getCategoryId());
        if (!category.getType().equals(request.getType())) throw new BusinessException(400, "收支方向与分类不一致");
        if ((category.getDeletedAt() != null || !Boolean.TRUE.equals(category.getActive()))
                && !category.getId().equals(existing.getCategoryId()))
            throw new BusinessException(409, "分类不可用");
        existing.setCategoryId(category.getId());
        existing.setType(request.getType());
        existing.setAmountCents(request.getAmountCents());
        existing.setProjectId(projectId);
        existing.setSavingTransfer(linkedSaving);
        existing.setNote(value(request.getNote()));
        existing.setUpdatedAt(Instant.now());
        itemMapper.updateById(existing);
        moneyService.validateBalances(familyId);
        moneyService.recordHousehold(familyId, existing, category.getName(), "update");
        return household(existing, request.getDate().withDayOfMonth(1).toString(), moneyService.projectTypes(familyId));
    }

    @Transactional
    public void deleteRecord(int userId, long id) {
        String familyId = currentFamily(userId, true);
        HouseholdItemEntity item = itemMapper.selectOne(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getId, id).eq(HouseholdItemEntity::getFamilyId, familyId));
        if (item == null) throw new BusinessException(404, "收支记录不存在");
        if (item.getLoanId() != null) throw new BusinessException(409, "借贷关联存款记录不能单独删除");
        if (item.getGiftId() != null) throw new BusinessException(409, "人情关联收支不能单独删除");
        itemMapper.deleteById(id);
        if (itemMapper.selectCount(new LambdaQueryWrapper<HouseholdItemEntity>().eq(HouseholdItemEntity::getMonthId, item.getMonthId())) == 0)
            monthMapper.deleteById(item.getMonthId());
        moneyService.validateBalances(familyId);
        moneyService.recordHousehold(familyId, item, ownedCategory(familyId, item.getCategoryId()).getName(), "delete");
    }

    /** 调用方已持家庭锁并开启借贷事务；此记录只进入存款统计。 */
    LedgerResponses.Household createLoanSavingRecord(String familyId, int userId, LoanEntity loan, Long projectId) {
        ensureCategories(familyId);
        CategoryEntity category = categoryMapper.selectOne(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId).eq(CategoryEntity::getType, "saving"));
        HouseholdMonthEntity month = month(familyId, loan.getDate(), true);
        HouseholdItemEntity item = new HouseholdItemEntity();
        item.setFamilyId(familyId);
        item.setMonthId(month.getId());
        item.setCategoryId(category.getId());
        item.setType("saving");
        item.setProjectId(projectId);
        item.setLoanId(loan.getId());
        item.setSavingTransfer(false);
        boolean lend = "lend".equals(loan.getDirection());
        item.setSavingEffect(lend ? "add" : "subtract");
        item.setAmountCents(loan.getAmountCents());
        String amount = java.math.BigDecimal.valueOf(loan.getAmountCents(), 2).stripTrailingZeros().toPlainString();
        item.setNote((lend ? "借给" : "收回") + loan.getName() + amount + "元");
        item.setCreatedBy(userId);
        item.setCreatedByName(loan.getCreatedByName());
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        itemMapper.insert(item);
        return household(item, loan.getDate().withDayOfMonth(1).toString(), moneyService.projectTypes(familyId));
    }

    /** 调用方已持家庭锁；账户金额仅由此收支流水计算，人情本身不再重复加减。 */
    LedgerResponses.Household saveGiftRecord(String familyId, GiftEntity gift) {
        boolean received = "received".equals(gift.getDirection());
        String type = received ? "income" : "expense";
        HouseholdItemEntity item = itemMapper.selectOne(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId).eq(HouseholdItemEntity::getGiftId, gift.getId()));
        HouseholdMonthEntity month = month(familyId, gift.getDate(), true);
        Long oldMonthId = item == null ? null : item.getMonthId();
        if (item == null) {
            item = new HouseholdItemEntity();
            item.setCreatedBy(gift.getCreatedBy());
            item.setCreatedByName(gift.getCreatedByName());
            item.setCreatedAt(gift.getCreatedAt());
        }
        item.setFamilyId(familyId);
        item.setMonthId(month.getId());
        item.setCategoryId(null);
        item.setType(type);
        item.setProjectId(gift.getProjectId());
        item.setGiftId(gift.getId());
        item.setRecordDate(gift.getDate());
        item.setAmountCents(gift.getAmountCents());
        item.setNote(gift.getNote());
        item.setUpdatedAt(Instant.now());
        if (item.getId() == null) itemMapper.insert(item);
        else itemMapper.updateById(item);
        if (oldMonthId != null && !oldMonthId.equals(month.getId())) deleteEmptyGiftMonth(oldMonthId);
        return household(item, gift.getDate().toString(), moneyService.projectTypes(familyId));
    }

    void deleteGiftRecord(String familyId, long giftId) {
        HouseholdItemEntity item = itemMapper.selectOne(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId).eq(HouseholdItemEntity::getGiftId, giftId));
        itemMapper.deleteById(item.getId());
        deleteEmptyGiftMonth(item.getMonthId());
    }

    private void deleteEmptyGiftMonth(long monthId) {
        if (itemMapper.selectCount(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getMonthId, monthId)) == 0) monthMapper.deleteById(monthId);
    }

    private void ensureCategories(String familyId) {
        if (categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getFamilyId, familyId)) > 0) return;
        String[][] defaults = {
                {"expense", "餐饮", "fork"}, {"expense", "房租", "home"}, {"expense", "房贷", "home"},
                {"expense", "还花呗", "creditcard"}, {"expense", "父母养老", "heart"}, {"expense", "日用", "shop"},
                {"expense", "其他", "wallet"}, {"income", "工资", "money"}, {"income", "额外收入", "money"},
                {"income", "其他收入", "wallet"}, {"saving", "存款", "wallet"}};
        for (int i = 0; i < defaults.length; i++) {
            CategoryEntity entity = new CategoryEntity();
            entity.setFamilyId(familyId); entity.setType(defaults[i][0]); entity.setName(defaults[i][1]);
            entity.setIcon(defaults[i][2]); entity.setActive(true); entity.setSortOrder(i);
            entity.setCreatedAt(Instant.now()); entity.setUpdatedAt(Instant.now()); categoryMapper.insert(entity);
        }
    }

    private HouseholdMonthEntity month(String familyId, LocalDate date, boolean create) {
        String value = date.toString().substring(0, 7);
        HouseholdMonthEntity entity = monthMapper.selectOne(new LambdaQueryWrapper<HouseholdMonthEntity>()
                .eq(HouseholdMonthEntity::getFamilyId, familyId).eq(HouseholdMonthEntity::getMonthValue, value));
        if (entity == null && create) {
            entity = new HouseholdMonthEntity(); entity.setFamilyId(familyId); entity.setMonthValue(value);
            entity.setCreatedAt(Instant.now()); entity.setUpdatedAt(Instant.now()); monthMapper.insert(entity);
        }
        return entity;
    }

    private CategoryEntity ownedCategory(String familyId, long id) {
        CategoryEntity entity = categoryMapper.selectOne(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getId, id).eq(CategoryEntity::getFamilyId, familyId));
        if (entity == null) throw new BusinessException(404, "分类不存在");
        return entity;
    }

    private String currentFamily(int userId, boolean write) {
        String id = familyMapper.currentFamily(userId);
        FamilyModels.Family family = id == null ? null : (write ? familyMapper.lockFamily(id) : familyMapper.family(id));
        if (family == null || family.getHiddenOwner() != null || !id.equals(familyMapper.currentFamily(userId)))
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        return id;
    }

    private void validateCategory(String type, String name) {
        if (!TYPES.contains(type) || name == null || name.trim().isEmpty() || name.trim().length() > 12)
            throw new BusinessException(400, "分类名称请输入1至12个字");
    }

    private void duplicate(String familyId, String type, String name, Long id) {
        if (categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getFamilyId, familyId)
                .eq(CategoryEntity::getType, type).eq(CategoryEntity::getName, name.trim())
                .isNull(CategoryEntity::getDeletedAt)
                .ne(id != null, CategoryEntity::getId, id)) > 0) throw new BusinessException(409, "这个分类名称已经存在");
    }

    private int nextSort(String familyId, String type) {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getFamilyId, familyId)
                .eq(CategoryEntity::getType, type)).stream().map(CategoryEntity::getSortOrder).max(Comparator.naturalOrder()).orElse(-1) + 1;
    }

    private LedgerResponses.Category category(CategoryEntity entity) {
        LedgerResponses.Category result = new LedgerResponses.Category();
        BeanUtils.copyProperties(entity, result);
        result.setDeleted(entity.getDeletedAt() != null);
        return result;
    }

    private LedgerResponses.Household household(HouseholdItemEntity entity, String date, Map<Long, String> projectTypes) {
        LedgerResponses.Household result = new LedgerResponses.Household(); BeanUtils.copyProperties(entity, result);
        result.setProjectType(entity.getProjectId() == null ? "cash" : projectTypes.get(entity.getProjectId()));
        result.setDate(entity.getRecordDate() == null ? date : entity.getRecordDate().toString());
        result.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy())); return result;
    }

    private String value(String text) { return text == null ? "" : text.trim(); }

    public record Snapshot(List<LedgerResponses.Category> categories, List<LedgerResponses.Household> records) {}
}
