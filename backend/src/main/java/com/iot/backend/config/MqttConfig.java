package com.iot.backend.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tham số kết nối MQTT Mosquitto Broker.
 * Đọc từ application.properties: mqtt.broker.url, mqtt.client.id, mqtt.username, mqtt.password.
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());

        // Tự động kết nối lại khi mất kết nối với broker
        options.setAutomaticReconnect(true);

        // Không nhớ session cũ, luôn tạo session mới
        options.setCleanSession(true);

        // Timeout kết nối 10 giây
        options.setConnectionTimeout(10);

        // Gửi heartbeat mỗi 20 giây để duy trì kết nối
        options.setKeepAliveInterval(20);

        return options;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }
}
