package com.iot.backend.service;

import com.iot.backend.dto.mqtt.SensorMeasurementPayload;
import com.iot.backend.entity.CommandStatus;
import com.iot.backend.repository.HistoryRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttService implements MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(MqttService.class);
    private static final String TOPIC_SENSOR_DATA = "iot/sensor/data";
    private static final String TOPIC_DEVICE_STATUS = "iot/status";
    private static final String TOPIC_DEVICE_CONTROL = "iot/control";
    private final IMqttClient mqttClient;
    private final MqttConnectOptions mqttConnectOptions;
    private final ObjectMapper objectMapper;
    private final SensorMeasurementService measurements;
    private final HistoryRepository historyRepository;
    private final ScheduledExecutorService connectionExecutor;
    private volatile boolean stopping;
    private volatile boolean subscribed;
    private ScheduledFuture<?> connectionTask;

    public MqttService(IMqttClient mqttClient, MqttConnectOptions mqttConnectOptions,
                       ObjectMapper objectMapper, SensorMeasurementService measurements,
                       HistoryRepository historyRepository, ScheduledExecutorService connectionExecutor) {
        this.mqttClient = mqttClient;
        this.mqttConnectOptions = mqttConnectOptions;
        this.objectMapper = objectMapper;
        this.measurements = measurements;
        this.historyRepository = historyRepository;
        this.connectionExecutor = connectionExecutor;
    }

    @PostConstruct
    public void init() {
        mqttClient.setCallback(this);
        // No blocking broker connection on Spring's startup thread.
        connectionTask = connectionExecutor.scheduleWithFixedDelay(this::ensureConnection, 0, 5, TimeUnit.SECONDS);
    }

    private synchronized void ensureConnection() {
        if (stopping) return;
        try {
            if (!mqttClient.isConnected()) {
                subscribed = false;
                mqttClient.connect(mqttConnectOptions);
                log.info("MQTT connected");
            }
            if (!subscribed) {
                mqttClient.subscribe(new String[]{TOPIC_SENSOR_DATA, TOPIC_DEVICE_STATUS}, new int[]{1, 1});
                subscribed = true;
                log.info("MQTT subscribed to sensor data and device status");
            }
        } catch (MqttException | RuntimeException exception) {
            // Catch in the scheduled task so one failure cannot cancel all future attempts.
            subscribed = false;
            log.warn("MQTT connection/subscription failed; retry in 5 seconds ({}: {})",
                    exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        stopping = true;
        if (connectionTask != null) connectionTask.cancel(true);
        connectionExecutor.shutdownNow();
        // Serialized against an in-flight connect attempt, including shutdown while broker is offline.
        synchronized (this) {
            try {
                if (mqttClient.isConnected()) mqttClient.disconnect();
            } catch (MqttException exception) {
                log.warn("MQTT disconnect failed: {}", exception.getMessage());
                try {
                    mqttClient.disconnectForcibly(1000, 1000);
                } catch (MqttException forcedException) {
                    log.warn("MQTT forced disconnect failed: {}", forcedException.getMessage());
                }
            } finally {
                try {
                    mqttClient.close();
                } catch (MqttException exception) {
                    log.warn("MQTT close failed: {}", exception.getMessage());
                }
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // Subscribe on the connection worker, not in Paho's callback (avoids callback deadlocks).
        subscribed = false;
    }

    @Override
    public void connectionLost(Throwable cause) {
        subscribed = false;
        log.warn("MQTT connection lost; retry scheduled: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        final String payload;
        try {
            payload = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(message.getPayload())).toString();
        } catch (CharacterCodingException exception) {
            log.warn("Ignoring MQTT message with invalid UTF-8 on {}", topic);
            return;
        }
        switch (topic) {
            case TOPIC_SENSOR_DATA -> handleSensorData(payload);
            case TOPIC_DEVICE_STATUS -> handleDeviceStatus(payload);
            default -> log.debug("Ignoring MQTT topic {}", topic);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { }

    public void publish(String topic, String payload) throws MqttException {
        if (stopping || !mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(false);
        mqttClient.publish(topic, message);
        log.debug("MQTT published on {}", topic);
    }

    public void publishDeviceControl(String deviceCode, String action) throws MqttException {
        publish(TOPIC_DEVICE_CONTROL, objectMapper.writeValueAsString(Map.of("device", deviceCode, "cmd", action)));
    }

    private void handleSensorData(String payload) {
        final SensorMeasurementPayload measurement;
        try {
            measurement = SensorMeasurementPayload.parse(objectMapper, payload);
        } catch (RuntimeException exception) {
            log.warn("Ignoring malformed sensor payload: {}", exception.getClass().getSimpleName());
            return;
        }
        try {
            boolean saved = measurements.persist(measurement);
            log.debug(saved ? "Stored sensor measurement" : "Ignored duplicate sensor measurement");
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring conflicting measurement_id");
        } catch (RuntimeException exception) {
            // Do not silently acknowledge a database failure as a successful ingestion.
            log.error("Sensor measurement transaction failed", exception);
            throw exception;
        }
    }

    // Existing ACK behavior remains unchanged; command correlation belongs to phase 5.
    private void handleDeviceStatus(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            String deviceCode = json.path("device").asText("");
            String status = json.path("status").asText("");
            String deviceStatus = json.path("device_status").asText("");
            boolean legacyAck = "ON".equals(status) || "OFF".equals(status);
            if (deviceCode.isBlank() || (!legacyAck && !List.of("success", "failed", "pending").contains(status))) {
                log.warn("Bỏ qua phản hồi thiết bị có device/status không hợp lệ");
                return;
            }
            if (!deviceStatus.isEmpty() && !List.of("ON", "OFF").contains(deviceStatus)) {
                log.warn("Bỏ qua phản hồi có device_status không hợp lệ");
                return;
            }

            // Ghép theo lệnh pending gần nhất; correlation ID triển khai ở giai đoạn điều khiển.
            historyRepository
                    .findTopByDeviceCodeAndStatusInOrderByCreatedAtDesc(
                            deviceCode, List.of(CommandStatus.PENDING.getValue()))
                    .ifPresentOrElse(
                            history -> {
                                String received = legacyAck ? status : deviceStatus;
                                String result = status;
                                if (legacyAck) {
                                    result = history.getAction().equals(received)
                                            ? CommandStatus.SUCCESS.getValue() : CommandStatus.FAILED.getValue();
                                } else if (CommandStatus.SUCCESS.getValue().equals(status)) {
                                    // ACK success không gửi trạng thái: thiết bị xác nhận đã thực hiện action.
                                    if (received.isEmpty()) received = history.getAction();
                                    if (!history.getAction().equals(received)) result = CommandStatus.FAILED.getValue();
                                }
                                history.setStatus(result);
                                if (!received.isEmpty()) history.setStatusReceived(received);
                                historyRepository.save(history);
                                log.info("Kết quả lệnh {}: pending → {}", deviceCode, result);
                            },
                            () -> log.warn("Không tìm thấy lệnh pending cho thiết bị: {}", deviceCode)
                    );

        } catch (Exception e) {
            log.error("Lỗi parse trạng thái thiết bị: {}", e.getMessage(), e);
        }
    }
}
