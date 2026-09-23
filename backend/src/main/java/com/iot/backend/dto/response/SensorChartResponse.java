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

    private Float temperature;

    private Float humidity;

    private Float light;
}
