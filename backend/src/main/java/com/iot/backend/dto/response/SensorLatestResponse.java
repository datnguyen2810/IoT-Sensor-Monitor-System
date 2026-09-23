package com.iot.backend.dto.response;

import lombok.*;

/**
 * DTO trả về giá trị mới nhất của 3 thông số cảm biến phục vụ Dashboard polling.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorLatestResponse {

    private Float temperature;

    private Float humidity;

    private Float light;

    private String timestamp;
}
