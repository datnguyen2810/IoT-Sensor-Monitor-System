package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeviceHistoryResponse(
        Integer id,
        @JsonProperty("command_id") Integer commandId,
        @JsonProperty("device_name") String deviceName,
        @JsonProperty("action_sent") String actionSent,
        @JsonProperty("status_received") String statusReceived,
        @JsonProperty("command_status") String commandStatus,
        String operator,
        @JsonProperty("executed_at") String executedAt) { }
