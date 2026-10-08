package com.iot.backend.service;

import com.iot.backend.dto.response.*;
import com.iot.backend.entity.DataSensor;
import com.iot.backend.repository.DataSensorRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        var keys = repository.findCompleteMeasurements(PageRequest.of(0, limit));
        if (keys.isEmpty()) return List.of();
        record Key(String measurementId, LocalDateTime time) { }
        Map<Key, SensorChartResponse> points = new LinkedHashMap<>();
        // Query returns newest first; chart output must be oldest first, with stable ordering on ties.
        for (var key : keys.reversed()) {
            LocalDateTime time = key.getCreatedAt();
            points.put(new Key(key.getMeasurementId(), key.getMeasurementId() == null ? time : null), SensorChartResponse.builder()
                    .time(time.format(SEARCH_TIME)).timestamp(formatTimestamp(time)).build());
        }
        var times = keys.stream().map(DataSensorRepository.MeasurementKey::getCreatedAt).distinct().toList();
        for (DataSensor record : repository.findByCreatedAtInOrderByCreatedAtAsc(times)) {
            String measurementId = record.getMeasurement() == null ? null : record.getMeasurement().getId();
            var key = new Key(measurementId, measurementId == null ? record.getCreatedAt() : null);
            SensorChartResponse point = points.get(key);
            if (point == null) continue;
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
        Specification<DataSensor> specification = (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("sensor", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (!type.isEmpty()) predicates.add(cb.equal(root.get("sensor").get("id"), SENSOR_TYPES.get(type)));
            if (!keyword.isEmpty()) {
                String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
                Expression<String> value = ((JpaExpression<Float>) root.<Float>get("value")).cast(String.class);
                Predicate valueMatch = cb.like(value, pattern, '!');
                if ("value".equals(mode)) {
                    predicates.add(valueMatch);
                } else {
                    Predicate timeMatch;
                    LocalDate date = fullDate(keyword);
                    if (date != null) {
                        timeMatch = cb.and(cb.greaterThanOrEqualTo(root.get("createdAt"), date.atStartOfDay()),
                                cb.lessThan(root.get("createdAt"), date.plusDays(1).atStartOfDay()));
                    } else {
                        Expression<String> time = cb.function("DATE_FORMAT", String.class,
                                root.get("createdAt"), cb.literal("%Y-%m-%d %H:%i:%s"));
                        timeMatch = cb.like(time, pattern, '!');
                    }
                    if ("time".equals(mode)) predicates.add(timeMatch);
                    else predicates.add(cb.or(timeMatch, valueMatch,
                            cb.like(cb.lower(root.get("sensor").get("name")), pattern, '!')));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<DataSensor> result = repository.findAll(specification, pageable);
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
