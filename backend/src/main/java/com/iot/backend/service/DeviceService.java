package com.iot.backend.service;

import com.iot.backend.dto.request.DeviceControlRequest;
import com.iot.backend.dto.response.DeviceControlResponse;
import com.iot.backend.dto.response.DeviceStatusResponse;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class DeviceService {
    private final DeviceCommandTransactions commands;
    private final ObjectProvider<MqttService> mqttProvider;

    public DeviceService(DeviceCommandTransactions commands, ObjectProvider<MqttService> mqttProvider) {
        this.commands = commands;
        this.mqttProvider = mqttProvider;
    }

    public List<DeviceStatusResponse> getStatuses() { return commands.statuses(); }

    public DeviceControlResponse control(DeviceControlRequest request, String username) {
        if (request == null || request.device() == null || request.action() == null
                || !List.of("led", "fan", "ac").contains(request.device())
                || !List.of("ON", "OFF").contains(request.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lệnh điều khiển không hợp lệ");
        }
        if (username == null || username.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        MqttService mqtt = mqttProvider.getIfAvailable();
        if (mqtt == null || !mqtt.isReadyForControl()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT chưa sẵn sàng");
        }
        DeviceControlResponse receipt = commands.reserve(request.device(), request.action(), username);
        try {
            mqtt.publishDeviceControl(receipt.device(), receipt.action(), receipt.commandId());
        } catch (MqttException | RuntimeException exception) {
            commands.failPublish(receipt); // Conditional: an early success ACK must not be downgraded.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không gửi được lệnh MQTT", exception);
        }
        return receipt;
    }
}
