package com.base.security;

// 实现由服务提供：统一管理服务查询本地库，业务服务查询统一管理服务。
public interface CurrentUserProvider {
    LoginUser currentUser(int userId, String token);
}
