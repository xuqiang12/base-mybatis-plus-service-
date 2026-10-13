package com.base.system.security;

import com.base.security.JwtVerificationProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(JwtSigningProperties.class)
public class JwtSigningConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public JwtEncoder jwtEncoder(JwtSigningProperties signing, JwtVerificationProperties verification) throws IOException {
        try (var publicInput = verification.publicKey().getInputStream();
                var privateInput = signing.privateKey().getInputStream()) {
            var publicKey = RsaKeyConverters.x509().convert(publicInput);
            var privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            if (!publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new IllegalArgumentException("JWT 公钥与私钥不匹配");
            }
            var key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        }
    }
}
