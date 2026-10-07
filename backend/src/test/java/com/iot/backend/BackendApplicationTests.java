package com.iot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.iot.backend.service.MqttService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BackendApplicationTests {

    @Autowired MockMvc mvc;
    // Kiểm tra context HTTP thực mà không phụ thuộc broker/ghi dữ liệu MQTT.
    @MockitoBean MqttService mqttService;

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
