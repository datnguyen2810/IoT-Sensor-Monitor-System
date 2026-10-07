package com.iot.backend.dto.response;

import lombok.*;

/**
 * DTO điểm đo cảm biến phục vụ vẽ biểu đồ Chart.js trên Dashboard.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorChartResponse {

    private String time;

    // Khóa đầy đủ để latest/chart không nhầm các lần đo khác ngày.
    private String timestamp;

    private Float temperature;

    private Float humidity;

    private Float light;
}
