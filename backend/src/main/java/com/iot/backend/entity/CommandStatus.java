package com.iot.backend.entity;

/** Kết quả thực hiện lệnh; độc lập với trạng thái ON/OFF của thiết bị. */
public enum CommandStatus {
    SUCCESS("success"), FAILED("failed"), PENDING("pending");

    private final String value;

    CommandStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
