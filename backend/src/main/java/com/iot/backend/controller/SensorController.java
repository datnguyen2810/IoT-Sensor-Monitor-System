package com.iot.backend.controller;

import com.iot.backend.dto.response.ApiResponse;
import com.iot.backend.dto.response.PageResponse;
import com.iot.backend.dto.response.SensorChartResponse;
import com.iot.backend.dto.response.SensorHistoryResponse;
import com.iot.backend.dto.response.SensorLatestResponse;
import com.iot.backend.service.SensorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller cung cấp các API dữ liệu cảm biến:
 * - GET /api/sensors/latest : Lấy thông số mới nhất cho Dashboard
 * - GET /api/sensors/chart  : Lấy 15 điểm đo gần nhất cho biểu đồ
 * - GET /api/sensors/history: Lấy lịch sử đo có phân trang, lọc và tìm kiếm
 */
@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    /**
     * API lấy thông số cảm biến mới nhất (nhiệt độ, độ ẩm, ánh sáng).
     * Endpoint: GET /api/sensors/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<SensorLatestResponse>> getLatestData() {
        SensorLatestResponse data = sensorService.getLatestData();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * API lấy 15 mốc đo gần nhất gom thành mảng điểm đo phục vụ biểu đồ.
     * Endpoint: GET /api/sensors/chart
     */
    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<List<SensorChartResponse>>> getChartData() {
        List<SensorChartResponse> data = sensorService.getChartData();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * API lấy danh sách lịch sử đo cảm biến có phân trang, tìm kiếm và lọc.
     * Endpoint: GET /api/sensors/history
     *
     * @param page       Số trang (bắt đầu từ 1, mặc định: 1)
     * @param limit      Số bản ghi mỗi trang (mặc định: 10)
     * @param sensorType Loại cảm biến cần lọc (Nhiệt Độ, Độ Ẩm, Ánh Sáng)
     * @param search     Từ khóa tìm kiếm trong chuỗi thời gian
     * @param sort       Thứ tự sắp xếp theo thời gian (asc | desc, mặc định: desc)
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<SensorHistoryResponse>>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false, name = "sensor_type") String sensorType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "desc") String sort) {
        PageResponse<SensorHistoryResponse> data = sensorService.getHistory(page, limit, sensorType, search, sort);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
