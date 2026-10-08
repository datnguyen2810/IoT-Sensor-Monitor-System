package com.iot.backend.repository;

import com.iot.backend.entity.DataSensor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataSensorRepository extends JpaRepository<DataSensor, Integer>, JpaSpecificationExecutor<DataSensor> {

    interface MeasurementKey {
        String getMeasurementId();
        LocalDateTime getCreatedAt();
        Integer getLastId();
    }

    @Query("""
            SELECT m.id AS measurementId, d.createdAt AS createdAt, MAX(d.id) AS lastId
            FROM DataSensor d LEFT JOIN d.measurement m
            WHERE d.sensor.id IN (1, 2, 3)
            GROUP BY m.id, d.createdAt
            HAVING COUNT(d) = 3 AND COUNT(DISTINCT d.sensor.id) = 3
            ORDER BY d.createdAt DESC, MAX(d.id) DESC
            """)
    List<MeasurementKey> findCompleteMeasurements(Pageable pageable);

    /**
     * Lấy giá trị đo mới nhất của một loại cảm biến cụ thể (nhiệt độ / độ ẩm / ánh sáng)
     */
    Optional<DataSensor> findTopBySensorIdOrderByCreatedAtDesc(Integer sensorId);

    /**
     * Lấy danh sách các mốc thời gian đo duy nhất gần nhất phục vụ đồ thị
     */
    @Query("SELECT DISTINCT d.createdAt FROM DataSensor d ORDER BY d.createdAt DESC")
    Page<LocalDateTime> findDistinctRecentCreatedAt(Pageable pageable);

    /** Chỉ lấy các lần đo đủ ba sensor; loại timestamp có bản ghi trùng để tránh ghép sai. */
    @Query("""
            SELECT d.createdAt FROM DataSensor d
            WHERE d.sensor.id IN (1, 2, 3)
            GROUP BY d.createdAt
            HAVING COUNT(d) = 3 AND COUNT(DISTINCT d.sensor.id) = 3
            ORDER BY d.createdAt DESC
            """)
    List<LocalDateTime> findCompleteMeasurementTimes(Pageable pageable);

    /**
     * Lấy danh sách các bản ghi tương ứng với tập hợp các mốc thời gian
     */
    @Query("SELECT d FROM DataSensor d JOIN FETCH d.sensor WHERE d.sensor.id IN (1, 2, 3) AND d.createdAt IN :timestamps ORDER BY d.createdAt ASC, d.id ASC")
    List<DataSensor> findByCreatedAtInOrderByCreatedAtAsc(@Param("timestamps") Collection<LocalDateTime> timestamps);

    /**
     * Tìm kiếm bản ghi theo loại cảm biến có phân trang
     */
    Page<DataSensor> findBySensorId(Integer sensorId, Pageable pageable);
}
