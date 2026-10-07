-- Chạy thủ công trên DB đã backup, sau khi cột history.status_received tồn tại.
-- Hibernate ddl-auto=update sẽ thêm cột nullable khi backend khởi động.
-- Dừng backend khi chuyển đổi dữ liệu để tránh nhận ACK giữa các bước.
-- Không nằm trong data.sql, không tự chạy lại mỗi lần khởi động.

-- Kiểm tra các giá trị lạ trước khi chuyển đổi; xử lý riêng nếu query có kết quả.
SELECT id, action, status FROM history
WHERE status IS NOT NULL
  AND LOWER(status) NOT IN ('on', 'off', 'pending', 'sent', 'confirmed', 'success', 'failed', 'timeout');

START TRANSACTION;

-- Giữ lại phản hồi ON/OFF cũ trước khi thay status bằng kết quả thực hiện lệnh.
UPDATE history
SET status_received = UPPER(status)
WHERE LOWER(status) IN ('on', 'off') AND status_received IS NULL;

UPDATE history
SET status = CASE
    WHEN LOWER(status) IN ('on', 'off') THEN
        CASE WHEN UPPER(action) = UPPER(status) THEN 'success' ELSE 'failed' END
    WHEN LOWER(status) IN ('pending', 'sent') THEN 'pending'
    WHEN LOWER(status) IN ('confirmed', 'success') THEN 'success'
    WHEN LOWER(status) IN ('failed', 'timeout') THEN 'failed'
    ELSE status
END
WHERE LOWER(status) IN ('on', 'off', 'pending', 'sent', 'confirmed', 'success', 'failed', 'timeout');

COMMIT;

SELECT status, COUNT(*) AS total FROM history GROUP BY status;
