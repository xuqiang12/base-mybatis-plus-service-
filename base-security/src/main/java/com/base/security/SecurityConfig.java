package com.base.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.web.SecurityFilterChain;
import java.io.IOException;
import java.util.ArrayList;

// 公钥校验 JWT；操作权限使用服务提供的当前授权，不信任 Token 中的旧权限。
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtVerificationProperties.class, PublicEndpointProperties.class})
public class SecurityConfig {
    @Bean
    public JwtDecoder jwtDecoder(JwtVerificationProperties properties) throws IOException {
        try (var input = properties.publicKey().getInputStream()) {
            var decoder = NimbusJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(input)).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
            return decoder;
        }
    }

    @Bean
    public SecurityFilterChain security(HttpSecurity http, CurrentUserProvider users, SecurityErrorHandler errors,
            PublicEndpointProperties publicEndpoints) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    if (!publicEndpoints.get().isEmpty()) {
                        auth.requestMatchers(HttpMethod.GET, publicEndpoints.get().toArray(String[]::new)).permitAll();
                    }
                    if (!publicEndpoints.post().isEmpty()) {
                        auth.requestMatchers(HttpMethod.POST, publicEndpoints.post().toArray(String[]::new)).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            int userId;
                            try {
                                userId = Integer.parseInt(token.getSubject());
                            } catch (NumberFormatException e) {
                                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
                            }
                            LoginUser user = users.currentUser(userId, token.getTokenValue());
                            var authorities = new ArrayList<>(user.permissions().stream()
                                    .map(code -> new SimpleGrantedAuthority("permission:" + code)).toList());
                            if (user.isSuperAdmin()) { authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")); }
                            return new UsernamePasswordAuthenticationToken(user, token, authorities);
                        })));
        return http.build();
    }
}
