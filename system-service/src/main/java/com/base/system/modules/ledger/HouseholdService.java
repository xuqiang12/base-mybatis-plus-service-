package com.base.system.modules.ledger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.ledger.dto.LedgerRequests;
import com.base.system.modules.ledger.dto.LedgerResponses;
import com.base.system.modules.ledger.entity.CategoryEntity;
import com.base.system.modules.ledger.entity.HouseholdItemEntity;
import com.base.system.modules.ledger.entity.HouseholdMonthEntity;
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

    /** 返回当前家庭分类和月度分类合计。 */
    @Transactional
    public Snapshot snapshot(int userId) {
        String familyId = currentFamily(userId, true);
        ensureCategories(familyId);
        return historySnapshot(familyId);
    }

    // 只由已校验归属的业务读取，历史家庭不会初始化分类或执行任何写入。
    Snapshot historySnapshot(String familyId) {
        List<LedgerResponses.Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId)
                .orderByAsc(CategoryEntity::getSortOrder)
                .orderByAsc(CategoryEntity::getId)).stream().map(this::category).toList();
        Map<Long, String> months = monthMapper.selectList(new LambdaQueryWrapper<HouseholdMonthEntity>()
                .eq(HouseholdMonthEntity::getFamilyId, familyId)).stream()
                .collect(Collectors.toMap(HouseholdMonthEntity::getId, month -> month.getMonthValue() + "-01"));
        List<LedgerResponses.Household> records = itemMapper.selectList(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId)
                .orderByDesc(HouseholdItemEntity::getId)).stream().map(item -> household(item, months.get(item.getMonthId()))).toList();
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
        entity.setUpdatedAt(Instant.now());
        categoryMapper.updateById(entity);
        return category(entity);
    }

    @Transactional
    public void deleteCategory(int userId, long id) {
        String familyId = currentFamily(userId, true);
        CategoryEntity entity = ownedCategory(familyId, id);
        if ("saving".equals(entity.getType())) throw new BusinessException(409, "存款分类不可删除");
        if (itemMapper.selectCount(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId)
                .eq(HouseholdItemEntity::getCategoryId, id)) > 0)
            throw new BusinessException(409, "分类已有记录，请改为停用");
        categoryMapper.deleteById(id);
    }

    @Transactional
    public List<LedgerResponses.Category> moveCategory(int userId, long id) {
        String familyId = currentFamily(userId, true);
        CategoryEntity entity = ownedCategory(familyId, id);
        if ("saving".equals(entity.getType())) throw new BusinessException(409, "存款分类不可排序");
        List<CategoryEntity> sameType = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getFamilyId, familyId).eq(CategoryEntity::getType, entity.getType())
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
                .eq(CategoryEntity::getFamilyId, familyId).orderByAsc(CategoryEntity::getSortOrder)
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
            if (!category.getActive()) throw new BusinessException(409, "分类已停用");
            Long projectId = moneyService.resolveProject(familyId, item.getProjectId());
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
            record.setNote(value(item.getNote()));
            record.setUpdatedAt(Instant.now());
            itemMapper.insert(record);
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
        LedgerResponses.Month result = new LedgerResponses.Month();
        result.setId(month == null ? null : month.getId());
        result.setMonth(date.toString().substring(0, 7));
        result.setItems(month == null ? List.of() : itemMapper.selectList(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getFamilyId, familyId).eq(HouseholdItemEntity::getMonthId, month.getId()))
                .stream().map(item -> household(item, date.toString())).toList());
        return result;
    }

    @Transactional
    public LedgerResponses.Household saveRecord(int userId, LedgerRequests.Household request) {
        String familyId = currentFamily(userId, true);
        Long projectId = moneyService.resolveProject(familyId, request.getProjectId());
        CategoryEntity category = ownedCategory(familyId, request.getCategoryId());
        if (!category.getType().equals(request.getType())) throw new BusinessException(400, "收支方向与分类不一致");
        if (!Boolean.TRUE.equals(category.getActive())) throw new BusinessException(409, "分类已停用");
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
        existing.setNote(value(request.getNote()));
        existing.setUpdatedAt(Instant.now());
        itemMapper.insert(existing);
        moneyService.validateBalances(familyId);
        return household(existing, request.getDate().withDayOfMonth(1).toString());
    }

    @Transactional
    public LedgerResponses.Household updateRecord(int userId, LedgerRequests.HouseholdUpdate request) {
        String familyId = currentFamily(userId, true);
        HouseholdItemEntity existing = itemMapper.selectOne(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getId, request.getId()).eq(HouseholdItemEntity::getFamilyId, familyId));
        if (existing == null) throw new BusinessException(404, "收支记录不存在");
        Long projectId = moneyService.resolveProject(familyId, request.getProjectId());
        CategoryEntity category = ownedCategory(familyId, request.getCategoryId());
        if (!category.getType().equals(request.getType())) throw new BusinessException(400, "收支方向与分类不一致");
        HouseholdMonthEntity month = month(familyId, request.getDate(), true);
        existing.setMonthId(month.getId());
        existing.setCategoryId(category.getId());
        existing.setType(request.getType());
        existing.setAmountCents(request.getAmountCents());
        existing.setProjectId(projectId);
        existing.setNote(value(request.getNote()));
        existing.setUpdatedAt(Instant.now());
        itemMapper.updateById(existing);
        moneyService.validateBalances(familyId);
        return household(existing, request.getDate().withDayOfMonth(1).toString());
    }

    @Transactional
    public void deleteRecord(int userId, long id) {
        String familyId = currentFamily(userId, true);
        HouseholdItemEntity item = itemMapper.selectOne(new LambdaQueryWrapper<HouseholdItemEntity>()
                .eq(HouseholdItemEntity::getId, id).eq(HouseholdItemEntity::getFamilyId, familyId));
        if (item == null) throw new BusinessException(404, "收支记录不存在");
        itemMapper.deleteById(id);
        if (itemMapper.selectCount(new LambdaQueryWrapper<HouseholdItemEntity>().eq(HouseholdItemEntity::getMonthId, item.getMonthId())) == 0)
            monthMapper.deleteById(item.getMonthId());
        moneyService.validateBalances(familyId);
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
                .ne(id != null, CategoryEntity::getId, id)) > 0) throw new BusinessException(409, "这个分类名称已经存在");
    }

    private int nextSort(String familyId, String type) {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getFamilyId, familyId)
                .eq(CategoryEntity::getType, type)).stream().map(CategoryEntity::getSortOrder).max(Comparator.naturalOrder()).orElse(-1) + 1;
    }

    private LedgerResponses.Category category(CategoryEntity entity) {
        LedgerResponses.Category result = new LedgerResponses.Category(); BeanUtils.copyProperties(entity, result); return result;
    }

    private LedgerResponses.Household household(HouseholdItemEntity entity, String date) {
        LedgerResponses.Household result = new LedgerResponses.Household(); BeanUtils.copyProperties(entity, result);
        result.setDate(date); result.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy())); return result;
    }

    private String value(String text) { return text == null ? "" : text.trim(); }

    public record Snapshot(List<LedgerResponses.Category> categories, List<LedgerResponses.Household> records) {}
}
