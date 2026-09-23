package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * DTO đại diện cho một bản ghi trong bảng lịch sử đo cảm biến (Data Sensor).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorHistoryResponse {

    private Integer id;

    @JsonProperty("sensor_name")
    private String sensorName;

    private Float value;

    private String unit;

    @JsonProperty("created_at")
    private String createdAt;
}
