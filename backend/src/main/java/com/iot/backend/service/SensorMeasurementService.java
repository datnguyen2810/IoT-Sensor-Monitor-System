package com.iot.backend.service;

import com.iot.backend.dto.mqtt.SensorMeasurementPayload;
import com.iot.backend.entity.DataSensor;
import com.iot.backend.entity.SensorMeasurement;
import com.iot.backend.repository.DataSensorRepository;
import com.iot.backend.repository.SensorMeasurementRepository;
import com.iot.backend.repository.SensorRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SensorMeasurementService {
    private final SensorMeasurementRepository measurements;
    private final SensorRepository sensors;
    private final DataSensorRepository records;
    private final EntityManager entityManager;

    public SensorMeasurementService(SensorMeasurementRepository measurements, SensorRepository sensors,
                                    DataSensorRepository records, EntityManager entityManager) {
        this.measurements = measurements;
        this.sensors = sensors;
        this.records = records;
        this.entityManager = entityManager;
    }

    /** Invoked through the Spring proxy: batch metadata and all three rows commit or roll back together. */
    @Transactional
    public boolean persist(SensorMeasurementPayload payload) {
        String id = payload.measurementId() == null ? "generated:" + UUID.randomUUID() : "firmware:" + payload.measurementId();
        var existing = measurements.findById(id);
        if (existing.isPresent()) {
            SensorMeasurement previous = existing.get();
            if (Float.compare(previous.getTemperature(), payload.temperature()) != 0
                    || Float.compare(previous.getHumidity(), payload.humidity()) != 0
                    || Float.compare(previous.getLight(), payload.light()) != 0) {
                throw new IllegalArgumentException("measurement_id was reused with different values");
            }
            return false;
        }
        LocalDateTime time = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).truncatedTo(ChronoUnit.MICROS);
        SensorMeasurement measurement = SensorMeasurement.builder().id(id).createdAt(time)
                .temperature(payload.temperature()).humidity(payload.humidity()).light(payload.light()).build();
        // persist (not merge) ensures a concurrent identical ID cannot overwrite an existing batch.
        entityManager.persist(measurement);
        entityManager.flush();
        float[] values = {payload.temperature(), payload.humidity(), payload.light()};
        var batch = new java.util.ArrayList<DataSensor>(3);
        for (int sensorId : List.of(1, 2, 3)) {
            batch.add(DataSensor.builder().sensor(sensors.findById(sensorId).orElseThrow(
                            () -> new IllegalStateException("Missing seeded sensor")))
                    .measurement(measurement).value(values[sensorId - 1]).createdAt(time).build());
        }
        records.saveAllAndFlush(batch);
        return true;
    }
}
