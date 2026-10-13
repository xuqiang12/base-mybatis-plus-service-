package com.base.system.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.security.LoginUser;
import com.base.system.modules.auth.mapper.AccessMapper;
import com.base.system.modules.menu.dto.ButtonResponse;
import com.base.system.modules.menu.dto.MenuNode;
import com.base.system.modules.menu.dto.MenuRequests;
import com.base.system.modules.menu.entity.MenuButtonEntity;
import com.base.system.modules.menu.entity.MenuEntity;
import com.base.system.modules.menu.mapper.MenuButtonMapper;
import com.base.system.modules.menu.mapper.MenuMapper;
import com.base.system.modules.permission.entity.PermissionEntity;
import com.base.system.modules.permission.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

// 菜单层级、管理菜单树、当前用户菜单及按钮权限绑定。
@Service
@RequiredArgsConstructor
public class MenuService {
    private final MenuMapper menus;
    private final MenuButtonMapper buttons;
    private final PermissionMapper permissions;
    private final AccessMapper access;

    public List<MenuEntity> list() {
        return menus.selectList(new LambdaQueryWrapper<MenuEntity>().orderByAsc(MenuEntity::getSort).orderByAsc(MenuEntity::getId));
    }
    public List<MenuNode> tree() { return buildTree(list(), access.buttons(), null); }
    public List<ButtonResponse> buttons(int menuId) {
        requiredMenu(menuId);
        return access.buttons().stream().filter(button -> button.menuId().equals(menuId)).toList();
    }

    // 用户只取得被角色授权的菜单，按钮只返回实际拥有的权限码。
    public List<MenuNode> userMenus(LoginUser user) {
        var visible = new HashSet<>(access.visibleMenuIds(user.id()));
        var selected = user.isSuperAdmin() ? list() : list().stream().filter(menu -> visible.contains(menu.getId())).toList();
        return buildTree(selected, access.buttons(), new HashSet<>(user.permissions()));
    }

    public String create(MenuRequests.Create request) {
        if (request.parentId() != null) { requiredMenu(request.parentId()); }
        var menu = new MenuEntity();
        menu.setParentId(request.parentId()); menu.setName(request.name()); menu.setPath(request.path());
        menu.setComponent(request.component()); menu.setIcon(request.icon());
        menu.setSort(request.sort() == null ? 0 : request.sort()); menu.setType(request.type().name());
        menu.setCreatedAt(Instant.now()); menu.setUpdatedAt(menu.getCreatedAt());
        menus.insert(menu);
        return "菜单新增成功";
    }

    @Transactional
    public String update(MenuRequests.Update request) {
        // 串行校验菜单层级，避免并发把两个菜单互相设为父级。
        menus.selectList(new LambdaQueryWrapper<MenuEntity>().select(MenuEntity::getId)
                .orderByAsc(MenuEntity::getId).last("FOR UPDATE"));
        var menu = requiredMenu(request.getId());
        if (request.isParentIdProvided()) {
            Integer ancestor = request.getParentId();
            while (ancestor != null) {
                if (ancestor.equals(menu.getId())) { throw new BusinessException(400, "菜单父级不能是自身或其子菜单"); }
                ancestor = requiredMenu(ancestor).getParentId();
            }
            // 使用更新条件显式写入 null，避免 MyBatis-Plus 忽略空字段。
            menus.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MenuEntity>()
                    .eq(MenuEntity::getId, menu.getId()).set(MenuEntity::getParentId, request.getParentId()));
            menu.setParentId(request.getParentId());
        }
        if (request.getName() != null) { menu.setName(request.getName()); }
        if (request.getPath() != null) { menu.setPath(request.getPath()); }
        if (request.getComponent() != null) { menu.setComponent(request.getComponent()); }
        if (request.getIcon() != null) { menu.setIcon(request.getIcon()); }
        if (request.getSort() != null) { menu.setSort(request.getSort()); }
        if (request.getType() != null) { menu.setType(request.getType().name()); }
        menu.setUpdatedAt(Instant.now()); menus.updateById(menu);
        return "菜单修改成功";
    }

    public String delete(int id) {
        requiredMenu(id);
        if (menus.selectCount(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getParentId, id)) > 0) {
            throw new BusinessException(400, "菜单下存在子菜单，不能删除");
        }
        if (access.menuRoleCount(id) > 0 || buttons.selectCount(new LambdaQueryWrapper<MenuButtonEntity>().eq(MenuButtonEntity::getMenuId, id)) > 0) {
            throw new BusinessException(400, "菜单存在授权关系，不能删除");
        }
        menus.deleteById(id);
        return "菜单删除成功";
    }

    @Transactional
    public String createButton(MenuRequests.CreateButton request) {
        requiredMenu(request.menuId());
        var permission = findOrCreatePermission(request.permissionCode(), request.name());
        var button = new MenuButtonEntity(); button.setMenuId(request.menuId());
        button.setPermissionId(permission.getId()); button.setName(request.name()); button.setSort(request.sort() == null ? 0 : request.sort());
        buttons.insert(button);
        return "按钮新增成功";
    }

    @Transactional
    public String updateButton(MenuRequests.UpdateButton request) {
        var button = requiredButton(request.id()); requiredMenu(request.menuId());
        var permission = findOrCreatePermission(request.permissionCode(), request.name());
        button.setMenuId(request.menuId()); button.setPermissionId(permission.getId()); button.setName(request.name());
        if (request.sort() != null) { button.setSort(request.sort()); }
        buttons.updateById(button);
        return "按钮修改成功";
    }

    @Transactional
    public String deleteButton(int id) {
        requiredButton(id);
        buttons.deleteById(id);
        return "按钮删除成功";
    }

    private MenuEntity requiredMenu(int id) {
        var menu = menus.selectById(id);
        if (menu == null) { throw new BusinessException(404, "菜单不存在"); }
        return menu;
    }
    private MenuButtonEntity requiredButton(int id) {
        var button = buttons.selectById(id);
        if (button == null) { throw new BusinessException(404, "按钮不存在"); }
        return button;
    }
    private PermissionEntity findOrCreatePermission(String code, String name) {
        var permission = permissions.selectOne(new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getCode, code));
        if (permission == null) {
            permission = new PermissionEntity(); permission.setCode(code); permission.setName(name); permissions.insert(permission);
        }
        return permission;
    }

    // 同一层级结构支持管理按钮详情和用户按钮权限码两种返回。
    private List<MenuNode> buildTree(List<MenuEntity> selected, List<ButtonResponse> allButtons, Set<String> allowed) {
        Map<Integer, MenuNode> nodes = new LinkedHashMap<>();
        for (var menu : selected) {
            var menuButtons = allButtons.stream().filter(button -> button.menuId().equals(menu.getId())).toList();
            List<?> exposedButtons = allowed == null ? menuButtons : menuButtons.stream()
                    .map(ButtonResponse::permissionCode).filter(allowed::contains).toList();
            nodes.put(menu.getId(), new MenuNode(menu.getId(), menu.getParentId(), menu.getName(), menu.getPath(),
                    menu.getComponent(), menu.getIcon(), menu.getType(), menu.getSort(), exposedButtons, new ArrayList<>()));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (var node : nodes.values()) {
            var parent = nodes.get(node.parentId());
            if (parent == null) { roots.add(node); } else { parent.children().add(node); }
        }
        return roots;
    }
}
