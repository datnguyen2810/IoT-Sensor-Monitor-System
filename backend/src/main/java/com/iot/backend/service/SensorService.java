package com.iot.backend.service;

import com.iot.backend.dto.response.PageResponse;
import com.iot.backend.dto.response.SensorChartResponse;
import com.iot.backend.dto.response.SensorHistoryResponse;
import com.iot.backend.dto.response.SensorLatestResponse;
import com.iot.backend.entity.DataSensor;
import com.iot.backend.repository.DataSensorRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý các nghiệp vụ truy vấn dữ liệu cảm biến:
 * - Lấy thông số mới nhất phục vụ Polling Dashboard (2s)
 * - Lấy 15 điểm đo gần nhất phục vụ biểu đồ Chart.js
 * - Lấy lịch sử đo có phân trang, tìm kiếm thời gian và lọc theo loại cảm biến
 */
@Service
public class SensorService {

    private static final int SENSOR_ID_TEMPERATURE = 1;
    private static final int SENSOR_ID_HUMIDITY = 2;
    private static final int SENSOR_ID_LIGHT = 3;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DataSensorRepository dataSensorRepository;

    public SensorService(DataSensorRepository dataSensorRepository) {
        this.dataSensorRepository = dataSensorRepository;
    }

    /**
     * Lấy giá trị mới nhất của 3 thông số cảm biến (nhiệt độ, độ ẩm, ánh sáng).
     */
    @Transactional(readOnly = true)
    public SensorLatestResponse getLatestData() {
        Optional<DataSensor> tempOpt = dataSensorRepository.findTopBySensorIdOrderByCreatedAtDesc(SENSOR_ID_TEMPERATURE);
        Optional<DataSensor> humiOpt = dataSensorRepository.findTopBySensorIdOrderByCreatedAtDesc(SENSOR_ID_HUMIDITY);
        Optional<DataSensor> lightOpt = dataSensorRepository.findTopBySensorIdOrderByCreatedAtDesc(SENSOR_ID_LIGHT);

        Float temperature = tempOpt.map(DataSensor::getValue).orElse(0.0f);
        Float humidity = humiOpt.map(DataSensor::getValue).orElse(0.0f);
        Float light = lightOpt.map(DataSensor::getValue).orElse(0.0f);

        // Lấy mốc thời gian của bản ghi mới nhất
        LocalDateTime latestTime = null;
        if (tempOpt.isPresent()) latestTime = tempOpt.get().getCreatedAt();
        if (humiOpt.isPresent() && (latestTime == null || humiOpt.get().getCreatedAt().isAfter(latestTime))) {
            latestTime = humiOpt.get().getCreatedAt();
        }
        if (lightOpt.isPresent() && (latestTime == null || lightOpt.get().getCreatedAt().isAfter(latestTime))) {
            latestTime = lightOpt.get().getCreatedAt();
        }

        String timestampStr = latestTime != null ? latestTime.format(DATETIME_FORMATTER) : LocalDateTime.now().format(DATETIME_FORMATTER);

        return SensorLatestResponse.builder()
                .temperature(temperature)
                .humidity(humidity)
                .light(light)
                .timestamp(timestampStr)
                .build();
    }

    /**
     * Lấy 15 mốc thời gian gần nhất gom thành mảng điểm đo {time, temperature, humidity, light}.
     * Sắp xếp theo thời gian tăng dần để biểu đồ Chart.js vẽ từ trái qua phải.
     */
    @Transactional(readOnly = true)
    public List<SensorChartResponse> getChartData() {
        // Lấy 15 mốc thời gian gần nhất
        Page<LocalDateTime> recentPage = dataSensorRepository.findDistinctRecentCreatedAt(PageRequest.of(0, 15));
        List<LocalDateTime> recentTimestamps = recentPage.getContent();

        if (recentTimestamps.isEmpty()) {
            return Collections.emptyList();
        }

        // Truy vấn tất cả bản ghi tương ứng đã được sắp xếp tăng dần theo createdAt
        List<DataSensor> records = dataSensorRepository.findByCreatedAtInOrderByCreatedAtAsc(recentTimestamps);

        // Gom các điểm đo theo mốc thời gian (LinkedHashMap bảo toàn thứ tự tăng dần)
        Map<LocalDateTime, SensorChartResponse> map = new LinkedHashMap<>();

        for (DataSensor record : records) {
            LocalDateTime createdAt = record.getCreatedAt();
            SensorChartResponse point = map.computeIfAbsent(createdAt, t -> SensorChartResponse.builder()
                    .time(t.format(TIME_FORMATTER))
                    .temperature(0.0f)
                    .humidity(0.0f)
                    .light(0.0f)
                    .build());

            if (record.getSensor() != null) {
                int sensorId = record.getSensor().getId();
                if (sensorId == SENSOR_ID_TEMPERATURE) {
                    point.setTemperature(record.getValue());
                } else if (sensorId == SENSOR_ID_HUMIDITY) {
                    point.setHumidity(record.getValue());
                } else if (sensorId == SENSOR_ID_LIGHT) {
                    point.setLight(record.getValue());
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    /**
     * Lấy lịch sử đo cảm biến có phân trang, tìm kiếm chuỗi thời gian, lọc theo loại cảm biến và đổi chiều sắp xếp.
     *
     * @param page       Trang hiện tại (1-indexed từ client)
     * @param limit      Số bản ghi trên mỗi trang
     * @param sensorType Tên loại cảm biến (Nhiệt Độ, Độ Ẩm, Ánh Sáng) hoặc để trống
     * @param search     Từ khóa tìm kiếm trong chuỗi thời gian (yyyy-MM-dd hoặc HH:mm:ss)
     * @param sort       Thứ tự sắp xếp ("asc" hoặc "desc", mặc định "desc")
     */
    @Transactional(readOnly = true)
    public PageResponse<SensorHistoryResponse> getHistory(int page, int limit, String sensorType, String search, String sort) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = Math.max(1, limit);

        Sort.Direction direction = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, "createdAt", "id"));

        Specification<DataSensor> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Eager fetch sensor để tránh N+1 khi truy vấn danh sách (bỏ qua nếu là count query)
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("sensor", JoinType.LEFT);
            }

            // Lọc theo loại cảm biến
            if (sensorType != null && !sensorType.trim().isEmpty()) {
                String typeStr = sensorType.trim().toLowerCase();
                predicates.add(cb.like(cb.lower(root.get("sensor").get("name")), "%" + typeStr + "%"));
            }

            // Tìm kiếm chuỗi thời gian (search)
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim() + "%";
                Expression<String> dateFormatted = cb.function(
                        "DATE_FORMAT",
                        String.class,
                        root.get("createdAt"),
                        cb.literal("%Y-%m-%d %H:%i:%s")
                );
                predicates.add(cb.like(dateFormatted, searchPattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DataSensor> pageResult = dataSensorRepository.findAll(spec, pageable);

        List<SensorHistoryResponse> items = pageResult.getContent().stream().map(d ->
                SensorHistoryResponse.builder()
                        .id(d.getId())
                        .sensorName(d.getSensor() != null ? d.getSensor().getName() : "--")
                        .value(d.getValue())
                        .unit(d.getSensor() != null ? d.getSensor().getUnit() : "")
                        .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().format(DATETIME_FORMATTER) : "--")
                        .build()
        ).collect(Collectors.toList());

        return PageResponse.<SensorHistoryResponse>builder()
                .totalRecords(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .currentPage(page)
                .data(items)
                .build();
    }
}
