package com.iot.backend.repository;

import com.iot.backend.entity.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface HistoryRepository extends JpaRepository<History, Integer>, JpaSpecificationExecutor<History> {

    /**
     * Lấy bản ghi điều khiển mới nhất theo deviceId và tập hợp trạng thái (ví dụ: ON, OFF)
     * Phục vụ API /api/devices/status
     */
    Optional<History> findTopByDeviceIdAndStatusInOrderByCreatedAtDesc(Integer deviceId, Collection<String> statuses);

    /**
     * Lấy bản ghi điều khiển mới nhất theo deviceCode (led/fan/ac) và tập hợp trạng thái (ví dụ: ON, OFF)
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
