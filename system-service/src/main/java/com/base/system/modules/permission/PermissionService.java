package com.base.system.modules.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.modules.auth.mapper.AccessMapper;
import com.base.system.modules.menu.entity.MenuButtonEntity;
import com.base.system.modules.menu.mapper.MenuButtonMapper;
import com.base.system.modules.permission.dto.PermissionRequests;
import com.base.system.modules.permission.entity.PermissionEntity;
import com.base.system.modules.permission.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 操作权限码维护，被按钮或角色引用的权限不能删除。
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionMapper permissions;
    private final MenuButtonMapper buttons;
    private final AccessMapper access;

    public java.util.List<PermissionEntity> list() {
        return permissions.selectList(new LambdaQueryWrapper<PermissionEntity>().orderByAsc(PermissionEntity::getId));
    }
    public String create(PermissionRequests.Create request) {
        var value = new PermissionEntity(); value.setCode(request.code()); value.setName(request.name()); permissions.insert(value);
        return "权限新增成功";
    }
    public String update(PermissionRequests.Update request) {
        var value = required(request.id());
        if (value.getCode().startsWith("system:") && !value.getCode().equals(request.code())) {
            throw new BusinessException(400, "基础权限码不能改名");
        }
        value.setCode(request.code()); value.setName(request.name()); permissions.updateById(value);
        return "权限修改成功";
    }
    public String delete(int id) {
        var value = required(id);
        if (value.getCode().startsWith("system:")) { throw new BusinessException(400, "基础权限不能删除"); }
        if (access.permissionRoleCount(id) > 0 || buttons.selectCount(new LambdaQueryWrapper<MenuButtonEntity>()
                .eq(MenuButtonEntity::getPermissionId, id)) > 0) { throw new BusinessException(400, "权限已被使用，不能删除"); }
        permissions.deleteById(id);
        return "权限删除成功";
    }
    private PermissionEntity required(int id) {
        var value = permissions.selectById(id);
        if (value == null) { throw new BusinessException(404, "权限不存在"); }
        return value;
    }
}
