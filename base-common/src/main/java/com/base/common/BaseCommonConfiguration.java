package com.base.common;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

// 服务显式引入此配置，只启用通用 Web 基础组件。
@Configuration
@ComponentScan("com.base.common")
public class BaseCommonConfiguration {}
