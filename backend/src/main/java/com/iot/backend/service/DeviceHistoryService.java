package com.iot.backend.service;

import com.iot.backend.dto.response.DeviceHistoryResponse;
import com.iot.backend.dto.response.PageResponse;
import com.iot.backend.repository.HistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeviceHistoryService {
    private static final Map<String, String> DEVICES = Map.of(
            "Đèn LED", "led", "Quạt", "fan", "Điều hoà", "ac");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final HistoryRepository repository;

    public DeviceHistoryService(HistoryRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public PageResponse<DeviceHistoryResponse> getHistory(int page, int limit, String device,
            String action, String status, String commandStatus, String search, String sort) {
        String name = emptyIfNull(device);
        String sent = emptyIfNull(action);
        String received = emptyIfNull(status);
        String outcome = emptyIfNull(commandStatus);
        if (page < 1 || limit < 1 || limit > 100 || (!name.isEmpty() && !DEVICES.containsKey(name))
                || !List.of("", "ON", "OFF").contains(sent)
                || !List.of("", "ON", "OFF").contains(received)
                || !List.of("", "success", "failed", "pending").contains(outcome)
                || !List.of("asc", "desc").contains(emptyIfNull(sort))
                || (search != null && search.length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tham số lịch sử thiết bị không hợp lệ");
        }
        String keyword = emptyIfNull(search).trim();
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        LocalDate date = fullDate(keyword);
        var pageable = PageRequest.of(page - 1, limit,
                Sort.by("asc".equals(sort) ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt", "id"));
        var result = repository.searchHistory(DEVICES.get(name), nullIfEmpty(sent), nullIfEmpty(received),
                nullIfEmpty(outcome), keyword.isEmpty() ? null : "%" + escaped + "%",
                date == null ? null : date.atStartOfDay(),
                date == null ? null : date.plusDays(1).atStartOfDay(), pageable);
        var rows = result.getContent().stream().map(h -> new DeviceHistoryResponse(
                h.getId(), h.getId(), h.getDevice().getName(), h.getAction(), h.getStatusReceived(),
                h.getStatus(), h.getUser() == null ? "Hệ thống (Auto)" : h.getUser().getUsername(),
                h.getCreatedAt().atZone(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).toList();
        return PageResponse.<DeviceHistoryResponse>builder().data(rows).totalRecords(result.getTotalElements())
                .totalPages(Math.max(1, result.getTotalPages()))
                .currentPage(result.getTotalElements() == 0 ? 1 : page).build();
    }

    private static String emptyIfNull(String value) { return value == null ? "" : value; }
    private static String nullIfEmpty(String value) { return value.isEmpty() ? null : value; }

    private static LocalDate fullDate(String keyword) {
        if (!keyword.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try { return LocalDate.parse(keyword); }
        catch (DateTimeParseException exception) { return null; }
    }
}
