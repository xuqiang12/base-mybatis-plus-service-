package com.base.system.modules.auth.mapper;

import com.base.system.modules.menu.dto.ButtonResponse;
import com.base.system.modules.role.dto.RoleResponses;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户角色、角色菜单与操作权限关联数据库访问。
 * SQL 位于 mapper/auth/AccessMapper.xml。
 */
public interface AccessMapper {

    /**
     * 查询用户角色名称，按角色 ID 排序。
     *
     * @param userId 用户 ID
     * @return 查询结果
     */
    List<String> findRoleNames(@Param("userId") int userId);

    /**
     * 查询用户关联的角色 ID。
     *
     * @param userId 用户 ID
     * @return 查询结果
     */
    List<Integer> findRoleIdsByUserId(@Param("userId") int userId);

    /**
     * 查询用户通过角色获得的操作权限码。
     *
     * @param userId 用户 ID
     * @return 查询结果
     */
    List<String> findPermissionCodes(@Param("userId") int userId);

    /**
     * 查询用户通过角色获得的可见菜单 ID。
     *
     * @param userId 用户 ID
     * @return 查询结果
     */
    List<Integer> findVisibleMenuIds(@Param("userId") int userId);

    /**
     * 查询角色关联的菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 查询结果
     */
    List<Integer> findMenuIdsByRoleId(@Param("roleId") int roleId);

    /**
     * 查询角色关联的操作权限 ID。
     *
     * @param roleId 角色 ID
     * @return 查询结果
     */
    List<Integer> findPermissionIdsByRoleId(@Param("roleId") int roleId);

    /**
     * 查询角色列表及未删除用户数量。
     *
     * @return 查询结果
     */
    List<RoleResponses.Summary> findRoleSummaries();

    /**
     * 查询菜单按钮及关联权限码。
     *
     * @return 查询结果
     */
    List<ButtonResponse> findButtons();

    /**
     * 删除用户的全部角色关联。
     *
     * @param userId 用户 ID
     * @return 受影响行数
     */
    int deleteUserRoles(@Param("userId") int userId);

    /**
     * 新增用户角色关联。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 受影响行数
     */
    int insertUserRole(@Param("userId") int userId, @Param("roleId") int roleId);

    /**
     * 删除角色的全部菜单关联。
     *
     * @param roleId 角色 ID
     * @return 受影响行数
     */
    int deleteRoleMenus(@Param("roleId") int roleId);

    /**
     * 新增角色菜单关联。
     *
     * @param roleId 角色 ID
     * @param menuId 菜单 ID
     * @return 受影响行数
     */
    int insertRoleMenu(@Param("roleId") int roleId, @Param("menuId") int menuId);

    /**
     * 删除角色的全部操作权限关联。
     *
     * @param roleId 角色 ID
     * @return 受影响行数
     */
    int deleteRolePermissions(@Param("roleId") int roleId);

    /**
     * 新增角色操作权限关联。
     *
     * @param roleId 角色 ID
     * @param permissionId 操作权限 ID
     * @return 受影响行数
     */
    int insertRolePermission(@Param("roleId") int roleId, @Param("permissionId") int permissionId);

    /**
     * 统计使用角色的未删除用户数量。
     *
     * @param roleId 角色 ID
     * @return 关联数量
     */
    long getRoleUserCount(@Param("roleId") int roleId);

    /**
     * 删除角色的全部用户关联。
     *
     * @param roleId 角色 ID
     * @return 受影响行数
     */
    int deleteRoleUsers(@Param("roleId") int roleId);

    /**
     * 统计菜单关联的角色数量。
     *
     * @param menuId 菜单 ID
     * @return 关联数量
     */
    long getMenuRoleCount(@Param("menuId") int menuId);

    /**
     * 统计操作权限关联的角色数量。
     *
     * @param permissionId 操作权限 ID
     * @return 关联数量
     */
    long getPermissionRoleCount(@Param("permissionId") int permissionId);

    /**
     * 查询并锁定有效超级管理员，串行化管理员变更。
     *
     * @return 查询结果
     */
    List<Integer> findActiveAdministratorsForUpdate();
}
