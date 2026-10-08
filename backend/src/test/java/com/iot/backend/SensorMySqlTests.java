package com.iot.backend;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cùng bộ kiểm tra chạy MySQL thật trên schema tạm riêng, tuyệt đối không dùng bảng dự án. */
@EnabledIfSystemProperty(named = "iot.mysql.tests", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SensorMySqlTests extends SensorApiTests {
    private static final String DATABASE = "iot_sensor_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final Properties SETTINGS = settings();

    private static Properties settings() {
        Properties settings = new Properties();
        try (var stream = SensorMySqlTests.class.getResourceAsStream("/application.properties")) {
            if (stream == null) throw new IllegalStateException("Thiếu application.properties");
            settings.load(stream);
            return settings;
        } catch (IOException ex) { throw new IllegalStateException("Không đọc được cấu hình MySQL", ex); }
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        String source = SETTINGS.getProperty("spring.datasource.url");
        int queryStart = source.indexOf('?');
        String address = queryStart < 0 ? source : source.substring(0, queryStart);
        String options = queryStart < 0 ? "" : source.substring(queryStart + 1) + "&";
        String testUrl = address.substring(0, address.lastIndexOf('/') + 1) + DATABASE
                + "?" + options + "createDatabaseIfNotExist=true";
        properties.add("spring.datasource.url", () -> testUrl);
        properties.add("spring.datasource.username", () -> SETTINGS.getProperty("spring.datasource.username"));
        properties.add("spring.datasource.password", () -> SETTINGS.getProperty("spring.datasource.password"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        // Không drop schema lúc Hibernate đóng context: cleanup schema riêng ở dưới.
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Test
    void expectedIndexesArePresentOnMySql() {
        assertThat(jdbc.queryForList("SELECT DISTINCT INDEX_NAME FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'datasensors'", String.class))
                .contains("idx_datasensors_created_id", "idx_datasensors_sensor_created");
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE()", String.class))
                .containsExactlyInAnyOrder("sensors", "datasensors", "devices", "history", "user");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'datasensors'", String.class))
                .containsExactlyInAnyOrder("id", "sensor_id", "value", "created_at");
    }

    @AfterAll
    static void removeTemporarySchema() throws Exception {
        if (!DATABASE.matches("iot_sensor_test_[a-f0-9]{12}")) throw new IllegalStateException("Unsafe test database name");
        try (var connection = DriverManager.getConnection(SETTINGS.getProperty("spring.datasource.url"),
                SETTINGS.getProperty("spring.datasource.username"), SETTINGS.getProperty("spring.datasource.password"));
             var statement = connection.createStatement()) {
            // Target được sinh riêng cho lượt test này, không phải database trong application.properties.
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + DATABASE + "`");
        }
    }
}
