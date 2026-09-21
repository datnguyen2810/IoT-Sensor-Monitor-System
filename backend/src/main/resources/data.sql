-- =============================================
-- Seed Data - IoT Sensor Monitor System
-- Chay sau khi Hibernate tao bang (defer-datasource-initialization=true)
-- Su dung INSERT IGNORE de tranh loi khi chay lai
-- =============================================

-- Bang sensors: 3 loai cam bien
INSERT IGNORE INTO sensors (id, name, unit) VALUES (1, 'Nhiệt Độ', '°C');
INSERT IGNORE INTO sensors (id, name, unit) VALUES (2, 'Độ Ẩm', '%');
INSERT IGNORE INTO sensors (id, name, unit) VALUES (3, 'Ánh Sáng', 'Lux');

-- Bang devices: 3 thiet bi dieu khien (code khop voi ESP8266 firmware)
INSERT IGNORE INTO devices (id, code, name) VALUES (1, 'led', 'Đèn LED');
INSERT IGNORE INTO devices (id, code, name) VALUES (2, 'fan', 'Quạt');
INSERT IGNORE INTO devices (id, code, name) VALUES (3, 'ac', 'Điều hoà');

-- Bang user: tai khoan mac dinh (mat khau: 123456, ma hoa BCrypt)
INSERT IGNORE INTO user (id, username, password) VALUES (1, 'admin', '$2a$10$lgsNZJy5jcNwrAD0zPzRMO/Q7eQYiNgmSZQ.e9kjhIF3xgD0Cge.K');
