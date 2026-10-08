package com.iot.backend.dto.mqtt;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

public record DeviceAcknowledgement(int commandId, String device, String status, String deviceStatus) {
    public DeviceAcknowledgement {
        if (commandId <= 0 || device == null || status == null || deviceStatus == null
                || !List.of("led", "fan", "ac").contains(device)
                || !List.of("ON", "OFF", "success", "failed", "pending").contains(status)
                || (!deviceStatus.isEmpty() && !List.of("ON", "OFF").contains(deviceStatus))
                || (List.of("ON", "OFF").contains(status) && !deviceStatus.isEmpty() && !status.equals(deviceStatus))) {
            throw new IllegalArgumentException("Invalid device acknowledgement");
        }
    }

    public static DeviceAcknowledgement parse(ObjectMapper mapper, String payload) {
        JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION).readTree(payload);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Expected JSON object");
        JsonNode id = root.get("command_id");
        if (id == null || !id.isIntegralNumber() || id.asDouble() <= 0 || id.asDouble() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected a positive integer command_id");
        }
        JsonNode state = root.get("device_status");
        return new DeviceAcknowledgement(id.asInt(), string(root.get("device")), string(root.get("status")),
                state == null ? "" : string(state));
    }

    private static String string(JsonNode node) {
        if (node == null || !node.isString()) throw new IllegalArgumentException("Expected string");
        return node.asString();
    }
}
