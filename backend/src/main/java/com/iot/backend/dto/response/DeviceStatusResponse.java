package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** last_seen stays null: the unchanged schema has no ACK/heartbeat timestamp. */
public record DeviceStatusResponse(String device, String status,
                                   @JsonProperty("last_seen") String lastSeen) { }
