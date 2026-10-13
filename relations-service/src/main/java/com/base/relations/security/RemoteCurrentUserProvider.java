package com.base.relations.security;

import com.base.common.result.ApiResponse;
import com.base.security.CurrentUserProvider;
import com.base.security.IdentityServiceUnavailableException;
import com.base.security.LoginUser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 转发原 Token 获取最新授权；不持有私钥，不连接统一管理服务的数据库。
@Component
@EnableConfigurationProperties(IdentityClientProperties.class)
public class RemoteCurrentUserProvider implements CurrentUserProvider {
    private final RestClient client;
    private final IdentityClientProperties properties;

    public RemoteCurrentUserProvider(IdentityClientProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public LoginUser currentUser(int userId, String token) {
        try {
            var response = client.get().uri(properties.accessUrl()).headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(new ParameterizedTypeReference<ApiResponse<LoginUser>>() {});
            if (response == null || response.code() != 0 || response.data() == null
                    || !Integer.valueOf(userId).equals(response.data().id())
                    || response.data().roles() == null || response.data().permissions() == null) {
                throw new IdentityServiceUnavailableException(new IllegalStateException("身份响应不完整或主体不匹配"));
            }
            return response.data();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
            }
            throw new IdentityServiceUnavailableException(e);
        } catch (RestClientException e) {
            throw new IdentityServiceUnavailableException(e);
        }
    }
}
