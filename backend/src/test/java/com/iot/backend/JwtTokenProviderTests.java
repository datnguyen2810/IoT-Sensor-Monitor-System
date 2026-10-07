package com.iot.backend;

import com.iot.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTests {
    private JwtTokenProvider provider(String secret, long expiration) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", expiration);
        provider.init();
        return provider;
    }

    @Test
    void invalidTokensReturnFalseInsteadOfEscapingTheFilter() {
        JwtTokenProvider provider = provider("test-key-one-012345678901234567890123456789", 60000);
        JwtTokenProvider other = provider("test-key-two-012345678901234567890123456789", 60000);
        JwtTokenProvider expired = provider("test-key-one-012345678901234567890123456789", -60000);
        assertThat(provider.validateToken(other.generateTokenFromUsername("admin"))).isFalse();
        assertThat(provider.validateToken(expired.generateTokenFromUsername("admin"))).isFalse();
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
        assertThat(provider.validateToken(provider.generateTokenFromUsername("admin"))).isTrue();
    }
}
