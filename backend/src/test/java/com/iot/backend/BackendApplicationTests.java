package com.iot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.iot.backend.security.JwtTokenProvider;
import com.iot.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationContext;
import com.iot.backend.service.MqttService;
import com.iot.backend.config.MqttConfig;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.SecretKey;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

// Cấu hình nội bộ của kiểm thử; ứng dụng chỉ dùng application.properties, không có profile dev/test.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_monitor_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.data-locations=classpath:data.sql",
        "mqtt.enabled=false", "devices.command.timeout-scheduler.enabled=false"
})
@AutoConfigureMockMvc
class BackendApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ApplicationContext context;

    @Test
    void testsDisableMqttAndUseAnIsolatedDatabaseWithoutProfiles() {
        assertThat(context.getBeansOfType(MqttService.class)).isEmpty();
        assertThat(context.getBeansOfType(MqttConfig.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
        assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
    }

    @Test
    void deviceStatusWorksWhenMqttBeanIsDisabledButControlReturns503() throws Exception {
        String token = jwtTokenProvider.generateTokenFromUsername("admin");
        mvc.perform(get("/api/devices/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"device\":\"led\",\"action\":\"ON\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void seedPasswordMatchesAndLoginWorksWithRealSecurityAndRepository() throws Exception {
        assertThat(passwordEncoder.matches("123456", users.findByUsername("admin").orElseThrow().getPassword()))
                .isTrue();
        String body = mvc.perform(post("/api/auth/login").contentType("application/json")
                .header("Origin", "http://localhost:5500")
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("data").get("token").stringValue();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("admin");
        mvc.perform(get("/api/sensors/latest").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void wrongPasswordAndUnknownUserReturnSame401Message() throws Exception {
        for (String username : new String[]{"admin", "no-such-user"}) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Test
    void expiredWrongSignatureAndMissingTokensReturn401() throws Exception {
        SecretKey key = (SecretKey) ReflectionTestUtils.getField(jwtTokenProvider, "key");
        String expired = Jwts.builder().subject("admin")
                .issuedAt(new Date(System.currentTimeMillis() - 120000))
                .expiration(new Date(System.currentTimeMillis() - 60000)).signWith(key).compact();
        String wrongSignature = Jwts.builder().subject("admin")
                .signWith(Keys.hmacShaKeyFor("test-only-different-key-01234567890123456789".getBytes(StandardCharsets.UTF_8)))
                .compact();
        for (String token : new String[]{"", expired, wrongSignature}) {
            mvc.perform(get("/api/sensors/latest").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Test
    void emptyLoginBodyIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5500", "http://127.0.0.1:5500"})
    void preflightAllowsFrontendWithoutJwt(String origin) throws Exception {
        mvc.perform(options("/api/sensors/latest")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().exists("Access-Control-Allow-Headers"));
    }

    @Test
    void externalOriginsAreNotAllowed() throws Exception {
        mvc.perform(options("/api/sensors/latest")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void openApiIsGeneratedWithCurrentSpringBootAndResponseSchema() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.components.schemas.ApiResponseLoginResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ApiResponseLoginResponse.properties.message").exists())
                .andExpect(jsonPath("$.components.schemas.ApiResponseLoginResponse.properties.data").exists())
                .andExpect(jsonPath("$.components.schemas.ApiResponseLoginResponse.properties.errors").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApiResponseLoginResponse.properties.timestamp").doesNotExist());
    }

	@Test
	void contextLoads() {
	}

}
