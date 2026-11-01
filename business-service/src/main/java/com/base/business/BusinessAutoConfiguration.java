package com.base.business;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/** 启动程序通过自动配置装配，家庭代码无需导入或调用生意业务。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "business", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = BusinessController.class)
public class BusinessAutoConfiguration {
}
