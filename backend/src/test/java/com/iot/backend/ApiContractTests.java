package com.iot.backend;

import com.iot.backend.config.CorsConfig;
import com.iot.backend.config.SecurityConfig;
import com.iot.backend.controller.ApiErrorController;
import com.iot.backend.controller.AuthController;
import com.iot.backend.controller.SensorController;
import com.iot.backend.dto.response.ApiResponse;
import com.iot.backend.dto.response.LoginResponse;
import com.iot.backend.security.CustomUserDetailsService;
import com.iot.backend.security.JwtAuthenticationFilter;
import com.iot.backend.security.JwtTokenProvider;
import com.iot.backend.service.AuthService;
import com.iot.backend.service.SensorService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Kiểm tra MVC/Security thực, không cần MySQL hoặc MQTT. */
@WebMvcTest(controllers = {AuthController.class, SensorController.class,
        ApiErrorController.class, ApiContractTests.ProbeController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class,
        ApiContractTests.ProbeController.class})
class ApiContractTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SecurityFilterChain securityFilterChain;
    @MockitoBean AuthService authService;
    @MockitoBean SensorService sensorService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean CustomUserDetailsService userDetailsService;

    private JsonNode assertEnvelope(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.has("status")).isTrue();
        assertThat(json.has("message")).isTrue();
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("status").intValue()).isEqualTo(status);
        assertThat(json.get("message").isString()).isTrue();
        return json;
    }

    @Test
    void loginSuccessAndInvalidCredentials() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("jwt", "admin"));
        JsonNode success = assertEnvelope(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"password\"}"), 200);
        assertThat(success.get("data").get("token").stringValue()).isEqualTo("jwt");
        when(authService.login(any())).thenThrow(new BadCredentialsException("internal details"));
        JsonNode failure = assertEnvelope(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"), 401);
        assertThat(failure.get("data").isNull()).isTrue();
        assertThat(failure.toString()).doesNotContain("internal details");
    }

    @Test
    void bodyValidationDetailsAreInsideData() throws Exception {
        JsonNode json = assertEnvelope(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"\",\"password\":\"\"}"), 400);
        assertThat(json.get("data").has("username")).isTrue();
        assertThat(json.get("data").has("password")).isTrue();
    }

    @Test
    void malformedJsonAndWrongMethodKeepHttpStatus() throws Exception {
        assertThat(assertEnvelope(post("/api/auth/login").contentType("application/json")
                .content("{"), 400).get("data").isNull()).isTrue();
        assertEnvelope(get("/api/auth/login"), 405);
        assertEnvelope(post("/api/auth/login").contentType("text/plain").content("body"), 415);
    }

    @Test
    void queryValidationAndConversionAreClientErrors() throws Exception {
        assertEnvelope(get("/api/auth/probe/page").param("page", "abc"), 400);
        JsonNode json = assertEnvelope(get("/api/auth/probe/page").param("page", "0"), 400);
        assertThat(json.get("data").has("page")).isTrue();
        assertEnvelope(get("/api/auth/probe/page"), 400);
    }

    @ParameterizedTest
    @CsvSource({"missing,404", "conflict,409", "unavailable,503", "unexpected,500", "forbidden,403"})
    void exceptionsKeepEnvelopeAndDoNotLeakDetails(String type, int status) throws Exception {
        JsonNode json = assertEnvelope(get("/api/auth/probe/" + type), status);
        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain("sensitive details");
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        assertEnvelope(get("/api/auth/not-a-route"), 404);
    }

    @Test
    void missingAndInvalidTokenReturn401FromSecurity() throws Exception {
        assertThat(assertEnvelope(get("/api/sensors/latest"), 401).get("data").isNull()).isTrue();
        assertEnvelope(get("/api/sensors/latest").header("Authorization", "Bearer invalid"), 401);
    }

    @Test
    void validTokenAllowsNullDataAndDeletedUserReturns401() throws Exception {
        when(jwtTokenProvider.validateToken("valid")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("valid")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin"))
                .thenReturn(User.withUsername("admin").password("unused").roles("USER").build());
        when(sensorService.getLatestData()).thenReturn(null);
        assertThat(assertEnvelope(get("/api/sensors/latest")
                .header("Authorization", "Bearer valid"), 200).get("data").isNull()).isTrue();
        when(userDetailsService.loadUserByUsername("admin"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("deleted"));
        assertEnvelope(get("/api/sensors/latest").header("Authorization", "Bearer valid"), 401);
    }

    @Test
    void errorDispatchPreservesOriginalStatusWithoutRequiringToken() throws Exception {
        assertEnvelope(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                .with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; }), 404);
    }

    @Test
    void acceptedResponseSupports202() throws Exception {
        JsonNode json = mapper.valueToTree(ApiResponse.success(202, "Đã gửi lệnh", Map.of("device", "led")));
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.get("status").intValue()).isEqualTo(202);
    }

    @Test
    void securityAccessDeniedHandlerReturns403Envelope() throws Exception {
        ExceptionTranslationFilter filter = securityFilterChain.getFilters().stream()
                .filter(ExceptionTranslationFilter.class::isInstance)
                .map(ExceptionTranslationFilter.class::cast).findFirst().orElseThrow();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of()));
        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new AccessDeniedException("sensitive details");
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.get("status").intValue()).isEqualTo(403);
        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain("sensitive details");
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/auth/probe/page")
        ApiResponse<Integer> page(@RequestParam @Min(1) int page) { return ApiResponse.success(page); }
        @GetMapping("/api/auth/probe/missing")
        void missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có thiết bị"); }
        @GetMapping("/api/auth/probe/conflict")
        void conflict() { throw new DataIntegrityViolationException("sensitive details"); }
        @GetMapping("/api/auth/probe/unavailable")
        void unavailable() { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "sensitive details"); }
        @GetMapping("/api/auth/probe/unexpected")
        void unexpected() { throw new IllegalStateException("sensitive details"); }
        @GetMapping("/api/auth/probe/forbidden")
        void forbidden() { throw new AccessDeniedException("sensitive details"); }
    }
}
