package com.iot.backend;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the same locking/ACK/API tests on a separate randomly named MySQL schema. */
@EnabledIfSystemProperty(named = "iot.mysql.tests", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeviceMySqlTests extends DeviceApiTests {
    private static final String DATABASE = "iot_device_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final Properties SETTINGS = settings();
    @Autowired JdbcTemplate jdbc;

    private static Properties settings() {
        Properties values = new Properties();
        try (var stream = DeviceMySqlTests.class.getResourceAsStream("/application.properties")) {
            if (stream == null) throw new IllegalStateException("Missing application settings");
            values.load(stream);
            return values;
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot read MySQL settings", exception); }
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String source = SETTINGS.getProperty("spring.datasource.url");
        int query = source.indexOf('?');
        String address = query < 0 ? source : source.substring(0, query);
        String options = query < 0 ? "" : source.substring(query + 1) + "&";
        String url = address.substring(0, address.lastIndexOf('/') + 1) + DATABASE + "?" + options + "createDatabaseIfNotExist=true";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> SETTINGS.getProperty("spring.datasource.username"));
        registry.add("spring.datasource.password", () -> SETTINGS.getProperty("spring.datasource.password"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Test
    void stageFiveAddsNeitherTablesNorColumns() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class))
                .containsExactlyInAnyOrder("sensors", "datasensors", "devices", "history", "user");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'devices'", String.class))
                .containsExactlyInAnyOrder("id", "code", "name");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'history'", String.class))
                .containsExactlyInAnyOrder("id", "device_id", "user_id", "action", "status", "status_received", "created_at");
    }

    @AfterAll
    static void removeTemporarySchema() throws Exception {
        if (!DATABASE.matches("iot_device_test_[a-f0-9]{12}")) throw new IllegalStateException("Unsafe temporary schema");
        try (var connection = DriverManager.getConnection(SETTINGS.getProperty("spring.datasource.url"),
                SETTINGS.getProperty("spring.datasource.username"), SETTINGS.getProperty("spring.datasource.password"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + DATABASE + "`");
        }
    }
}
