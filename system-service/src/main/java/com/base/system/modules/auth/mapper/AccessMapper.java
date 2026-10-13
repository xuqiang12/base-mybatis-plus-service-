package com.base.system.modules.auth.mapper;

import com.base.system.modules.menu.dto.ButtonResponse;
import com.base.system.modules.role.dto.RoleResponses;
import org.apache.ibatis.annotations.*;
import java.util.List;

// 用户角色、角色菜单与操作权限关联查询，所有值使用绑定参数。
public interface AccessMapper {
    @Select("SELECT r.name FROM relations.sys_role r JOIN relations.sys_user_role ur ON r.id=ur.role_id WHERE ur.user_id=#{userId} ORDER BY r.id")
    List<String> roles(int userId);
    @Select("SELECT role_id FROM relations.sys_user_role WHERE user_id=#{userId} ORDER BY role_id")
    List<Integer> userRoleIds(int userId);
    @Select("SELECT DISTINCT p.code FROM relations.sys_permission p JOIN relations.sys_role_permission rp ON p.id=rp.permission_id JOIN relations.sys_user_role ur ON rp.role_id=ur.role_id WHERE ur.user_id=#{userId} ORDER BY p.code")
    List<String> permissions(int userId);
    @Select("SELECT DISTINCT mr.menu_id FROM relations.sys_menu_role mr JOIN relations.sys_user_role ur ON mr.role_id=ur.role_id WHERE ur.user_id=#{userId}")
    List<Integer> visibleMenuIds(int userId);
    @Select("SELECT menu_id FROM relations.sys_menu_role WHERE role_id=#{roleId} ORDER BY menu_id")
    List<Integer> roleMenuIds(int roleId);
    @Select("SELECT permission_id FROM relations.sys_role_permission WHERE role_id=#{roleId} ORDER BY permission_id")
    List<Integer> rolePermissionIds(int roleId);
    @Select("SELECT r.id,r.name,(SELECT COUNT(*) FROM relations.sys_user_role ur JOIN relations.sys_user u ON ur.user_id=u.id WHERE ur.role_id=r.id AND u.is_deleted=FALSE) AS user_count FROM relations.sys_role r ORDER BY r.id")
    List<RoleResponses.Summary> roleSummaries();
    @Select("SELECT b.id,b.menu_id,b.name,b.sort,b.permission_id,p.code AS permission_code FROM relations.sys_menu_button b JOIN relations.sys_permission p ON b.permission_id=p.id ORDER BY b.sort,b.id")
    List<ButtonResponse> buttons();

    @Delete("DELETE FROM relations.sys_user_role WHERE user_id=#{userId}")
    int clearUserRoles(int userId);
    @Insert("INSERT INTO relations.sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})")
    int addUserRole(@Param("userId") int userId, @Param("roleId") int roleId);
    @Delete("DELETE FROM relations.sys_menu_role WHERE role_id=#{roleId}")
    int clearRoleMenus(int roleId);
    @Insert("INSERT INTO relations.sys_menu_role(role_id,menu_id) VALUES(#{roleId},#{menuId})")
    int addRoleMenu(@Param("roleId") int roleId, @Param("menuId") int menuId);
    @Delete("DELETE FROM relations.sys_role_permission WHERE role_id=#{roleId}")
    int clearRolePermissions(int roleId);
    @Insert("INSERT INTO relations.sys_role_permission(role_id,permission_id) VALUES(#{roleId},#{permissionId})")
    int addRolePermission(@Param("roleId") int roleId, @Param("permissionId") int permissionId);
    @Select("SELECT COUNT(*) FROM relations.sys_user_role ur JOIN relations.sys_user u ON ur.user_id=u.id WHERE ur.role_id=#{roleId} AND u.is_deleted=FALSE")
    long roleUserCount(int roleId);
    @Delete("DELETE FROM relations.sys_user_role WHERE role_id=#{roleId}")
    int clearRoleUsers(int roleId);
    @Select("SELECT COUNT(*) FROM relations.sys_menu_role WHERE menu_id=#{menuId}")
    long menuRoleCount(int menuId);
    @Select("SELECT COUNT(*) FROM relations.sys_role_permission WHERE permission_id=#{permissionId}")
    long permissionRoleCount(int permissionId);
    @Select("SELECT id FROM relations.sys_user WHERE is_super_admin=TRUE AND is_deleted=FALSE ORDER BY id FOR UPDATE")
    List<Integer> lockActiveAdministrators();
}
