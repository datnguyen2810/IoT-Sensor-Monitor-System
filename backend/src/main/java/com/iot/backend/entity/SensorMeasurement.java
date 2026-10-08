package com.iot.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** One atomic batch; the primary key also arbitrates concurrent duplicate deliveries. */
@Entity
@Table(name = "sensor_measurements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SensorMeasurement {
    @Id
    @Column(length = 128)
    private String id;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private Float temperature;
    @Column(nullable = false)
    private Float humidity;
    @Column(nullable = false)
    private Float light;
}
