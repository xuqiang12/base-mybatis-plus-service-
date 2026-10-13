package com.base.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 注册 Mapper 扫描和数据库分页插件。
@Configuration
@MapperScan("com.base.system.modules.**.mapper")
public class MybatisConfig {
    @Bean
    public MybatisPlusInterceptor pagination() {
        var interceptor = new MybatisPlusInterceptor();
        var pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
