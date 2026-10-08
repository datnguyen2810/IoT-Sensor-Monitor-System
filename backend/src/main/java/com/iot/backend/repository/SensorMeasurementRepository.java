package com.iot.backend.repository;

import com.iot.backend.entity.SensorMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorMeasurementRepository extends JpaRepository<SensorMeasurement, String> { }
