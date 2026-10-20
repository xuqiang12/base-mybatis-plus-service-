package com.base.system.modules.friend;

import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.friend.dto.FriendCreateRequest;
import com.base.system.modules.friend.dto.FriendResponse;
import com.base.system.modules.friend.entity.FriendEntity;
import com.base.system.modules.friend.mapper.FriendMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final FriendMapper friendMapper;
    private final FamilyMapper familyMapper;

    @Transactional
    public FriendResponse create(int userId, FriendCreateRequest request) {
        // 1. 根据当前登录用户，查找所属家庭。
        String familyId = familyMapper.currentFamily(userId);
        if (familyId == null) {
            throw new BusinessException(409, "未找到所属家庭，请刷新后重试");
        }

        // 2. 锁定家庭记录，并确认家庭归属仍然有效。
        FamilyModels.Family family = familyMapper.lockFamily(familyId);
        if (family == null
                || family.getHiddenOwner() != null
                || !familyId.equals(familyMapper.currentFamily(userId))) {
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        }

        // 3. 批量复制请求中的姓名、称谓、性别和备注。
        FriendEntity friend = new FriendEntity();
        BeanUtils.copyProperties(request, friend);

        // 家庭编号由后端确定。
        friend.setFamilyId(familyId);

        // 4. 设置时间。
        Instant now = Instant.now();
        friend.setCreatedAt(now);
        friend.setUpdatedAt(now);

        // 5. 保存，数据库生成的编号会回填到 friend.id。
        friendMapper.insert(friend);

        // 6. 批量复制保存结果，返回给前端。
        FriendResponse response = new FriendResponse();
        BeanUtils.copyProperties(friend, response);
        return response;

    }


}
