package com.base.system.modules.role;

import com.base.common.exception.BusinessException;
import com.base.system.modules.auth.mapper.AccessMapper;
import com.base.system.modules.menu.mapper.MenuMapper;
import com.base.system.modules.permission.mapper.PermissionMapper;
import com.base.system.modules.role.dto.RoleRequests;
import com.base.system.modules.role.dto.RoleResponses;
import com.base.system.modules.role.entity.RoleEntity;
import com.base.system.modules.role.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;

// 角色维护、菜单可见性和按钮操作权限授权。
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper roles;
    private final MenuMapper menus;
    private final PermissionMapper permissions;
    private final AccessMapper access;

    public List<RoleResponses.Summary> list() { return access.roleSummaries(); }
    public RoleResponses.Detail detail(int id) {
        var role = requiredRole(id);
        return new RoleResponses.Detail(id, role.getName(), access.roleMenuIds(id), access.rolePermissionIds(id));
    }
    public String create(RoleRequests.Create request) {
        var role = new RoleEntity(); role.setName(request.name()); roles.insert(role);
        return "角色新增成功";
    }
    public String update(RoleRequests.Update request) {
        var role = requiredRole(request.id()); role.setName(request.name()); roles.updateById(role);
        return "角色修改成功";
    }
    @Transactional
    public String delete(int id) {
        requiredRole(id);
        if (access.roleUserCount(id) > 0) { throw new BusinessException(400, "角色已被用户使用，不能删除"); }
        access.clearRoleMenus(id); access.clearRolePermissions(id); access.clearRoleUsers(id); roles.deleteById(id);
        return "角色删除成功";
    }
    // 整体替换授权，任何校验或数据库失败都回滚。
    @Transactional
    public String grant(RoleRequests.Grant request) {
        requiredRole(request.id());
        if (new HashSet<>(request.menuIds()).size() != request.menuIds().size()
                || (!request.menuIds().isEmpty() && menus.selectByIds(request.menuIds()).size() != request.menuIds().size())) {
            throw new BusinessException(400, "菜单不存在或重复");
        }
        if (new HashSet<>(request.permissionIds()).size() != request.permissionIds().size()
                || (!request.permissionIds().isEmpty() && permissions.selectByIds(request.permissionIds()).size() != request.permissionIds().size())) {
            throw new BusinessException(400, "权限不存在或重复");
        }
        access.clearRoleMenus(request.id()); access.clearRolePermissions(request.id());
        for (int id : request.menuIds()) { access.addRoleMenu(request.id(), id); }
        for (int id : request.permissionIds()) { access.addRolePermission(request.id(), id); }
        return "角色授权保存成功";
    }
    private RoleEntity requiredRole(int id) {
        var role = roles.selectById(id);
        if (role == null) { throw new BusinessException(404, "角色不存在"); }
        return role;
    }
}
