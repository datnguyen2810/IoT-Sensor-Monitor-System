package com.iot.backend.service;

import com.iot.backend.dto.response.*;
import com.iot.backend.entity.DataSensor;
import com.iot.backend.repository.DataSensorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SensorService {
    private static final ZoneId SENSOR_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Map<String, Integer> SENSOR_TYPES = Map.of(
            "Nhiệt Độ", 1, "Độ Ẩm", 2, "Ánh Sáng", 3);
    private static final DateTimeFormatter SEARCH_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DataSensorRepository repository;

    public SensorService(DataSensorRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SensorLatestResponse getLatestData() {
        List<SensorChartResponse> snapshots = snapshots(1);
        if (snapshots.isEmpty()) return null;
        SensorChartResponse latest = snapshots.getFirst();
        return SensorLatestResponse.builder().temperature(latest.getTemperature())
                .humidity(latest.getHumidity()).light(latest.getLight())
                .timestamp(latest.getTimestamp()).build();
    }

    @Transactional(readOnly = true)
    public List<SensorChartResponse> getChartData() {
        return snapshots(15);
    }

    private List<SensorChartResponse> snapshots(int limit) {
        List<LocalDateTime> times = repository.findCompleteMeasurementTimes(PageRequest.of(0, limit));
        if (times.isEmpty()) return List.of();
        Map<LocalDateTime, SensorChartResponse> points = new TreeMap<>();
        for (DataSensor record : repository.findByCreatedAtInOrderByCreatedAtAsc(times)) {
            SensorChartResponse point = points.computeIfAbsent(record.getCreatedAt(), time ->
                    SensorChartResponse.builder().time(time.format(SEARCH_TIME))
                            .timestamp(formatTimestamp(time)).build());
            switch (record.getSensor().getId()) {
                case 1 -> point.setTemperature(record.getValue());
                case 2 -> point.setHumidity(record.getValue());
                case 3 -> point.setLight(record.getValue());
                default -> { }
            }
        }
        // Không bù số 0 khi mẫu bị thiếu, kể cả khi DB thay đổi giữa hai query.
        return points.values().stream().filter(point -> point.getTemperature() != null
                && point.getHumidity() != null && point.getLight() != null).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SensorHistoryResponse> getHistory(
            int page, int limit, String sensorType, String search, String searchType, String sort) {
        String type = sensorType == null ? "" : sensorType;
        String mode = searchType == null ? "" : searchType;
        String keyword = search == null ? "" : search.trim();
        // Giữ validation khi service được gọi ngoài controller.
        if (page < 1 || limit < 1 || limit > 100 || (!type.isEmpty() && !SENSOR_TYPES.containsKey(type))
                || !List.of("", "time", "value").contains(mode)
                || !List.of("asc", "desc").contains(sort == null ? "" : sort)
                || (search != null && search.length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tham số tìm kiếm cảm biến không hợp lệ");
        }
        var pageable = PageRequest.of(page - 1, limit,
                Sort.by("asc".equals(sort) ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt", "id"));
        String pattern = keyword.isEmpty() ? null : "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        LocalDate date = fullDate(keyword);
        Page<DataSensor> result = repository.searchHistory(SENSOR_TYPES.get(type), pattern, mode,
                date == null ? null : date.atStartOfDay(),
                date == null ? null : date.plusDays(1).atStartOfDay(), pageable);
        List<SensorHistoryResponse> rows = result.getContent().stream().map(record ->
                SensorHistoryResponse.builder().id(record.getId())
                        .sensorName(record.getSensor().getName()).value(record.getValue())
                        .unit(record.getSensor().getUnit()).createdAt(formatTimestamp(record.getCreatedAt())).build()).toList();
        return PageResponse.<SensorHistoryResponse>builder().data(rows)
                .totalRecords(result.getTotalElements()).totalPages(Math.max(1, result.getTotalPages()))
                .currentPage(result.getTotalElements() == 0 ? 1 : page).build();
    }

    private static String formatTimestamp(LocalDateTime time) {
        return time.atZone(SENSOR_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static LocalDate fullDate(String keyword) {
        if (!keyword.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try { return LocalDate.parse(keyword); }
        catch (DateTimeParseException ex) { return null; }
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
