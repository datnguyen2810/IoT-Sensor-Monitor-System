package com.iot.backend.repository;

import com.iot.backend.entity.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface HistoryRepository extends JpaRepository<History, Integer> {
    boolean existsByDeviceIdAndStatus(Integer deviceId, String status);

    Optional<History> findByIdAndDeviceId(Integer id, Integer deviceId);

    Optional<History> findTopByDeviceIdAndStatusReceivedIsNotNullAndStatusInOrderByCreatedAtDescIdDesc(
            Integer deviceId, Collection<String> statuses);

    @Query("SELECT DISTINCT h.device.code FROM History h WHERE h.status = 'pending' AND h.createdAt <= :cutoff")
    List<String> findExpiredPendingDeviceCodes(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE History h SET h.status = 'failed' WHERE h.device.id = :deviceId "
            + "AND h.status = 'pending' AND h.createdAt <= :cutoff")
    int failExpiredPending(@Param("deviceId") Integer deviceId, @Param("cutoff") LocalDateTime cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE History h SET h.status = 'failed' WHERE h.id = :id "
            + "AND h.device.id = :deviceId AND h.status = 'pending'")
    int failPending(@Param("id") Integer id, @Param("deviceId") Integer deviceId);

    /**
     * Lấy lệnh gần nhất theo deviceId và kết quả success/failed/pending.
     * Khi cần trạng thái thiết bị, đọc statusReceived của lệnh đã xác nhận.
     */
    Optional<History> findTopByDeviceIdAndStatusInOrderByCreatedAtDesc(Integer deviceId, Collection<String> statuses);

    /**
     * Lấy lệnh gần nhất theo deviceCode và kết quả success/failed/pending.
     */
    Optional<History> findTopByDeviceCodeAndStatusInOrderByCreatedAtDesc(String deviceCode, Collection<String> statuses);

    /**
     * Lấy bản ghi điều khiển mới nhất của thiết bị
     */
    Optional<History> findTopByDeviceIdOrderByCreatedAtDesc(Integer deviceId);

    /**
     * Lấy bản ghi điều khiển mới nhất của thiết bị theo deviceCode
     */
    Optional<History> findTopByDeviceCodeOrderByCreatedAtDesc(String deviceCode);
}
