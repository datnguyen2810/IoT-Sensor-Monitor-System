package com.iot.backend;

import com.iot.backend.config.MqttConfig;
import com.iot.backend.entity.History;
import com.iot.backend.repository.*;
import com.iot.backend.service.MqttService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MqttCommandStatusTests {
    private final HistoryRepository histories = mock(HistoryRepository.class);
    private final MqttService service = new MqttService(mock(MqttConfig.class), new MqttConnectOptions(),
            JsonMapper.builder().build(), mock(DataSensorRepository.class), mock(SensorRepository.class),
            mock(DeviceRepository.class), histories);

    @ParameterizedTest
    @CsvSource({"ON,ON,success,ON", "ON,OFF,failed,OFF", "OFF,OFF,success,OFF",
            "ON,success,success,ON", "ON,failed,failed,NULL", "ON,pending,pending,NULL"})
    void mapsAcknowledgementsToThreeCommandStates(String action, String ack, String expected, String received) {
        History history = History.builder().action(action).build();
        when(histories.findTopByDeviceCodeAndStatusInOrderByCreatedAtDesc("led", List.of("pending")))
                .thenReturn(Optional.of(history));
        receive("{\"device\":\"led\",\"status\":\"" + ack + "\"}");
        assertThat(history.getStatus()).isEqualTo(expected);
        assertThat(history.getStatusReceived()).isEqualTo("NULL".equals(received) ? null : received);
        verify(histories).save(history);
    }

    @Test
    void successWithWrongDeviceStateIsFailed() {
        History history = History.builder().action("ON").build();
        when(histories.findTopByDeviceCodeAndStatusInOrderByCreatedAtDesc("led", List.of("pending")))
                .thenReturn(Optional.of(history));
        receive("{\"device\":\"led\",\"status\":\"success\",\"device_status\":\"OFF\"}");
        assertThat(history.getStatus()).isEqualTo("failed");
        assertThat(history.getStatusReceived()).isEqualTo("OFF");
    }

    @Test
    void malformedAndUnknownStatusDoNotChangeHistory() {
        receive("{\"device\":\"led\",\"status\":\"invalid\"}");
        receive("{\"device\":\"led\",\"status\":\"success\",\"device_status\":\"invalid\"}");
        receive("{\"status\":\"success\"}");
        verifyNoInteractions(histories);
    }

    @Test
    void acknowledgementWithoutPendingCommandDoesNotCreateHistory() {
        when(histories.findTopByDeviceCodeAndStatusInOrderByCreatedAtDesc("led", List.of("pending")))
                .thenReturn(Optional.empty());
        receive("{\"device\":\"led\",\"status\":\"success\"}");
        verify(histories, never()).save(any());
    }

    private void receive(String payload) {
        service.messageArrived("iot/status", new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
