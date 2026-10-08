package com.iot.backend;

import com.iot.backend.dto.mqtt.DeviceAcknowledgement;
import com.iot.backend.dto.request.DeviceControlRequest;
import com.iot.backend.entity.Device;
import com.iot.backend.entity.History;
import com.iot.backend.repository.*;
import com.iot.backend.security.JwtTokenProvider;
import com.iot.backend.service.*;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real transactions/security/database; only the network publisher is mocked. No test-wide transaction. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_device_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false", "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always", "mqtt.enabled=false", "devices.command.timeout-scheduler.enabled=false"
})
@AutoConfigureMockMvc
public class DeviceApiTests {
    @Autowired DeviceService service;
    @Autowired DeviceCommandTransactions commands;
    @Autowired HistoryRepository histories;
    @Autowired DeviceRepository devices;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean MqttService mqtt;

    @BeforeEach
    void prepare() {
        histories.deleteAll();
        for (String code : List.of("led", "fan", "ac")) {
            if (!devices.existsByCode(code)) devices.saveAndFlush(Device.builder().code(code).name(code).build());
        }
        when(mqtt.isReadyForControl()).thenReturn(true);
    }

    @AfterEach
    void cleanup() { histories.deleteAll(); }

    private String token() { return tokens.generateTokenFromUsername("admin"); }
    private int send(String device, String action) { return service.control(new DeviceControlRequest(device, action), "admin").commandId(); }
    private History row(int id) { return histories.findById(id).orElseThrow(); }
    private void ack(int id, String device, String status, String actual) {
        commands.acknowledge(new DeviceAcknowledgement(id, device, status, actual));
    }
    private String state(String code) {
        return service.getStatuses().stream().filter(item -> item.device().equals(code)).findFirst().orElseThrow().status();
    }
    private void age(int id) {
        History history = row(id);
        history.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMinutes(1));
        histories.saveAndFlush(history);
    }

    @Test
    void statusUnknownUntilAckAndPendingDoesNotChangeHardwareState() throws Exception {
        mvc.perform(get("/api/devices/status").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data.length()").value(3));
        assertThat(service.getStatuses()).allSatisfy(item -> {
            assertThat(item.status()).isEqualTo("UNKNOWN");
            assertThat(item.lastSeen()).isNull();
        });
        int id = send("led", "ON");
        assertThat(state("led")).isEqualTo("UNKNOWN");
        ack(id, "led", "pending", "ON");
        assertThat(row(id).getStatus()).isEqualTo("pending");
        assertThat(state("led")).isEqualTo("UNKNOWN");
        ack(id, "led", "ON", "");
        assertThat(row(id).getStatus()).isEqualTo("success");
        assertThat(state("led")).isEqualTo("ON");
    }

    @Test
    void apiReturns202AndHistoryUsesAuthenticatedUserNotBody() throws Exception {
        var result = mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{\"device\":\"led\",\"action\":\"ON\",\"user\":\"forged\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.data.command_status").value("pending"))
                .andExpect(jsonPath("$.data.command_id").isNumber()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.size()).isEqualTo(3);
        int id = json.get("data").get("command_id").asInt();
        verify(mqtt).publishDeviceControl("led", "ON", id);
        assertThat(row(id).getUser().getId()).isEqualTo(users.findByUsername("admin").orElseThrow().getId());
    }

    @Test
    void pendingIsCommittedBeforePublishAndEarlyAckIsNeverReverted() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            int id = invocation.getArgument(2);
            assertThat(row(id).getStatus()).isEqualTo("pending");
            ack(id, "led", "success", "ON");
            return null;
        }).when(mqtt).publishDeviceControl(eq("led"), eq("ON"), anyInt());
        int id = send("led", "ON");
        assertThat(row(id).getStatus()).isEqualTo("success");
    }

    @Test
    void publishFailureMarksFailedAndDoesNotChangeConfirmedState() throws Exception {
        int first = send("led", "ON"); ack(first, "led", "ON", "");
        doThrow(new MqttException(32103)).when(mqtt).publishDeviceControl(eq("led"), eq("OFF"), anyInt());
        mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{\"device\":\"led\",\"action\":\"OFF\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
        assertThat(histories.findAll()).extracting(History::getStatus).containsExactlyInAnyOrder("success", "failed");
        assertThat(state("led")).isEqualTo("ON");
    }

    @Test
    void failureAfterEarlyAckCannotDowngradeSuccess() throws Exception {
        doAnswer(invocation -> {
            ack(invocation.getArgument(2), "led", "ON", "");
            throw new MqttException(32103);
        }).when(mqtt).publishDeviceControl(eq("led"), eq("ON"), anyInt());
        assertThatThrownBy(() -> send("led", "ON")).isInstanceOf(ResponseStatusException.class);
        assertThat(histories.findAll().getFirst().getStatus()).isEqualTo("success");
        assertThat(state("led")).isEqualTo("ON");
    }

    @Test
    void offlineBrokerReturns503WithoutCreatingHistory() throws Exception {
        when(mqtt.isReadyForControl()).thenReturn(false);
        mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{\"device\":\"led\",\"action\":\"ON\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        assertThat(histories.count()).isZero();
        verify(mqtt, never()).publishDeviceControl(anyString(), anyString(), anyInt());
    }

    @Test
    void duplicateLateWrongDeviceAndUnknownIdAcksDoNotChangeNewCommand() {
        int first = send("led", "ON"); ack(first, "led", "ON", "");
        int second = send("led", "OFF");
        ack(first, "led", "OFF", "");
        ack(second, "fan", "OFF", "");
        ack(Integer.MAX_VALUE, "led", "OFF", "");
        assertThat(row(first).getStatusReceived()).isEqualTo("ON");
        assertThat(row(second).getStatus()).isEqualTo("pending");
        assertThat(state("led")).isEqualTo("ON");
        ack(second, "led", "OFF", "");
        assertThat(state("led")).isEqualTo("OFF");
        assertThat(histories.count()).isEqualTo(2);
    }

    @Test
    void mismatchAckIsFailedButActualPhysicalStateIsRecorded() {
        int id = send("led", "ON");
        ack(id, "led", "success", "OFF");
        assertThat(row(id).getStatus()).isEqualTo("failed");
        assertThat(state("led")).isEqualTo("OFF");
    }

    @Test
    void failedAckWithoutPhysicalStateKeepsPreviousConfirmation() {
        int first = send("led", "ON"); ack(first, "led", "ON", "");
        int second = send("led", "OFF"); ack(second, "led", "failed", "");
        assertThat(row(second).getStatusReceived()).isNull();
        assertThat(state("led")).isEqualTo("ON");
    }

    @Test
    void timeoutRecoveryRejectsLateAckAndAllowsAnotherCommand() {
        int first = send("led", "ON"); age(first);
        new DeviceCommandTimeoutTask(commands).expirePendingCommands();
        assertThat(row(first).getStatus()).isEqualTo("failed");
        int second = send("led", "OFF");
        ack(first, "led", "ON", "");
        assertThat(row(second).getStatus()).isEqualTo("pending");
        assertThat(state("led")).isEqualTo("UNKNOWN");
        ack(second, "led", "OFF", "");
        assertThat(state("led")).isEqualTo("OFF");
    }

    @Test
    void ackPastDeadlineCannotWinEvenBeforeTimeoutWorkerRuns() {
        int id = send("led", "ON"); age(id);
        ack(id, "led", "ON", "");
        assertThat(row(id).getStatus()).isEqualTo("failed");
        assertThat(row(id).getStatusReceived()).isNull();
    }

    @Test
    void reserveExpiresStalePendingWithoutWaitingForWorker() {
        int first = send("led", "ON"); age(first);
        int second = send("led", "OFF");
        assertThat(row(first).getStatus()).isEqualTo("failed");
        assertThat(row(second).getStatus()).isEqualTo("pending");
    }

    @Test
    void secondPendingRequestReturns409WhileAnotherDeviceCanProceed() throws Exception {
        send("led", "ON");
        mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{\"device\":\"led\",\"action\":\"OFF\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        send("fan", "ON");
        assertThat(histories.count()).isEqualTo(2);
    }

    @Test
    void concurrentRequestsHaveOnlyOnePendingWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> request = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { send("led", "ON"); return 202; }
                catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
            };
            var one = executor.submit(request);
            var two = executor.submit(request);
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(202, 409);
            assertThat(histories.count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "bad", "{\"device\":\"unknown\",\"action\":\"ON\"}",
            "{\"device\":\"led\",\"action\":\"on\"}", "{\"device\":\"led\",\"action\":null}"})
    void invalidRequestReturns400(String body) throws Exception {
        mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(histories.count()).isZero();
    }

    @Test
    void absentDeviceIs404AndBothEndpointsRequireAuthentication() throws Exception {
        var device = devices.findByCode("ac").orElseThrow(); devices.delete(device);
        mvc.perform(post("/api/devices/control").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{\"device\":\"ac\",\"action\":\"ON\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/devices/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/devices/control").contentType("application/json")
                .content("{\"device\":\"led\",\"action\":\"ON\"}")).andExpect(status().isUnauthorized());
    }
}
