package com.base.system.modules.auth;

import com.base.security.LoginUser;
import com.base.system.modules.auth.dto.LoginRequest;
import com.base.system.modules.auth.dto.LoginResponse;
import com.base.system.modules.auth.dto.UserInfoResponse;

/**
 * 密码登录与当前用户信息业务接口。
 */
public interface AuthService {

    /**
     * 使用邮箱或手机号和密码登录。
     *
     * @param request 登录请求参数
     * @return 登录成功后签发的 Token
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前用户资料、权限及可见菜单。
     *
     * @param user 已认证的当前用户
     * @return 当前用户信息
     */
    UserInfoResponse getUserInfo(LoginUser user);
}
