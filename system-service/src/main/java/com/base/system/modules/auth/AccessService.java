package com.base.system.modules.auth;

import com.base.security.CurrentUserProvider;
import com.base.security.LoginUser;

/**
 * 当前身份与授权业务接口，同时提供安全模块所需的当前用户查询能力。
 */
public interface AccessService extends CurrentUserProvider {

    /**
     * 查询有效用户及其最新角色、操作权限。
     *
     * @param userId 用户 ID
     * @return 当前有效身份与授权信息
     */
    @Override
    LoginUser currentUser(int userId);
}
