package com.base.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

// 各服务明确声明需要匿名访问的业务路径，默认无公开业务接口。
@ConfigurationProperties("security.public-endpoints")
public record PublicEndpointProperties(List<String> get, List<String> post) {
    public PublicEndpointProperties {
        get = get == null ? List.of() : List.copyOf(get);
        post = post == null ? List.of() : List.copyOf(post);
    }
}
