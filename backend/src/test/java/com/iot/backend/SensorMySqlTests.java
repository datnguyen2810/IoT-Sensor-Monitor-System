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
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.ConnectionCallback;

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
                .contains("idx_datasensors_created_id", "idx_datasensors_sensor_created", "uk_datasensors_measurement_sensor");
    }

    @Test
    void additiveMigrationPreservesLegacyDataAndCanRunTwice() throws Exception {
        // Only touches new, explicitly named tables inside this test's random temporary schema.
        jdbc.execute("CREATE TABLE datasensors_migration (id INT PRIMARY KEY, sensor_id INT NOT NULL, "
                + "value FLOAT NOT NULL, created_at DATETIME(6) NOT NULL)");
        try {
            jdbc.update("INSERT INTO datasensors_migration VALUES (1,1,25,NOW()),(2,2,50,NOW()),(3,3,10,NOW())");
            String script = Files.readString(Path.of(System.getProperty("basedir", "."), "db", "migrations", "004_sensor_measurements.sql"))
                    .replaceAll("(?m)^--.*$", "")
                    .replace("sensor_measurements", "sensor_measurements_migration")
                    .replace("datasensors", "datasensors_migration");
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.createStatement()) {
                    for (int run = 0; run < 2; run++) {
                        for (String sql : script.split(";")) {
                            if (!sql.isBlank()) statement.execute(sql.trim());
                        }
                    }
                }
                return null;
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM datasensors_migration WHERE measurement_id IS NULL", Integer.class))
                    .isEqualTo(3);
            assertThat(jdbc.queryForList("SELECT CONSTRAINT_TYPE FROM information_schema.table_constraints "
                    + "WHERE table_schema = DATABASE() AND table_name = 'datasensors_migration'", String.class))
                    .contains("UNIQUE", "FOREIGN KEY");
        } finally {
            jdbc.execute("DROP TABLE datasensors_migration");
            jdbc.execute("DROP TABLE IF EXISTS sensor_measurements_migration");
        }
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
