package com.iot.backend.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Cấu hình tham số kết nối MQTT Mosquitto Broker.
 * Đọc từ application.properties: mqtt.broker.url, mqtt.client.id, mqtt.username, mqtt.password.
 */
@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
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

        // One retry owner in MqttService, including initial connection and failed subscriptions.
        options.setAutomaticReconnect(false);

        // Keep the stable client session so unacknowledged QoS 1 messages can be redelivered.
        // Resubscription is still performed after every connection (broker may have restarted).
        options.setCleanSession(false);

        // Timeout kết nối 10 giây
        options.setConnectionTimeout(10);

        // Gửi heartbeat mỗi 20 giây để duy trì kết nối
        options.setKeepAliveInterval(20);

        return options;
    }

    @Bean(destroyMethod = "")
    public IMqttClient mqttClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        // Bound synchronous subscribe/publish waits as well as the connect timeout above.
        client.setTimeToWait(10_000);
        return client;
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService mqttConnectionExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mqtt-connection");
            thread.setDaemon(true);
            return thread;
        });
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }
}
