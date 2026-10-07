package com.iot.backend;

import com.iot.backend.dto.request.LoginRequest;
import com.iot.backend.security.JwtTokenProvider;
import com.iot.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTests {
    @Test
    void responseAndTokenUseAuthenticatedUsernameRatherThanRequestText() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        var principal = User.withUsername("admin").password("unused").authorities("USER").build();
        var authenticated = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(manager.authenticate(any())).thenReturn(authenticated);
        when(provider.generateToken(authenticated)).thenReturn("jwt");
        var result = new AuthService(manager, provider).login(new LoginRequest("ADMIN", "password"));
        assertThat(result.getUsername()).isEqualTo("admin");
        verify(provider).generateToken(authenticated);
    }
}
