package com.iot.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "datasensors", indexes = {
        @Index(name = "idx_datasensors_created_id", columnList = "created_at,id"),
        @Index(name = "idx_datasensors_sensor_created", columnList = "sensor_id,created_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_datasensors_measurement_sensor", columnNames = {"measurement_id", "sensor_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    // Nullable to keep existing timestamp-only measurements readable.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_id", foreignKey = @ForeignKey(name = "fk_datasensors_measurement"))
    private SensorMeasurement measurement;

    @Column(name = "value", nullable = false)
    private Float value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
