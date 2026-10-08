package com.iot.backend.repository;

import com.iot.backend.entity.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface HistoryRepository extends JpaRepository<History, Integer> {

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
