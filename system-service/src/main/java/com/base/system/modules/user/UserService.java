package com.base.system.modules.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.common.exception.BusinessException;
import com.base.common.result.PageResult;
import com.base.security.LoginUser;
import com.base.system.modules.auth.mapper.AccessMapper;
import com.base.system.modules.role.mapper.RoleMapper;
import com.base.system.modules.user.dto.UserRequests;
import com.base.system.modules.user.dto.UserResponse;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

// 用户维护、密码散列、角色关联和软删除。
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final AccessMapper access;
    private final PasswordEncoder passwords;

    // 按筛选条件分页，只返回允许暴露的字段。
    public PageResult<UserResponse> list(UserRequests.ListQuery query) {
        var where = new LambdaQueryWrapper<UserEntity>()
                .eq(!Boolean.TRUE.equals(query.includeDeleted()), UserEntity::getIsDeleted, false)
                .like(query.username() != null, UserEntity::getUsername, query.username())
                .like(query.email() != null, UserEntity::getEmail, query.email())
                .like(query.phone() != null, UserEntity::getPhone, query.phone())
                .orderByDesc(UserEntity::getCreatedAt).orderByDesc(UserEntity::getId);
        var page = users.selectPage(new Page<>(query.pageNum(), query.pageSize()), where);
        return new PageResult<>(page.getRecords().stream().map(this::response).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public UserResponse detail(int id) { return response(activeUser(id)); }

    // 创建用户与分配角色必须在同一事务中完成。
    @Transactional
    public String create(UserRequests.Create request, LoginUser operator) {
        if (Boolean.TRUE.equals(request.isSuperAdmin()) && !operator.isSuperAdmin()) {
            throw new BusinessException(403, "只有超级管理员可以设置管理员身份");
        }
        insertUser(request);
        return "用户新增成功";
    }

    private void insertUser(UserRequests.Create request) {
        var user = new UserEntity();
        user.setUsername(request.username()); user.setEmail(request.email()); user.setPhone(request.phone());
        user.setPassword(encodePassword(request.password())); user.setAvatar(request.avatar());
        user.setIsSuperAdmin(Boolean.TRUE.equals(request.isSuperAdmin())); user.setIsDeleted(false);
        user.setCreatedAt(Instant.now()); user.setUpdatedAt(user.getCreatedAt());
        users.insert(user);
        replaceRoles(user.getId(), request.roleIds() == null ? List.of() : request.roleIds());
    }

    // 公开注册只创建普通用户，角色与管理员标识不接受客户端指定。
    @Transactional
    public String register(UserRequests.Register request) {
        insertUser(new UserRequests.Create(request.username(), request.email(), request.phone(), request.password(),
                request.avatar(), false, List.of()));
        return "注册成功";
    }

    @Transactional
    public String update(UserRequests.Update request, LoginUser operator) {
        var user = activeUser(request.id());
        if (!operator.isSuperAdmin() && (Boolean.TRUE.equals(user.getIsSuperAdmin()) || Boolean.TRUE.equals(request.isSuperAdmin()))) {
            throw new BusinessException(403, "只有超级管理员可以修改管理员身份");
        }
        if (Boolean.FALSE.equals(request.isSuperAdmin())) { protectLastAdmin(user); }
        if (request.username() != null) { user.setUsername(request.username()); }
        if (request.email() != null) { user.setEmail(request.email()); }
        if (request.phone() != null) { user.setPhone(request.phone()); }
        if (request.password() != null) { user.setPassword(encodePassword(request.password())); }
        if (request.avatar() != null) { user.setAvatar(request.avatar()); }
        if (request.isSuperAdmin() != null) { user.setIsSuperAdmin(request.isSuperAdmin()); }
        user.setUpdatedAt(Instant.now());
        users.updateById(user);
        if (request.roleIds() != null) { replaceRoles(user.getId(), request.roleIds()); }
        return "用户修改成功";
    }

    @Transactional
    public String delete(int id, LoginUser operator) {
        var user = activeUser(id);
        if (Boolean.TRUE.equals(user.getIsSuperAdmin()) && !operator.isSuperAdmin()) {
            throw new BusinessException(403, "只有超级管理员可以删除管理员");
        }
        protectLastAdmin(user);
        user.setIsDeleted(true); user.setDeletedAt(Instant.now()); user.setUpdatedAt(user.getDeletedAt());
        users.updateById(user);
        access.clearUserRoles(id);
        return "用户删除成功";
    }

    // 仅在空用户库且显式给出初始化信息时建立首个管理员。
    @Transactional
    public void bootstrap(String username, String email, String phone, String password) {
        if (users.selectCount(null) != 0) { return; }
        insertUser(new UserRequests.Create(username, email, phone, password, null, true, List.of()));
    }

    private UserEntity activeUser(int id) {
        var user = users.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) { throw new BusinessException(404, "用户不存在"); }
        return user;
    }

    private UserResponse response(UserEntity user) {
        var roleNames = access.roles(user.getId());
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(), user.getAvatar(),
                Boolean.TRUE.equals(user.getIsSuperAdmin()), Boolean.TRUE.equals(user.getIsDeleted()),
                user.getDeletedAt(), user.getCreatedAt(), user.getUpdatedAt(), access.userRoleIds(user.getId()),
                roleNames, String.join("、", roleNames));
    }

    private void replaceRoles(int userId, List<Integer> ids) {
        if (new HashSet<>(ids).size() != ids.size() || (!ids.isEmpty() && roles.selectByIds(ids).size() != ids.size())) {
            throw new BusinessException(400, "角色不存在或重复");
        }
        access.clearUserRoles(userId);
        for (int id : ids) { access.addUserRole(userId, id); }
    }

    private String encodePassword(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) { throw new BusinessException(400, "密码不能超过72字节"); }
        return passwords.encode(value);
    }

    private void protectLastAdmin(UserEntity user) {
        if (!Boolean.TRUE.equals(user.getIsSuperAdmin())) { return; }
        // 先按固定顺序锁定管理员，再检查数量，防止两个事务同时删除最后两人。
        access.lockActiveAdministrators();
        if (users.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getIsSuperAdmin, true).eq(UserEntity::getIsDeleted, false)) <= 1) {
            throw new BusinessException(400, "不能删除或停用最后一个超级管理员");
        }
    }
}
