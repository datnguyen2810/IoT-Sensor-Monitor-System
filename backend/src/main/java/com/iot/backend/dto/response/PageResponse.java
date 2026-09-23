package com.iot.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO bọc dữ liệu phân trang chuẩn cho các bảng dữ liệu (Data Sensor, History).
 * Khớp chuẩn với các trường mà Frontend đang đọc (total_records, total_pages, current_page, data).
 *
 * @param <T> Kiểu dữ liệu của danh sách bản ghi
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    @JsonProperty("total_records")
    private long totalRecords;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("current_page")
    private int currentPage;

    @JsonProperty("data")
    private List<T> data;
}
