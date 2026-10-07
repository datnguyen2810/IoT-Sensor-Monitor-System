package com.iot.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class History {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action", length = 10, nullable = false)
    private String action;

    @Column(name = "status", length = 25, nullable = false)
    @Pattern(regexp = "success|failed|pending", message = "Trạng thái lệnh phải là success, failed hoặc pending")
    @Builder.Default
    private String status = CommandStatus.PENDING.getValue();

    // Trạng thái thiết bị đã xác nhận, không dùng ON/OFF làm kết quả lệnh.
    @Column(name = "status_received", length = 10)
    @Pattern(regexp = "ON|OFF", message = "Trạng thái thiết bị phải là ON hoặc OFF")
    private String statusReceived;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = CommandStatus.PENDING.getValue();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
