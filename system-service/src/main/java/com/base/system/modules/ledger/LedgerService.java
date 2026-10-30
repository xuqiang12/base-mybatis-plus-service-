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
import com.base.system.modules.ledger.dto.LedgerQueries;
import com.base.system.modules.ledger.dto.LedgerReadResponses;
import com.base.common.result.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Map;
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
    private final JdbcTemplate jdbc;

    private static final String GIFT_FROM = " FROM relations.ledger_gift r "
            + "LEFT JOIN relations.ledger_book b ON b.id=r.book_id AND b.family_id=r.family_id "
            + "LEFT JOIN relations.ledger_money_project p ON p.id=r.project_id AND p.family_id=r.family_id ";
    private static final String GIFT_SELECT = "SELECT r.*,b.name AS book_name,p.name AS project_name";
    private static final String LOAN_FROM = " FROM relations.ledger_loan r "
            + "LEFT JOIN relations.ledger_money_project cash ON cash.family_id=r.family_id AND cash.cash_key='cash' "
            + "LEFT JOIN relations.ledger_money_project p ON p.id=COALESCE(r.project_id,cash.id) AND p.family_id=r.family_id ";
    private static final String LOAN_SELECT = "SELECT r.*,p.id AS resolved_project_id,p.name AS project_name";
    private static final String BOOK_FROM = " FROM relations.ledger_book b LEFT JOIN "
            + "(SELECT book_id,COUNT(*) AS record_count,SUM(amount_cents) AS received_cents FROM relations.ledger_gift "
            + "WHERE family_id=? AND direction='received' GROUP BY book_id) g ON g.book_id=b.id WHERE b.family_id=?";
    private static final String BOOK_SELECT = "SELECT b.*,COALESCE(g.record_count,0) AS record_count,COALESCE(g.received_cents,0) AS received_cents";
    private static final String LOAN_TOTALS = "COUNT(*) AS count,"
            + "COALESCE(SUM(CASE WHEN direction='lend' THEN amount_cents WHEN direction='receive' THEN -amount_cents ELSE 0 END),0) AS receivable_cents,"
            + "COALESCE(SUM(CASE WHEN direction='borrow' THEN amount_cents WHEN direction='pay' THEN -amount_cents ELSE 0 END),0) AS payable_cents";

    public LedgerReadResponses.Gift giftDetail(int userId, long id) {
        String familyId = currentFamily(userId, false);
        var rows = jdbc.query(GIFT_SELECT + GIFT_FROM + " WHERE r.family_id=? AND r.id=?", this::readGift, familyId, id);
        if (rows.isEmpty()) throw new BusinessException(404, "人情记录不存在");
        return rows.getFirst();
    }

    public PageResult<LedgerReadResponses.Gift> giftList(int userId, LedgerQueries.Records query) {
        return giftListForFamily(currentFamily(userId, false), query);
    }

    private PageResult<LedgerReadResponses.Gift> giftListForFamily(String familyId, LedgerQueries.Records query) {
        var args = new java.util.ArrayList<Object>();
        String where = recordWhere(familyId, query, args, true);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + GIFT_FROM + where, Long.class, args.toArray());
        args.add(query.getPageSize()); args.add(query.offset());
        var records = jdbc.query(GIFT_SELECT + GIFT_FROM + where + " ORDER BY r.date DESC,r.id DESC LIMIT ? OFFSET ?", this::readGift, args.toArray());
        return new PageResult<>(records, total, query.getPageNum(), query.getPageSize());
    }

    public LedgerReadResponses.GiftSummary giftSummary(int userId, LedgerQueries.Records query) {
        var args = new java.util.ArrayList<Object>();
        String where = recordWhere(currentFamily(userId, false), query, args, true);
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN r.direction='received' THEN r.amount_cents ELSE 0 END),0) AS received_cents,"
                + "COALESCE(SUM(CASE WHEN r.direction='sent' THEN r.amount_cents ELSE 0 END),0) AS sent_cents,"
                + "COUNT(CASE WHEN r.direction='received' THEN 1 END) AS received_count,COUNT(CASE WHEN r.direction='sent' THEN 1 END) AS sent_count,MAX(r.date) AS last_date"
                + " FROM relations.ledger_gift r" + where, (row, index) -> {
            var result = new LedgerReadResponses.GiftSummary();
            result.setReceivedCents(row.getLong("received_cents")); result.setSentCents(row.getLong("sent_cents"));
            result.setReceivedCount(row.getLong("received_count")); result.setSentCount(row.getLong("sent_count"));
            result.setBalanceCents(result.getReceivedCents() - result.getSentCents());
            result.setLastDate(row.getObject("last_date", java.time.LocalDate.class)); return result;
        }, args.toArray());
    }

    public PageResult<LedgerReadResponses.Loan> loanList(int userId, LedgerQueries.Records query) {
        return loanListForFamily(currentFamily(userId, false), query);
    }

    private PageResult<LedgerReadResponses.Loan> loanListForFamily(String familyId, LedgerQueries.Records query) {
        var args = new java.util.ArrayList<Object>();
        String where = recordWhere(familyId, query, args, false);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + LOAN_FROM + where, Long.class, args.toArray());
        args.add(query.getPageSize()); args.add(query.offset());
        var records = jdbc.query(LOAN_SELECT + LOAN_FROM + where + " ORDER BY r.date DESC,r.id DESC LIMIT ? OFFSET ?", this::readLoan, args.toArray());
        return new PageResult<>(records, total, query.getPageNum(), query.getPageSize());
    }

    public LedgerReadResponses.LoanTotals loanSummary(int userId, long friendId) {
        String familyId = currentFamily(userId, false);
        ownedFriend(familyId, friendId);
        return jdbc.queryForObject("SELECT " + LOAN_TOTALS + " FROM relations.ledger_loan WHERE family_id=? AND friend_id=?", (row, index) -> {
            var result = new LedgerReadResponses.LoanTotals(); result.setCount(row.getLong("count"));
            result.setReceivableCents(row.getLong("receivable_cents")); result.setPayableCents(row.getLong("payable_cents")); return result;
        }, familyId, friendId);
    }

    public PageResult<LedgerReadResponses.FriendSummary> loanSummaryList(int userId, LedgerQueries.LoanSummary query) {
        String familyId = currentFamily(userId, false);
        String grouped = " FROM (SELECT friend_id,MAX(name) AS snapshot_name,MAX(appellation) AS snapshot_appellation," + LOAN_TOTALS
                + " FROM relations.ledger_loan WHERE family_id=? GROUP BY friend_id) l "
                + "LEFT JOIN relations.ledger_friend f ON f.id=l.friend_id AND f.family_id=? WHERE l."
                + ("receivable".equals(query.getSide()) ? "receivable_cents" : "payable_cents") + ">0";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + grouped, Long.class, familyId, familyId);
        var rows = jdbc.query("SELECT l.*,f.id,f.family_id,COALESCE(f.name,l.snapshot_name) AS name,COALESCE(f.appellation,l.snapshot_appellation) AS appellation,"
                + "f.gender,f.remark,f.created_at,f.updated_at" + grouped + " ORDER BY l.friend_id DESC LIMIT ? OFFSET ?", (row, index) -> {
            var result = new LedgerReadResponses.FriendSummary(); result.setId(row.getObject("friend_id", Long.class));
            result.setFamilyId(familyId); result.setName(row.getString("name")); result.setAppellation(row.getString("appellation"));
            result.setGender(row.getObject("gender", Integer.class)); result.setRemark(row.getString("remark"));
            result.setReceivableCents(row.getLong("receivable_cents")); result.setPayableCents(row.getLong("payable_cents")); result.setCount(row.getLong("count"));
            if (row.getTimestamp("created_at") != null) result.setCreatedAt(row.getTimestamp("created_at").toInstant());
            if (row.getTimestamp("updated_at") != null) result.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
            return result;
        }, familyId, familyId, query.getPageSize(), query.offset());
        return new PageResult<>(rows, total, query.getPageNum(), query.getPageSize());
    }

    public LedgerReadResponses.Book bookDetail(int userId, long id) {
        String familyId = currentFamily(userId, false);
        var rows = jdbc.query(BOOK_SELECT + BOOK_FROM + " AND b.id=?", this::readBook, familyId, familyId, id);
        if (rows.isEmpty()) throw new BusinessException(404, "账簿不存在");
        return rows.getFirst();
    }

    public PageResult<LedgerReadResponses.Book> bookList(int userId, LedgerQueries.Page query) {
        return bookListForFamily(currentFamily(userId, false), query);
    }

    private PageResult<LedgerReadResponses.Book> bookListForFamily(String familyId, LedgerQueries.Page query) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_book WHERE family_id=?", Long.class, familyId);
        var rows = jdbc.query(BOOK_SELECT + BOOK_FROM + " ORDER BY b.created_at DESC,b.id DESC LIMIT ? OFFSET ?", this::readBook,
                familyId, familyId, query.getPageSize(), query.offset());
        return new PageResult<>(rows, total, query.getPageNum(), query.getPageSize());
    }

    private String recordWhere(String familyId, LedgerQueries.Records query, List<Object> args, boolean gift) {
        args.add(familyId); String where = " WHERE r.family_id=?";
        if (query.getFriendId() != null) { where += " AND r.friend_id=?"; args.add(query.getFriendId()); }
        if (gift && query.getBookId() != null) { where += " AND r.book_id=?"; args.add(query.getBookId()); }
        if (query.getDirection() != null) {
            Set<String> allowed = gift ? Set.of("received", "sent") : Set.of("lend", "borrow", "receive", "pay");
            if (!allowed.contains(query.getDirection())) throw new BusinessException(400, "请选择有效方向");
            where += " AND r.direction=?"; args.add(query.getDirection());
        }
        if (query.getPeriod() != null) { where += " AND r.date>=? AND r.date<?"; args.add(LedgerQueries.start(query.getPeriod())); args.add(LedgerQueries.end(query.getPeriod())); }
        return where;
    }

    public List<LedgerReadResponses.Recent> recent(int userId, int limit) {
        if (limit < 1 || limit > 20) throw new BusinessException(400, "最近记录条数须为 1 至 20");
        String familyId = currentFamily(userId, false);
        var records = new java.util.ArrayList<LedgerReadResponses.Recent>();
        for (var gift : jdbc.query(GIFT_SELECT + GIFT_FROM + " WHERE r.family_id=? ORDER BY COALESCE(r.updated_at,r.created_at) DESC,r.id DESC LIMIT ?", this::readGift, familyId, limit)) {
            var item = new LedgerReadResponses.Recent(); item.setKind("gift"); item.setProjectId(gift.getProjectId()); item.setId(gift.getId()); item.setTitle(gift.getName());
            item.setDate(gift.getDate().toString()); item.setAmountCents(gift.getAmountCents()); item.setDirection(gift.getDirection());
            item.setEventType(gift.getEventType()); item.setName(gift.getName()); item.setAppellation(gift.getAppellation()); item.setNote(gift.getNote());
            item.setCreatedAt(gift.getCreatedAt()); item.setUpdatedAt(gift.getUpdatedAt()); records.add(item);
        }
        for (var loan : jdbc.query(LOAN_SELECT + LOAN_FROM + " WHERE r.family_id=? ORDER BY COALESCE(r.updated_at,r.created_at) DESC,r.id DESC LIMIT ?", this::readLoan, familyId, limit)) {
            var item = new LedgerReadResponses.Recent(); item.setKind("loan"); item.setProjectId(loan.getProjectId()); item.setId(loan.getId()); item.setTitle(loan.getName());
            item.setDate(loan.getDate().toString()); item.setAmountCents(loan.getAmountCents()); item.setDirection(loan.getDirection());
            item.setName(loan.getName()); item.setAppellation(loan.getAppellation()); item.setNote(loan.getNote());
            item.setCreatedAt(loan.getCreatedAt()); item.setUpdatedAt(loan.getUpdatedAt()); records.add(item);
        }
        records.addAll(jdbc.query("SELECT h.id,h.type,h.amount_cents,h.note,h.project_id,h.created_at,h.updated_at,c.name AS category_name,"
                + "COALESCE(h.record_date,CAST(m.month_value || '-01' AS DATE)) AS effective_date FROM relations.ledger_household_item h "
                + "JOIN relations.ledger_household_month m ON m.id=h.month_id AND m.family_id=h.family_id "
                + "LEFT JOIN relations.ledger_category c ON c.id=h.category_id AND c.family_id=h.family_id "
                + "WHERE h.family_id=? AND h.gift_id IS NULL AND h.loan_id IS NULL ORDER BY COALESCE(h.updated_at,h.created_at) DESC,h.id DESC LIMIT ?", (row, index) -> {
            var item = new LedgerReadResponses.Recent(); item.setKind("household"); item.setProjectId(row.getObject("project_id", Long.class)); item.setId(row.getLong("id")); item.setType(row.getString("type"));
            item.setCategoryName(row.getString("category_name")); item.setTitle(row.getString("category_name")); item.setDate(row.getDate("effective_date").toString());
            item.setAmountCents(row.getLong("amount_cents")); item.setNote(row.getString("note")); item.setCreatedAt(row.getTimestamp("created_at").toInstant());
            item.setUpdatedAt(row.getTimestamp("updated_at").toInstant()); return item;
        }, familyId, limit));
        return records.stream().sorted(java.util.Comparator.<LedgerReadResponses.Recent, Instant>comparing(item -> item.getUpdatedAt() == null ? item.getCreatedAt() : item.getUpdatedAt()).reversed()
                .thenComparing(LedgerReadResponses.Recent::getId, java.util.Comparator.reverseOrder())
                .thenComparing(LedgerReadResponses.Recent::getKind)).limit(limit).toList();
    }

    private String historicalFamily(int userId, String familyId) {
        FamilyModels.Family family = familyMapper.family(familyId);
        if (family == null || !Integer.valueOf(userId).equals(family.getHiddenOwner())) throw new BusinessException(404, "历史家庭不存在");
        return familyId;
    }

    public PageResult<LedgerReadResponses.Gift> historyGifts(int userId, LedgerQueries.History query) {
        var records = new LedgerQueries.Records(); records.setPageNum(query.getPageNum()); records.setPageSize(query.getPageSize());
        return giftListForFamily(historicalFamily(userId, query.getFamilyId()), records);
    }
    public PageResult<LedgerReadResponses.Loan> historyLoans(int userId, LedgerQueries.History query) {
        var records = new LedgerQueries.Records(); records.setPageNum(query.getPageNum()); records.setPageSize(query.getPageSize());
        return loanListForFamily(historicalFamily(userId, query.getFamilyId()), records);
    }
    public PageResult<LedgerReadResponses.Book> historyBooks(int userId, LedgerQueries.History query) {
        return bookListForFamily(historicalFamily(userId, query.getFamilyId()), query);
    }
    public LedgerReadResponses.HouseholdPage historyHousehold(int userId, LedgerQueries.History query) {
        var records = new LedgerQueries.Records(); records.setPageNum(query.getPageNum()); records.setPageSize(query.getPageSize());
        return householdService.listForFamily(historicalFamily(userId, query.getFamilyId()), records);
    }
    public PageResult<FriendResponse> historyFriends(int userId, LedgerQueries.History query) {
        String familyId = historicalFamily(userId, query.getFamilyId());
        var page = friendMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<FriendEntity>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<FriendEntity>().eq(FriendEntity::getFamilyId, familyId).orderByDesc(FriendEntity::getCreatedAt).orderByDesc(FriendEntity::getId));
        return new PageResult<>(page.getRecords().stream().map(this::friend).toList(), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    private LedgerReadResponses.Book readBook(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var item = new LedgerReadResponses.Book(); item.setId(row.getLong("id")); item.setName(row.getString("name"));
        item.setDate(row.getObject("date", java.time.LocalDate.class)); item.setNote(row.getString("note"));
        item.setRecordCount(row.getLong("record_count")); item.setReceivedCents(row.getLong("received_cents")); return item;
    }

    private LedgerReadResponses.Gift readGift(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var item = new LedgerReadResponses.Gift();
        item.setDirection(row.getString("direction"));
        item.setName(row.getString("name"));
        item.setAppellation(row.getString("appellation"));
        item.setNote(row.getString("note"));
        item.setCreatedBy(row.getString("created_by"));
        item.setCreatedByName(row.getString("created_by_name"));
        item.setProjectName(row.getString("project_name"));
        item.setEventType(row.getString("event_type"));
        item.setBookName(row.getString("book_name"));
        item.setId(row.getObject("id", Long.class));
        item.setAmountCents(row.getObject("amount_cents", Long.class));
        item.setFriendId(row.getObject("friend_id", Long.class));
        item.setBookId(row.getObject("book_id", Long.class));
        item.setProjectId(row.getObject("project_id", Long.class));
        item.setDate(row.getObject("date", java.time.LocalDate.class));
        item.setCreatedAt(row.getTimestamp("created_at").toInstant()); item.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        return item;
    }

    private LedgerReadResponses.Loan readLoan(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var item = new LedgerReadResponses.Loan();
        item.setDirection(row.getString("direction"));
        item.setName(row.getString("name"));
        item.setAppellation(row.getString("appellation"));
        item.setNote(row.getString("note"));
        item.setCreatedBy(row.getString("created_by"));
        item.setCreatedByName(row.getString("created_by_name"));
        item.setProjectName(row.getString("project_name"));
        item.setId(row.getObject("id", Long.class));
        item.setAmountCents(row.getObject("amount_cents", Long.class));
        item.setFriendId(row.getObject("friend_id", Long.class));
        item.setProjectId(row.getObject("resolved_project_id", Long.class));
        item.setDate(row.getObject("date", java.time.LocalDate.class));
        item.setCreatedAt(row.getTimestamp("created_at").toInstant()); item.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        return item;
    }

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
        if (request.getFriendId() == null) {
            String name = request.getName().trim();
            String appellation = value(request.getAppellation());
            // 已持有家庭锁；首次提交和确认新建后的再次提交，都在写入前重新查询全部同名亲友。
            List<FriendEntity> sameName = friendMapper.selectList(new LambdaQueryWrapper<FriendEntity>()
                    .eq(FriendEntity::getFamilyId, familyId)
                    .apply("TRIM(name) = {0}", name)
                    .orderByAsc(FriendEntity::getId));
            List<FriendEntity> identical = sameName.stream()
                    .filter(item -> value(item.getAppellation()).equals(appellation)).toList();
            // 完全重复优先，allowSameName 只允许同名不同称谓，不能绕过完全重复检查。
            if (!identical.isEmpty() || (!sameName.isEmpty() && !request.isAllowSameName())) {
                List<FriendEntity> candidates = identical.isEmpty() ? sameName : identical;
                throw new BusinessException(409, "已有同名亲友，请确认本笔往来归属", Map.of(
                        "reason", "LOAN_FRIEND_CONFIRM_REQUIRED",
                        "friends", candidates.stream().map(this::friend).toList(),
                        "canCreateNew", identical.isEmpty()));
            }
        }
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
