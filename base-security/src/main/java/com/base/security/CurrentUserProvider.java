package com.base.security;

// 由应用提供实现，从本地数据库读取当前身份与权限。
public interface CurrentUserProvider {
    LoginUser currentUser(int userId);
}
