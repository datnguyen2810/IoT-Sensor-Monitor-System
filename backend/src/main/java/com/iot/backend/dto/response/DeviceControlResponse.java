package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Receipt of acceptance, not a claim that hardware has executed the command. */
public record DeviceControlResponse(@JsonProperty("command_id") Integer commandId,
                                    String device, String action,
                                    @JsonProperty("command_status") String commandStatus) { }
