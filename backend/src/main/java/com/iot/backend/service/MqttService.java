package com.iot.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.backend.config.MqttConfig;
import com.iot.backend.entity.DataSensor;
import com.iot.backend.entity.History;
import com.iot.backend.entity.Sensor;
import com.iot.backend.repository.DataSensorRepository;
import com.iot.backend.repository.DeviceRepository;
import com.iot.backend.repository.HistoryRepository;
import com.iot.backend.repository.SensorRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Service quản lý kết nối MQTT với Mosquitto Broker.
 * - Subscribe: iot/sensor/data (dữ liệu cảm biến từ ESP8266)
 * - Subscribe: iot/status (phản hồi trạng thái thiết bị từ ESP8266)
 * - Publish: iot/control (gửi lệnh điều khiển xuống ESP8266)
 *
 * Triển khai MqttCallbackExtended để tự động subscribe lại khi reconnect.
 */
@Service
public class MqttService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttService.class);

    // Topics MQTT theo firmware ESP8266
    private static final String TOPIC_SENSOR_DATA = "iot/sensor/data";
    private static final String TOPIC_DEVICE_STATUS = "iot/status";
    private static final String TOPIC_DEVICE_CONTROL = "iot/control";

    // Sensor IDs khớp với data.sql seed
    private static final int SENSOR_ID_TEMPERATURE = 1;
    private static final int SENSOR_ID_HUMIDITY = 2;
    private static final int SENSOR_ID_LIGHT = 3;

    private final MqttConfig mqttConfig;
    private final MqttConnectOptions mqttConnectOptions;
    private final ObjectMapper objectMapper;
    private final DataSensorRepository dataSensorRepository;
    private final SensorRepository sensorRepository;
    private final DeviceRepository deviceRepository;
    private final HistoryRepository historyRepository;

    private MqttClient mqttClient;

    public MqttService(MqttConfig mqttConfig,
                       MqttConnectOptions mqttConnectOptions,
                       ObjectMapper objectMapper,
                       DataSensorRepository dataSensorRepository,
                       SensorRepository sensorRepository,
                       DeviceRepository deviceRepository,
                       HistoryRepository historyRepository) {
        this.mqttConfig = mqttConfig;
        this.mqttConnectOptions = mqttConnectOptions;
        this.objectMapper = objectMapper;
        this.dataSensorRepository = dataSensorRepository;
        this.sensorRepository = sensorRepository;
        this.deviceRepository = deviceRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * Khởi tạo kết nối MQTT khi ứng dụng Spring Boot khởi động.
     */
    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(mqttConfig.getBrokerUrl(), mqttConfig.getClientId());
            mqttClient.setCallback(this);
            mqttClient.connect(mqttConnectOptions);
            log.info("Đã kết nối MQTT Broker: {}", mqttConfig.getBrokerUrl());
        } catch (MqttException e) {
            log.error("Không thể kết nối MQTT Broker: {}", e.getMessage(), e);
        }
    }

    /**
     * Ngắt kết nối MQTT khi ứng dụng tắt.
     */
    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("Đã ngắt kết nối MQTT Broker");
            }
        } catch (MqttException e) {
            log.error("Lỗi khi ngắt kết nối MQTT: {}", e.getMessage(), e);
        }
    }

    // ==================== MqttCallbackExtended ====================

    /**
     * Được gọi khi kết nối (hoặc kết nối lại) thành công.
     * Subscribe lại các topic cần thiết.
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        String action = reconnect ? "Kết nối lại" : "Kết nối lần đầu";
        log.info("MQTT {}: {}", action, serverURI);
        subscribeTopics();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Mất kết nối MQTT: {}. Đang thử kết nối lại...", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.debug("MQTT nhận [{}]: {}", topic, payload);

        try {
            switch (topic) {
                case TOPIC_SENSOR_DATA -> handleSensorData(payload);
                case TOPIC_DEVICE_STATUS -> handleDeviceStatus(payload);
                default -> log.warn("Topic không xử lý: {}", topic);
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý message MQTT [{}]: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Không cần xử lý, chỉ log khi cần debug
    }

    // ==================== Subscribe & Publish ====================

    /**
     * Subscribe vào các topic cần thiết.
     */
    private void subscribeTopics() {
        try {
            mqttClient.subscribe(TOPIC_SENSOR_DATA, 1);
            mqttClient.subscribe(TOPIC_DEVICE_STATUS, 1);
            log.info("Đã subscribe: {}, {}", TOPIC_SENSOR_DATA, TOPIC_DEVICE_STATUS);
        } catch (MqttException e) {
            log.error("Lỗi subscribe MQTT topics: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish message xuống topic MQTT.
     * Được gọi bởi DeviceService khi frontend gửi lệnh điều khiển.
     *
     * @param topic   Topic MQTT (ví dụ: iot/control)
     * @param payload Nội dung JSON (ví dụ: {"device": "led", "cmd": "ON"})
     */
    public void publish(String topic, String payload) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        mqttClient.publish(topic, message);
        log.info("MQTT publish [{}]: {}", topic, payload);
    }

    /**
     * Publish lệnh điều khiển thiết bị xuống ESP8266.
     * Chuyển đổi field HTTP "action" thành field MQTT "cmd" theo plan.
     *
     * @param deviceCode Mã thiết bị (led/fan/ac)
     * @param action     Hành động (ON/OFF)
     */
    public void publishDeviceControl(String deviceCode, String action) throws MqttException {
        String payload = String.format("{\"device\": \"%s\", \"cmd\": \"%s\"}", deviceCode, action);
        publish(TOPIC_DEVICE_CONTROL, payload);
    }

    // ==================== Xử lý dữ liệu ====================

    /**
     * Xử lý dữ liệu cảm biến từ ESP8266.
     * Payload: {"temperature": 28.5, "humidity": 65.0, "light": 650}
     * Lưu 3 bản ghi vào bảng datasensors với cùng 1 timestamp.
     */
    private void handleSensorData(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            LocalDateTime timestamp = LocalDateTime.now();

            // Lấy giá trị từ JSON
            float temperature = (float) json.get("temperature").asDouble();
            float humidity = (float) json.get("humidity").asDouble();
            float light = (float) json.get("light").asDouble();

            // Lấy Sensor reference từ DB (có cache trong Hibernate L1)
            Sensor tempSensor = sensorRepository.getReferenceById(SENSOR_ID_TEMPERATURE);
            Sensor humiSensor = sensorRepository.getReferenceById(SENSOR_ID_HUMIDITY);
            Sensor lightSensor = sensorRepository.getReferenceById(SENSOR_ID_LIGHT);

            // Tạo 3 bản ghi DataSensor cùng timestamp
            List<DataSensor> records = Arrays.asList(
                    DataSensor.builder()
                            .sensor(tempSensor)
                            .value(temperature)
                            .createdAt(timestamp)
                            .build(),
                    DataSensor.builder()
                            .sensor(humiSensor)
                            .value(humidity)
                            .createdAt(timestamp)
                            .build(),
                    DataSensor.builder()
                            .sensor(lightSensor)
                            .value(light)
                            .createdAt(timestamp)
                            .build()
            );

            dataSensorRepository.saveAll(records);
            log.info("Lưu dữ liệu cảm biến: temp={}, humi={}, light={}", temperature, humidity, light);

        } catch (Exception e) {
            log.error("Lỗi parse dữ liệu cảm biến: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý phản hồi trạng thái thiết bị từ ESP8266.
     * Payload: {"device": "led", "status": "ON"}
     * Cập nhật bản ghi history gần nhất có status = PENDING/SENT sang ON/OFF.
     */
    private void handleDeviceStatus(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            String deviceCode = json.get("device").asText();
            String status = json.get("status").asText();

            // Tìm bản ghi history gần nhất đang PENDING/SENT của thiết bị này
            historyRepository
                    .findTopByDeviceCodeAndStatusInOrderByCreatedAtDesc(
                            deviceCode, Arrays.asList("PENDING", "SENT"))
                    .ifPresentOrElse(
                            history -> {
                                history.setStatus(status);
                                historyRepository.save(history);
                                log.info("Cập nhật trạng thái {}: {} → {}", deviceCode, "PENDING/SENT", status);
                            },
                            () -> log.warn("Không tìm thấy bản ghi PENDING/SENT cho thiết bị: {}", deviceCode)
                    );

        } catch (Exception e) {
            log.error("Lỗi parse trạng thái thiết bị: {}", e.getMessage(), e);
        }
    }
}
