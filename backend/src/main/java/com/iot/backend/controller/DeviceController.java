package com.iot.backend.controller;

import com.iot.backend.dto.request.DeviceControlRequest;
import com.iot.backend.dto.response.ApiResponse;
import com.iot.backend.dto.response.DeviceControlResponse;
import com.iot.backend.dto.response.DeviceStatusResponse;
import com.iot.backend.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService service;
    public DeviceController(DeviceService service) { this.service = service; }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<DeviceStatusResponse>>> status() {
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái thiết bị thành công", service.getStatuses()));
    }

    @PostMapping("/control")
    public ResponseEntity<ApiResponse<DeviceControlResponse>> control(
            @Valid @RequestBody DeviceControlRequest request, Authentication authentication) {
        return ResponseEntity.status(202).body(ApiResponse.success(202, "Đã tiếp nhận lệnh, đang chờ phản hồi",
                service.control(request, authentication.getName())));
    }
}
