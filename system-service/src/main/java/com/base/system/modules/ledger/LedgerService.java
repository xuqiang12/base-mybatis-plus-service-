package com.base.system.modules.ledger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.friend.dto.FriendResponse;
import com.base.system.modules.friend.entity.FriendEntity;
import com.base.system.modules.friend.mapper.FriendMapper;
import com.base.system.modules.ledger.dto.LedgerRequests;
import com.base.system.modules.ledger.dto.LedgerResponses;
import com.base.system.modules.ledger.entity.BookEntity;
import com.base.system.modules.ledger.entity.GiftEntity;
import com.base.system.modules.ledger.entity.LoanEntity;
import com.base.system.modules.ledger.mapper.BookMapper;
import com.base.system.modules.ledger.mapper.GiftMapper;
import com.base.system.modules.ledger.mapper.LoanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 账簿和人情流水业务，所有对象都限定在当前家庭。 */
@Service
@RequiredArgsConstructor
public class LedgerService {
    private static final Set<String> EVENTS = Set.of(
            "wedding", "baby", "visit", "new-year", "birthday", "school",
            "house", "longevity", "funeral", "other");
    private final BookMapper bookMapper;
    private final GiftMapper giftMapper;
    private final LoanMapper loanMapper;
    private final HouseholdService householdService;
    private final FriendMapper friendMapper;
    private final FamilyMapper familyMapper;
    private final MoneyService moneyService;

    /** 返回当前家庭的账簿、人情和亲友。 */
    @Transactional
    public LedgerResponses.DataSet data(int userId) {
        String familyId = currentFamily(userId, false);
        return dataForFamily(familyId, householdService.snapshot(familyId));
    }

    /** 只有历史家庭的原账号可以读取；所有写接口仍只操作当前家庭。 */
    @Transactional(readOnly = true)
    public LedgerResponses.DataSet history(int userId, String familyId) {
        FamilyModels.Family family = familyMapper.family(familyId);
        if (family == null || !Integer.valueOf(userId).equals(family.getHiddenOwner()))
            throw new BusinessException(404, "历史家庭不存在");
        return dataForFamily(familyId, householdService.historySnapshot(familyId));
    }

    private LedgerResponses.DataSet dataForFamily(String familyId, HouseholdService.Snapshot household) {
        LedgerResponses.DataSet result = new LedgerResponses.DataSet();
        result.setBooks(bookMapper.selectList(new LambdaQueryWrapper<BookEntity>()
                .eq(BookEntity::getFamilyId, familyId)
                .orderByDesc(BookEntity::getCreatedAt)).stream().map(this::book).toList());
        result.setFriends(friendMapper.selectList(new LambdaQueryWrapper<FriendEntity>()
                .eq(FriendEntity::getFamilyId, familyId)
                .orderByDesc(FriendEntity::getCreatedAt)).stream().map(this::friend).toList());
        result.setRecords(giftMapper.selectList(new LambdaQueryWrapper<GiftEntity>()
                .eq(GiftEntity::getFamilyId, familyId)
                .orderByDesc(GiftEntity::getDate)
                .orderByDesc(GiftEntity::getId)).stream().map(this::gift).toList());
        List<LoanEntity> loans = loanMapper.selectList(new LambdaQueryWrapper<LoanEntity>()
                .eq(LoanEntity::getFamilyId, familyId)
                .orderByDesc(LoanEntity::getDate)
                .orderByDesc(LoanEntity::getId));
        Long defaultProjectId = loans.stream().anyMatch(item -> item.getProjectId() == null)
                ? moneyService.resolveProject(familyId, null) : null;
        result.setLoanRecords(loans.stream().map(item -> loan(item, defaultProjectId)).toList());
        result.setHouseholdCategories(household.categories());
        result.setHouseholdRecords(household.records());
        return result;
    }

    /** 新增账簿。 */
    @Transactional
    public LedgerResponses.Book createBook(int userId, LedgerRequests.Book request) {
        String familyId = currentFamily(userId, true);
        BookEntity entity = new BookEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setFamilyId(familyId);
        entity.setNote(value(request.getNote()));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        bookMapper.insert(entity);
        return book(entity);
    }

    /** 修改账簿。 */
    @Transactional
    public LedgerResponses.Book updateBook(int userId, LedgerRequests.BookUpdate request) {
        String familyId = currentFamily(userId, true);
        BookEntity entity = ownedBook(familyId, request.getId());
        entity.setName(request.getName().trim());
        entity.setDate(request.getDate());
        entity.setNote(value(request.getNote()));
        entity.setUpdatedAt(Instant.now());
        bookMapper.updateById(entity);
        return book(entity);
    }

    /** 删除账簿并解除流水归属。 */
    @Transactional
    public void deleteBook(int userId, LedgerRequests.BookDelete request) {
        String familyId = currentFamily(userId, true);
        BookEntity entity = ownedBook(familyId, request.getId());
        giftMapper.update(null, new LambdaUpdateWrapper<GiftEntity>()
                .eq(GiftEntity::getFamilyId, familyId)
                .eq(GiftEntity::getBookId, entity.getId())
                .set(GiftEntity::getBookId, null)
                .set(GiftEntity::getUpdatedAt, Instant.now()));
        bookMapper.deleteById(entity.getId());
    }

    /** 新增人情；未选择亲友时和亲友档案在同一事务创建。 */
    @Transactional
    public LedgerResponses.CreatedGift createGift(int userId, LedgerRequests.Gift request) {
        String familyId = currentFamily(userId, true);
        validateGift(request);
        Long projectId = moneyService.resolveProject(familyId, request.getProjectId());
        FriendEntity friend = request.getFriendId() == null
                ? createFriend(familyId, request.getName(), request.getAppellation())
                : ownedFriend(familyId, request.getFriendId());
        if (request.getBookId() != null) ownedBook(familyId, request.getBookId());
        GiftEntity entity = new GiftEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setFamilyId(familyId);
        entity.setFriendId(friend.getId());
        entity.setName(request.getName().trim());
        entity.setAppellation(value(request.getAppellation()));
        entity.setNote(value(request.getNote()));
        entity.setBookId("received".equals(request.getDirection()) ? request.getBookId() : null);
        entity.setProjectId(projectId);
        entity.setCreatedBy(userId);
        entity.setCreatedByName(value(familyMapper.userName(userId)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        giftMapper.insert(entity);
        LedgerResponses.Household household = householdService.saveGiftRecord(familyId, entity);
        moneyService.validateBalances(familyId);
        moneyService.recordGift(familyId, entity, "create");
        LedgerResponses.CreatedGift result = new LedgerResponses.CreatedGift();
        result.setRecord(gift(entity));
        result.setHouseholdRecord(household);
        result.setFriend(request.getFriendId() == null ? friend(friend) : null);
        return result;
    }

    /** 修改人情及其关联收支；历史未入账人情仍只修改人情统计。 */
    @Transactional
    public LedgerResponses.CreatedGift updateGift(int userId, LedgerRequests.GiftUpdate request) {
        String familyId = currentFamily(userId, true);
        GiftEntity entity = ownedGift(familyId, request.getId());
        validateGift(request);
        boolean linked = entity.getProjectId() != null;
        Long projectId = linked ? moneyService.resolveProject(familyId, request.getProjectId()) : null;
        FriendEntity friend = null;
        if (request.getFriendId() != null) {
            friend = ownedFriend(familyId, request.getFriendId());
        } else if (entity.getFriendId() != null || !entity.getName().equals(request.getName().trim())
                || !value(entity.getAppellation()).equals(value(request.getAppellation()))) {
            friend = createFriend(familyId, request.getName(), request.getAppellation());
        }
        if (request.getBookId() != null) ownedBook(familyId, request.getBookId());
        entity.setEventType(request.getEventType());
        entity.setDirection(request.getDirection());
        entity.setDate(request.getDate());
        entity.setName(request.getName().trim());
        entity.setAppellation(value(request.getAppellation()));
        entity.setAmountCents(request.getAmountCents());
        entity.setNote(value(request.getNote()));
        entity.setBookId("received".equals(request.getDirection()) ? request.getBookId() : null);
        entity.setFriendId(friend == null ? null : friend.getId());
        entity.setProjectId(projectId);
        entity.setUpdatedAt(Instant.now());
        giftMapper.updateById(entity);
        LedgerResponses.CreatedGift result = new LedgerResponses.CreatedGift();
        if (linked) {
            LedgerResponses.Household household = householdService.saveGiftRecord(familyId, entity);
            moneyService.validateBalances(familyId);
            moneyService.recordGift(familyId, entity, "update");
            result.setHouseholdRecord(household);
        }
        result.setRecord(gift(entity));
        result.setFriend(request.getFriendId() == null && friend != null ? friend(friend) : null);
        return result;
    }

    @Transactional
    public void deleteGift(int userId, long id) {
        String familyId = currentFamily(userId, true);
        GiftEntity entity = ownedGift(familyId, id);
        if (entity.getProjectId() != null) householdService.deleteGiftRecord(familyId, id);
        giftMapper.deleteById(id);
        if (entity.getProjectId() != null) {
            moneyService.validateBalances(familyId);
            moneyService.recordGift(familyId, entity, "delete");
        }
    }

    private GiftEntity ownedGift(String familyId, long id) {
        GiftEntity entity = giftMapper.selectOne(new LambdaQueryWrapper<GiftEntity>()
                .eq(GiftEntity::getId, id).eq(GiftEntity::getFamilyId, familyId));
        if (entity == null) throw new BusinessException(404, "人情记录不存在或已删除");
        return entity;
    }

    /** 新增借贷流水，并在事务内校验收款或归还额度。 */
    @Transactional
    public LedgerResponses.CreatedLoan createLoan(int userId, LedgerRequests.Loan request) {
        String familyId = currentFamily(userId, true);
        if (!Set.of("lend", "borrow", "receive", "pay").contains(request.getDirection()))
            throw new BusinessException(400, "借贷操作无效");
        if (request.getAmountCents() == null || request.getAmountCents() <= 0)
            throw new BusinessException(400, "金额必须大于0");
        if (("receive".equals(request.getDirection()) || "pay".equals(request.getDirection()))
                && request.getFriendId() == null)
            throw new BusinessException(400, "收款或归还必须选择亲友");
        Long projectId = moneyService.resolveProject(familyId, request.getProjectId());
        FriendEntity friend = request.getFriendId() == null
                ? createFriend(familyId, request.getName(), request.getAppellation())
                : ownedFriend(familyId, request.getFriendId());
        if ("receive".equals(request.getDirection()) || "pay".equals(request.getDirection())) {
            long balance = loanMapper.selectList(new LambdaQueryWrapper<LoanEntity>()
                    .eq(LoanEntity::getFamilyId, familyId)
                    .eq(LoanEntity::getFriendId, friend.getId())).stream()
                    .mapToLong(item -> {
                        if ("receive".equals(request.getDirection()))
                            return "lend".equals(item.getDirection()) ? item.getAmountCents()
                                    : "receive".equals(item.getDirection()) ? -item.getAmountCents() : 0;
                        return "borrow".equals(item.getDirection()) ? item.getAmountCents()
                                : "pay".equals(item.getDirection()) ? -item.getAmountCents() : 0;
                    }).sum();
            if (request.getAmountCents() > balance)
                throw new BusinessException(409, "receive".equals(request.getDirection())
                        ? "不能超过当前待收金额" : "不能超过当前待还金额");
        }
        LoanEntity entity = new LoanEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setFamilyId(familyId);
        entity.setFriendId(friend.getId());
        entity.setProjectId(projectId);
        entity.setName(request.getName().trim());
        entity.setAppellation(value(request.getAppellation()));
        entity.setNote(value(request.getNote()));
        entity.setCreatedBy(userId);
        entity.setCreatedByName(value(familyMapper.userName(userId)));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        loanMapper.insert(entity);
        LedgerResponses.Household householdRecord = null;
        if ("lend".equals(entity.getDirection()) || "receive".equals(entity.getDirection()))
            householdRecord = householdService.createLoanSavingRecord(familyId, userId, entity, projectId);
        moneyService.validateBalances(familyId);
        moneyService.recordLoan(familyId, entity);
        LedgerResponses.CreatedLoan result = new LedgerResponses.CreatedLoan();
        result.setRecord(loan(entity));
        result.setHouseholdRecord(householdRecord);
        result.setFriend(request.getFriendId() == null ? friend(friend) : null);
        return result;
    }

    private void validateGift(LedgerRequests.Gift request) {
        if (!EVENTS.contains(request.getEventType())) throw new BusinessException(400, "事件类型无效");
        if (!Set.of("received", "sent").contains(request.getDirection()))
            throw new BusinessException(400, "收送方向无效");
        if (request.getAmountCents() == null || request.getAmountCents() <= 0)
            throw new BusinessException(400, "金额必须大于0");
    }

    private FriendEntity createFriend(String familyId, String name, String appellation) {
        FriendEntity friend = new FriendEntity();
        friend.setFamilyId(familyId);
        friend.setName(name.trim());
        friend.setAppellation(value(appellation));
        friend.setGender(0);
        friend.setRemark("");
        Instant now = Instant.now();
        friend.setCreatedAt(now);
        friend.setUpdatedAt(now);
        friendMapper.insert(friend);
        return friend;
    }

    private BookEntity ownedBook(String familyId, long id) {
        BookEntity entity = bookMapper.selectOne(new LambdaQueryWrapper<BookEntity>()
                .eq(BookEntity::getId, id).eq(BookEntity::getFamilyId, familyId));
        if (entity == null) throw new BusinessException(404, "账簿不存在");
        return entity;
    }

    private FriendEntity ownedFriend(String familyId, long id) {
        FriendEntity entity = friendMapper.selectOne(new LambdaQueryWrapper<FriendEntity>()
                .eq(FriendEntity::getId, id).eq(FriendEntity::getFamilyId, familyId));
        if (entity == null) throw new BusinessException(404, "亲友不存在");
        return entity;
    }

    private String currentFamily(int userId, boolean write) {
        String familyId = familyMapper.currentFamily(userId);
        if (familyId == null) throw new BusinessException(409, "未找到所属家庭，请刷新后重试");
        FamilyModels.Family family = write ? familyMapper.lockFamily(familyId) : familyMapper.family(familyId);
        if (family == null || family.getHiddenOwner() != null || !familyId.equals(familyMapper.currentFamily(userId)))
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        return familyId;
    }

    private LedgerResponses.Book book(BookEntity entity) {
        LedgerResponses.Book result = new LedgerResponses.Book();
        BeanUtils.copyProperties(entity, result);
        return result;
    }

    private LedgerResponses.Gift gift(GiftEntity entity) {
        LedgerResponses.Gift result = new LedgerResponses.Gift();
        BeanUtils.copyProperties(entity, result);
        result.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        return result;
    }

    private LedgerResponses.Loan loan(LoanEntity entity) {
        return loan(entity, entity.getProjectId() == null
                ? moneyService.resolveProject(entity.getFamilyId(), null) : null);
    }

    private LedgerResponses.Loan loan(LoanEntity entity, Long defaultProjectId) {
        LedgerResponses.Loan result = new LedgerResponses.Loan();
        BeanUtils.copyProperties(entity, result);
        if (result.getProjectId() == null) result.setProjectId(defaultProjectId);
        result.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        return result;
    }

    private FriendResponse friend(FriendEntity entity) {
        FriendResponse result = new FriendResponse();
        BeanUtils.copyProperties(entity, result);
        return result;
    }

    private String value(String text) { return text == null ? "" : text.trim(); }
}
