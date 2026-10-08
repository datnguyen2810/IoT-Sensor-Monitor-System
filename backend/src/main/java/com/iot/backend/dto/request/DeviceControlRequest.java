package com.iot.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceControlRequest(
        @NotBlank @Pattern(regexp = "led|fan|ac") String device,
        @NotBlank @Pattern(regexp = "ON|OFF") String action) { }
