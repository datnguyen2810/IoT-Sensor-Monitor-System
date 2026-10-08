package com.iot.backend.service;

import com.iot.backend.dto.mqtt.SensorMeasurementPayload;
import com.iot.backend.entity.DataSensor;
import com.iot.backend.repository.DataSensorRepository;
import com.iot.backend.repository.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SensorMeasurementService {
    private final SensorRepository sensors;
    private final DataSensorRepository records;

    public SensorMeasurementService(SensorRepository sensors, DataSensorRepository records) {
        this.sensors = sensors;
        this.records = records;
    }

    /** Invoked through the Spring proxy: all three rows commit or roll back together. */
    @Transactional
    public void persist(SensorMeasurementPayload payload) {
        LocalDateTime time = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).truncatedTo(ChronoUnit.MICROS);
        float[] values = {payload.temperature(), payload.humidity(), payload.light()};
        var batch = new java.util.ArrayList<DataSensor>(3);
        for (int sensorId : List.of(1, 2, 3)) {
            batch.add(DataSensor.builder().sensor(sensors.findById(sensorId).orElseThrow(
                            () -> new IllegalStateException("Missing seeded sensor")))
                    .value(values[sensorId - 1]).createdAt(time).build());
        }
        records.saveAllAndFlush(batch);
    }
}
