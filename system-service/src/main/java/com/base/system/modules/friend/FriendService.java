package com.base.system.modules.friend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.common.exception.BusinessException;
import com.base.common.result.PageResult;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.friend.dto.FriendCreateRequest;
import com.base.system.modules.friend.dto.FriendQueryRequest;
import com.base.system.modules.friend.dto.FriendResponse;
import com.base.system.modules.friend.dto.FriendUpdateRequest;
import com.base.system.modules.friend.entity.FriendEntity;
import com.base.system.modules.friend.mapper.FriendMapper;
import com.base.system.modules.ledger.entity.GiftEntity;
import com.base.system.modules.ledger.entity.LoanEntity;
import com.base.system.modules.ledger.mapper.GiftMapper;
import com.base.system.modules.ledger.mapper.LoanMapper;
import lombok.RequiredArgsConstructor;
import com.base.system.modules.ledger.dto.LedgerQueries;
import com.base.system.modules.ledger.dto.LedgerReadResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 亲友业务：接收 Controller 传入的登录用户和请求参数，通过 Mapper 操作数据库。
 * 所有亲友操作都限定在当前登录用户所属的家庭内。
 */
@Service
@RequiredArgsConstructor
public class FriendService {
    private final FriendMapper friendMapper;
    private final FamilyMapper familyMapper;
    private final GiftMapper giftMapper;
    private final LoanMapper loanMapper;
    private final JdbcTemplate jdbc;

    /**
     * 新增亲友；事务保证本次数据库写入整体提交或回滚。
     */
    @Transactional
    public FriendResponse create(int userId, FriendCreateRequest request) {
        // 1. 根据登录用户查所属家庭，家庭编号由后端确定。
        String familyId = currentFamily(userId);

        // 2. 将请求里的姓名、称谓、性别、备注复制到数据库对象，再补上家庭编号。
        FriendEntity friend = new FriendEntity();
        BeanUtils.copyProperties(request, friend);
        friend.setFamilyId(familyId);
        // 3. 新增时，创建时间和修改时间使用同一个时间。
        Instant now = Instant.now();
        friend.setCreatedAt(now);
        friend.setUpdatedAt(now);
        // 4. 保存亲友；数据库生成的自增编号会回填到 friend.id。
        friendMapper.insert(friend);
        // 5. 转成返回对象，交给 Controller 返回前端。
        return response(friend);
    }

    /**
     * 分页查询当前家庭的亲友，可按姓名和性别筛选。
     */
    public PageResult<FriendResponse> list(int userId, FriendQueryRequest query) {
        // 1. 先确定查询范围，不能查到其他家庭的亲友。
        String familyId = currentFamily(userId);
        // 2. 用 MyBatis-Plus 组装 SQL 条件；FriendEntity::getName 等写法用于指定字段。
        LambdaQueryWrapper<FriendEntity> where = new LambdaQueryWrapper<FriendEntity>()
                // eq 表示等于：所属家庭必须匹配。
                .eq(FriendEntity::getFamilyId, familyId)
                // like 表示模糊匹配；第一个参数为 true 时才添加条件，空姓名不筛选。
                .like(query.getName() != null && !query.getName().isBlank(), FriendEntity::getName, query.getName())
                // 传了性别才添加筛选条件；0 也属于有效的筛选值。
                .eq(query.getGender() != null, FriendEntity::getGender, query.getGender())
                // 创建时间倒序；时间相同时按编号倒序，保证排序稳定。
                .orderByDesc(FriendEntity::getCreatedAt)
                .orderByDesc(FriendEntity::getId);
        // 3. 按页码、每页条数执行分页查询，结果包含当前页记录和符合条件的总条数。
        Page<FriendEntity> page = friendMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), where);
        // 4. map(this::response) 将每条记录转成返回对象，toList() 将结果收集成列表。
        // PageResult 按“列表、总条数、当前页码、每页条数”的顺序封装分页结果。
        return new PageResult<>(page.getRecords().stream().map(this::response).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 只聚合当前页亲友，以两次分组 SQL 取代逐亲友查询。 */
    public PageResult<LedgerReadResponses.FriendSummary> summaryList(int userId, LedgerQueries.Friends query) {
        var request = new FriendQueryRequest(); request.setName(query.getName()); request.setPageNum(query.getPageNum()); request.setPageSize(query.getPageSize());
        var page = list(userId, request);
        var rows = page.list().stream().map(friend -> {
            var result = new LedgerReadResponses.FriendSummary(); BeanUtils.copyProperties(friend, result); return result;
        }).toList();
        if (rows.isEmpty()) return new PageResult<>(rows, page.total(), page.pageNum(), page.pageSize());
        String familyId = rows.getFirst().getFamilyId();
        var args = new java.util.ArrayList<Object>(); args.add(familyId); rows.forEach(friend -> args.add(friend.getId()));
        String ids = String.join(",", java.util.Collections.nCopies(rows.size(), "?"));
        var byId = rows.stream().collect(java.util.stream.Collectors.toMap(FriendResponse::getId, friend -> friend));
        jdbc.query("SELECT friend_id,"
                + "SUM(CASE WHEN direction='received' THEN amount_cents ELSE 0 END) AS received_cents,"
                + "SUM(CASE WHEN direction='sent' THEN amount_cents ELSE 0 END) AS sent_cents,"
                + "COUNT(CASE WHEN direction='received' THEN 1 END) AS received_count,COUNT(CASE WHEN direction='sent' THEN 1 END) AS sent_count,MAX(date) AS last_date "
                + "FROM relations.ledger_gift WHERE family_id=? AND friend_id IN (" + ids + ") GROUP BY friend_id", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
            var friend = byId.get(row.getLong("friend_id")); friend.setReceivedCents(row.getLong("received_cents")); friend.setSentCents(row.getLong("sent_cents"));
            friend.setReceivedCount(row.getLong("received_count")); friend.setSentCount(row.getLong("sent_count")); friend.setBalanceCents(friend.getReceivedCents() - friend.getSentCents());
            friend.setLastDate(row.getObject("last_date", java.time.LocalDate.class));
        }, args.toArray());
        jdbc.query("SELECT friend_id,COUNT(*) AS count,"
                + "SUM(CASE WHEN direction='lend' THEN amount_cents WHEN direction='receive' THEN -amount_cents ELSE 0 END) AS receivable_cents,"
                + "SUM(CASE WHEN direction='borrow' THEN amount_cents WHEN direction='pay' THEN -amount_cents ELSE 0 END) AS payable_cents "
                + "FROM relations.ledger_loan WHERE family_id=? AND friend_id IN (" + ids + ") GROUP BY friend_id", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
            var friend = byId.get(row.getLong("friend_id")); friend.setReceivableCents(row.getLong("receivable_cents")); friend.setPayableCents(row.getLong("payable_cents")); friend.setCount(row.getLong("count"));
        }, args.toArray());
        return new PageResult<>(rows, page.total(), page.pageNum(), page.pageSize());
    }

    /**
     * 查询详情：先确认亲友属于当前家庭，再转换为返回对象。
     */
    public FriendResponse detail(int userId, long id) {
        return response(ownedFriend(userId, id));
    }

    /**
     * 修改亲友，使用提交的表单值更新姓名、称谓、性别和备注。
     */
    @Transactional
    public FriendResponse update(int userId, FriendUpdateRequest request) {
        // 1. 同时检查亲友编号和家庭归属，不能仅凭编号修改其他家庭的数据。
        FriendEntity friend = ownedFriend(userId, request.getId());
        // 2. 更新允许修改的资料；编号、所属家庭和创建时间保持原值。
        // 这里按整份表单更新，未传的选填字段也会使用请求对象中的默认值。
        friend.setName(request.getName());
        friend.setAppellation(request.getAppellation());
        friend.setGender(request.getGender());
        friend.setRemark(request.getRemark());
        friend.setUpdatedAt(Instant.now());
        // 3. 按主键保存修改，并返回修改后的资料。
        friendMapper.updateById(friend);
        return response(friend);
    }

    /**
     * 删除亲友：先检查家庭归属，再从数据库删除记录。
     */
    @Transactional
    public String delete(int userId, long id) {
        FriendEntity friend = ownedFriend(userId, id);
        var loans = loanMapper.selectList(new LambdaQueryWrapper<LoanEntity>()
                .eq(LoanEntity::getFamilyId, friend.getFamilyId())
                .eq(LoanEntity::getFriendId, friend.getId()));
        long receivable = loans.stream().mapToLong(item -> "lend".equals(item.getDirection())
                ? item.getAmountCents() : "receive".equals(item.getDirection()) ? -item.getAmountCents() : 0).sum();
        long payable = loans.stream().mapToLong(item -> "borrow".equals(item.getDirection())
                ? item.getAmountCents() : "pay".equals(item.getDirection()) ? -item.getAmountCents() : 0).sum();
        if (receivable > 0 || payable > 0)
            throw new BusinessException(409, "该亲友有未结清借贷，请先结清");
        giftMapper.update(null, new LambdaUpdateWrapper<GiftEntity>()
                .eq(GiftEntity::getFamilyId, friend.getFamilyId())
                .eq(GiftEntity::getFriendId, friend.getId())
                .set(GiftEntity::getFriendId, null)
                .set(GiftEntity::getUpdatedAt, Instant.now()));
        loanMapper.update(null, new LambdaUpdateWrapper<LoanEntity>()
                .eq(LoanEntity::getFamilyId, friend.getFamilyId())
                .eq(LoanEntity::getFriendId, friend.getId())
                .set(LoanEntity::getFriendId, null)
                .set(LoanEntity::getUpdatedAt, Instant.now()));
        friendMapper.deleteById(friend.getId());
        return "亲友删除成功";
    }

    /**
     * 查找当前家庭的指定亲友，供详情、修改、删除共用。
     */
    private FriendEntity ownedFriend(int userId, long id) {
        String familyId = currentFamily(userId);
        // 两个条件必须同时满足：亲友编号相同，而且属于当前用户的家庭。
        FriendEntity friend = friendMapper.selectOne(new LambdaQueryWrapper<FriendEntity>()
                .eq(FriendEntity::getId, id)
                .eq(FriendEntity::getFamilyId, familyId));
        if (friend == null) {
            // 编号不存在、或亲友属于其他家庭时，都按当前家庭中不存在处理。
            throw new BusinessException(404, "亲友不存在");
        }
        return friend;
    }

    /**
     * 查找并检查登录用户所属的家庭，供各个亲友操作共用。
     */
    private String currentFamily(int userId) {
        // 1. 从家庭成员表查出用户当前所属的家庭编号。
        String familyId = familyMapper.currentFamily(userId);
        if (familyId == null) {
            throw new BusinessException(409, "未找到所属家庭，请刷新后重试");
        }
        // 2. 使用 SELECT ... FOR UPDATE 查询家庭；在写操作事务中，行锁会保留到事务结束。
        FamilyModels.Family family = familyMapper.lockFamily(familyId);
        // 3. 检查家庭是否有效，并再次确认用户归属没有变化。
        // hiddenOwner 非空表示家庭处于隐藏状态，不作为当前正常家庭使用。
        if (family == null
                || family.getHiddenOwner() != null
                || !familyId.equals(familyMapper.currentFamily(userId))) {
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        }
        return familyId;
    }

    /**
     * 将数据库对象转成返回对象，供新增、列表、详情、修改共用。
     */
    private FriendResponse response(FriendEntity friend) {
        FriendResponse response = new FriendResponse();
        // copyProperties 按同名且类型兼容的属性复制数据，不会执行数据库操作。
        BeanUtils.copyProperties(friend, response);
        return response;
    }

}
