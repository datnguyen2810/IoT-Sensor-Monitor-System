package com.iot.backend.service;

import com.iot.backend.dto.mqtt.DeviceAcknowledgement;
import com.iot.backend.dto.response.DeviceControlResponse;
import com.iot.backend.dto.response.DeviceStatusResponse;
import com.iot.backend.entity.History;
import com.iot.backend.repository.DeviceRepository;
import com.iot.backend.repository.HistoryRepository;
import com.iot.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Short, independent DB transactions. Never hold a transaction while publishing/waiting for MQTT. */
@Service
public class DeviceCommandTransactions {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final DeviceRepository devices;
    private final HistoryRepository histories;
    private final UserRepository users;
    private final Duration timeout;

    public DeviceCommandTransactions(DeviceRepository devices, HistoryRepository histories, UserRepository users,
                                     @Value("${devices.command.timeout-ms:5000}") long timeoutMs) {
        if (timeoutMs <= 0) throw new IllegalArgumentException("Command timeout must be positive");
        this.devices = devices;
        this.histories = histories;
        this.users = users;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    private LocalDateTime now() { return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS); }

    @Transactional
    public DeviceControlResponse reserve(String code, String action, String username) {
        var device = devices.findByCodeForUpdate(code).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị"));
        // Recover stale commands on restart, and let a fresh request proceed after an expired command.
        histories.failExpiredPending(device.getId(), now().minus(timeout));
        if (histories.existsByDeviceIdAndStatus(device.getId(), "pending")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thiết bị đang có lệnh chờ phản hồi");
        }
        var user = users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn tồn tại"));
        var history = histories.saveAndFlush(History.builder().device(device).user(user).action(action)
                .status("pending").createdAt(now()).build());
        return new DeviceControlResponse(history.getId(), code, action, "pending");
    }

    @Transactional
    public void failPublish(DeviceControlResponse receipt) {
        devices.findByCodeForUpdate(receipt.device())
                .ifPresent(device -> histories.failPending(receipt.commandId(), device.getId()));
    }

    @Transactional
    public void acknowledge(DeviceAcknowledgement ack) {
        var device = devices.findByCodeForUpdate(ack.device());
        if (device.isEmpty()) return;
        var command = histories.findByIdAndDeviceId(ack.commandId(), device.get().getId());
        if (command.isEmpty() || !"pending".equals(command.get().getStatus())) return;
        History history = command.get();
        if (!history.getCreatedAt().plus(timeout).isAfter(now())) {
            history.setStatus("failed");
            return; // A late ACK never revives a timed-out command or alters the confirmed state.
        }
        if ("pending".equals(ack.status())) return;
        boolean legacy = List.of("ON", "OFF").contains(ack.status());
        String received = legacy ? ack.status() : ack.deviceStatus();
        String result = ack.status();
        if (legacy || "success".equals(result)) {
            if (received.isEmpty()) received = history.getAction();
            result = history.getAction().equals(received) ? "success" : "failed";
        }
        history.setStatus(result);
        if (!received.isEmpty()) history.setStatusReceived(received);
        // Dirty checking commits state and command result together. No Device column is added.
    }

    @Transactional(readOnly = true)
    public List<DeviceStatusResponse> statuses() {
        return devices.findAll(Sort.by("id")).stream().map(device -> {
            String state = histories.findTopByDeviceIdAndStatusReceivedIsNotNullAndStatusInOrderByCreatedAtDescIdDesc(
                    device.getId(), List.of("success", "failed"))
                    .map(History::getStatusReceived).filter(value -> List.of("ON", "OFF").contains(value))
                    .orElse("UNKNOWN");
            return new DeviceStatusResponse(device.getCode(), state, null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<String> expiredDeviceCodes() {
        return histories.findExpiredPendingDeviceCodes(now().minus(timeout));
    }

    @Transactional
    public void expire(String code) {
        devices.findByCodeForUpdate(code)
                .ifPresent(device -> histories.failExpiredPending(device.getId(), now().minus(timeout)));
    }
}
