package com.base.security;

import com.base.common.BaseCommonConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// 按需导入安全模块，不扫描任何业务或用户管理组件。
@Configuration
@Import(BaseCommonConfiguration.class)
@ComponentScan("com.base.security")
public class BaseSecurityConfiguration {}
