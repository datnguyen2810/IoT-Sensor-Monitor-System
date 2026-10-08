# Backend IoT Sensor Monitor System

## Chạy phát triển

Yêu cầu Java 21 và MySQL. Thông tin DB, MQTT và JWT vẫn cấu hình trực tiếp trong `src/main/resources/application.properties` theo lựa chọn của dự án.

Ứng dụng dùng một file `application.properties`, không tách profile dev/test. Database cần tồn tại trước khi chạy. Seed dùng INSERT IGNORE: không thay mật khẩu tài khoản đã tồn tại trong DB.

Chạy từ thư mục backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Khi chỉ phát triển REST API và chưa bật broker, đặt `mqtt.enabled=false` trong `application.properties`, hoặc chạy:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--mqtt.enabled=false'
```

Khi tắt MQTT, MqttConfig/MqttService không được tạo và backend không thử kết nối broker. Muốn nhận dữ liệu phần cứng thì bật lại `mqtt.enabled=true`.

## Kiểm thử

```powershell
.\mvnw.cmd test
```

Các test không dùng profile. Riêng lớp `BackendApplicationTests` khai báo cấu hình H2 trong RAM và tắt MQTT ngay tại `@SpringBootTest(properties = ...)`. Cấu hình này chỉ áp dụng cho kiểm thử, không đóng gói vào ứng dụng chạy bình thường và không tạo/xóa bảng trên MySQL của bạn.

Test dùng seed thực của dự án để kiểm tra BCrypt, login và JWT; kiểm tra token login gọi API cảm biến, token sai/hết hạn, response ba trường, Swagger/OpenAPI và MQTT ACK bằng mock repository. H2 không thay thế kiểm thử các truy vấn đặc thù MySQL trong giai đoạn hoàn thiện cảm biến.

## Ghép frontend

- Chạy frontend qua HTTP Live Server, ví dụ `http://localhost:5500` hoặc `http://127.0.0.1:5500`; không mở trực tiếp bằng file://.
- Backend mặc định ở `http://localhost:8080`, khớp BASE_URL hiện tại của frontend.
- `POST /api/auth/login` nhận username/password và trả token cùng username trong `data`.
- Các API cảm biến yêu cầu `Authorization: Bearer <token>`.
- CORS hiện cho phép HTTP localhost/127.0.0.1 ở các port phát triển; OPTIONS không cần JWT.
- ApiResponse luôn có `status` (mã HTTP), `message`, `data`; lỗi thông thường có `data: null`, lỗi validation có chi tiết field trong `data`.
- Nếu trước đó frontend lưu demo token, đăng xuất rồi đăng nhập thật để lấy JWT mới.
- Việc tách demo UI và ghép vòng đời điều khiển thiết bị đầy đủ nằm ở giai đoạn 5–7.

## Trạng thái triển khai

Đã hoàn thiện giai đoạn 1 (response/lỗi) và giai đoạn 2 (cấu hình/auth). API cảm biến đã có nhưng còn các hạng mục giai đoạn 3; API điều khiển/lịch sử thiết bị chưa triển khai đầy đủ. Kế hoạch chi tiết nằm ở BACKEND_PLAN.md (file này hiện được gitignore theo cấu hình repo).
