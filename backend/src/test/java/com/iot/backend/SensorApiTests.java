package com.iot.backend;

import com.iot.backend.entity.DataSensor;
import com.iot.backend.dto.mqtt.SensorMeasurementPayload;
import com.iot.backend.repository.SensorMeasurementRepository;
import com.iot.backend.service.SensorMeasurementService;
import com.iot.backend.service.MqttService;
import com.iot.backend.repository.HistoryRepository;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.iot.backend.repository.DataSensorRepository;
import com.iot.backend.repository.SensorRepository;
import com.iot.backend.security.JwtTokenProvider;
import com.iot.backend.service.SensorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iot_sensor_api_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false", "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always", "mqtt.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
public class SensorApiTests {
    @Autowired SensorService service;
    @MockitoSpyBean DataSensorRepository records;
    @Autowired SensorMeasurementRepository measurements;
    @Autowired SensorMeasurementService ingestion;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SensorRepository sensors;
    @Autowired JwtTokenProvider tokens;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 7, 10, 0);

    @BeforeEach
    void prepare() {
        records.deleteAll();
        records.flush();
        measurements.deleteAll();
        measurements.flush();
        if (environment.getProperty("spring.datasource.url", "").startsWith("jdbc:h2:")) {
            // Chỉ mô phỏng function trong H2; cùng test còn được chạy trên MySQL thật.
            jdbc.execute("CREATE ALIAS IF NOT EXISTS date_format FOR 'com.iot.backend.SensorApiTests.mysqlDateFormat'");
        }
    }

    public static String mysqlDateFormat(Timestamp timestamp, String pattern) {
        if (!"%Y-%m-%d %H:%i:%s".equals(pattern)) throw new IllegalArgumentException("Unsupported test format");
        return timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void mqttPayloadCreatesExactlyThreeRowsReadableByLatestAndChart() {
        var mqtt = new MqttService(mock(IMqttClient.class), new MqttConnectOptions(), mapper, ingestion,
                mock(HistoryRepository.class), mock(ScheduledExecutorService.class));
        var message = new MqttMessage("{\"measurement_id\":\"first\",\"temperature\":28.5,\"humidity\":65,\"light\":0}"
                .getBytes(StandardCharsets.UTF_8));
        mqtt.messageArrived("iot/sensor/data", message);
        mqtt.messageArrived("iot/sensor/data", message);
        assertThat(records.count()).isEqualTo(3);
        assertThat(measurements.count()).isEqualTo(1);
        assertThat(records.findAll()).extracting(row -> row.getMeasurement().getId()).containsOnly("firmware:first");
        assertThat(service.getLatestData().getTemperature()).isEqualTo(28.5f);
        assertThat(service.getLatestData().getLight()).isZero();
        assertThat(service.getChartData()).hasSize(1);
    }

    @Test
    void distinctMeasurementsAtSameTimestampDoNotMergeAndLegacyMessagesRemainSupported() {
        ingestion.persist(new SensorMeasurementPayload("one", 1, 2, 3));
        ingestion.persist(new SensorMeasurementPayload("two", 4, 5, 6));
        jdbc.update("UPDATE datasensors SET created_at = ?", Timestamp.valueOf(START));
        assertThat(service.getChartData()).hasSize(2);
        assertThat(service.getChartData()).extracting(point -> point.getTemperature()).containsExactly(1f, 4f);
        assertThat(service.getLatestData().getTemperature()).isEqualTo(4f);
        ingestion.persist(new SensorMeasurementPayload(null, 7, 8, 9));
        ingestion.persist(new SensorMeasurementPayload(null, 7, 8, 9));
        assertThat(records.count()).isEqualTo(12);
        assertThat(measurements.count()).isEqualTo(4);
    }

    @Test
    void reusedMeasurementIdWithDifferentDataIsRejected() {
        ingestion.persist(new SensorMeasurementPayload("one", 1, 2, 3));
        assertThatThrownBy(() -> ingestion.persist(new SensorMeasurementPayload("one", 4, 5, 6)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(records.count()).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void thirdInsertFailureRollsBackRowsAndDeduplicationMetadataThenAllowsRetry() {
        // Deliberately violate the unique measurement/sensor constraint on the third row.
        // This uses the real repository and database, not a mocked transaction.
        doAnswer(invocation -> {
            List<DataSensor> batch = invocation.getArgument(0);
            batch.get(2).setSensor(batch.get(0).getSensor());
            var result = records.saveAll(batch);
            records.flush();
            return result;
        }).when(records).saveAllAndFlush(any());
        try {
            assertThatThrownBy(() -> ingestion.persist(new SensorMeasurementPayload("retry", 1, 2, 3)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            reset(records);
        }
        assertThat(records.count()).isZero();
        assertThat(measurements.count()).isZero();
        try {
            assertThat(ingestion.persist(new SensorMeasurementPayload("retry", 1, 2, 3))).isTrue();
            assertThat(records.count()).isEqualTo(3);
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                records.deleteAll();
                records.flush();
                measurements.deleteAll();
            });
        }
    }

    private void sample(LocalDateTime time, float temp, float humidity, float light) {
        record(time, 1, temp); record(time, 2, humidity); record(time, 3, light);
        records.flush();
    }

    private void record(LocalDateTime time, int sensor, float value) {
        records.save(DataSensor.builder().sensor(sensors.findById(sensor).orElseThrow())
                .createdAt(time).value(value).build());
    }

    @Test
    void emptyDatabaseReturnsNullAndEmptyCollections() throws Exception {
        assertThat(service.getLatestData()).isNull();
        assertThat(service.getChartData()).isEmpty();
        var page = service.getHistory(5, 10, "", "", "", "desc");
        assertThat(page.getData()).isEmpty();
        assertThat(page.getTotalRecords()).isZero();
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getCurrentPage()).isEqualTo(1);
        mvc.perform(get("/api/sensors/latest").header("Authorization", "Bearer " + tokens.generateTokenFromUsername("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.message").value("Chưa có dữ liệu cảm biến"));
    }

    @Test
    void snapshotsNeverMixIncompleteOrAmbiguousMeasurementsAndKeepRealZero() {
        sample(START, 0, 0, 0);
        record(START.plusMinutes(1), 1, 99);
        sample(START.plusMinutes(2), 20, 30, 40);
        record(START.plusMinutes(2), 1, 88); // Cùng timestamp có hai nhiệt độ: không đoán.
        records.flush();
        var latest = service.getLatestData();
        assertThat(latest.getTemperature()).isZero();
        assertThat(latest.getHumidity()).isZero();
        assertThat(latest.getLight()).isZero();
        assertThat(latest.getTimestamp()).isEqualTo("2026-10-07T10:00:00+07:00");
        assertThat(service.getChartData()).hasSize(1);
        assertThat(service.getChartData().getFirst().getTimestamp()).isEqualTo(latest.getTimestamp());
    }

    @Test
    void chartContainsLatestFifteenCompleteMeasurementsInAscendingOrder() {
        for (int i = 0; i < 17; i++) sample(START.plusMinutes(i), i, 50, 650);
        var chart = service.getChartData();
        assertThat(chart).hasSize(15);
        assertThat(chart.getFirst().getTemperature()).isEqualTo(2f);
        assertThat(chart.getLast().getTemperature()).isEqualTo(16f);
        assertThat(chart.getLast().getTimestamp()).isEqualTo(service.getLatestData().getTimestamp());
        assertThat(chart.getFirst().getTime()).isEqualTo("10:02:00");
    }

    @Test
    void historySupportsExactSensorValueTimeAndAllFieldsSearch() {
        sample(START, 30.5f, 83, 850);
        sample(START.plusDays(1), 31.2f, 80, 754);
        assertThat(service.getHistory(1, 10, "Nhiệt Độ", "30.5", "value", "desc").getTotalRecords()).isEqualTo(1);
        assertThat(service.getHistory(1, 10, "Độ Ẩm", "", "", "desc").getTotalRecords()).isEqualTo(2);
        assertThat(service.getHistory(1, 10, "Ánh Sáng", "", "", "desc").getTotalRecords()).isEqualTo(2);
        assertThat(service.getHistory(1, 10, "", "2026-10-07", "time", "desc").getTotalRecords()).isEqualTo(3);
        assertThat(service.getHistory(1, 10, "", "10:00", "time", "desc").getTotalRecords()).isEqualTo(6);
        assertThat(service.getHistory(1, 10, "", "nhiệt", "", "desc").getTotalRecords()).isEqualTo(2);
        assertThat(service.getHistory(1, 10, "", "850", "", "desc").getTotalRecords()).isEqualTo(1);
        assertThat(service.getHistory(1, 10, "", "%", "", "desc").getData()).isEmpty();
        assertThat(service.getHistory(1, 10, "", "_", "time", "desc").getData()).isEmpty();
    }

    @Test
    void paginationIsStableAndOutOfRangeNeverRepeatsFirstPage() {
        sample(START, 30, 60, 650);
        var first = service.getHistory(1, 2, "", "", "", "asc");
        var second = service.getHistory(2, 2, "", "", "", "asc");
        assertThat(first.getTotalRecords()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getData()).hasSize(2);
        assertThat(second.getData()).hasSize(1);
        List<Integer> ids = records.findAll().stream().map(DataSensor::getId).sorted().toList();
        assertThat(first.getData().getFirst().getId()).isEqualTo(ids.getFirst());
        assertThat(service.getHistory(1, 2, "", "", "", "desc").getData().getFirst().getId()).isEqualTo(ids.getLast());
        var beyond = service.getHistory(10, 2, "", "", "", "asc");
        assertThat(beyond.getCurrentPage()).isEqualTo(10);
        assertThat(beyond.getData()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"page,0", "page,-1", "limit,0", "limit,101", "sort,wrong",
            "sensor_type,Nhiệt", "search_type,wrong", "page,abc"})
    void invalidQueryParametersReturn400(String field, String value) throws Exception {
        mvc.perform(get("/api/sensors/history").param(field, value)
                .header("Authorization", "Bearer " + tokens.generateTokenFromUsername("admin")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void historyEndpointAcceptsFrontendQueryAndReturnsPaginationPayload() throws Exception {
        sample(START, 30.5f, 83, 850);
        mvc.perform(get("/api/sensors/history").param("sensor_type", "Nhiệt Độ")
                .param("search_type", "value").param("search", "30.5")
                .header("Authorization", "Bearer " + tokens.generateTokenFromUsername("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total_records").value(1))
                .andExpect(jsonPath("$.data.total_pages").value(1))
                .andExpect(jsonPath("$.data.current_page").value(1))
                .andExpect(jsonPath("$.data.data[0].sensor_name").value("Nhiệt Độ"))
                .andExpect(jsonPath("$.data.data[0].created_at").value("2026-10-07T10:00:00+07:00"));
        mvc.perform(get("/api/sensors/history").param("search", "x".repeat(101))
                .header("Authorization", "Bearer " + tokens.generateTokenFromUsername("admin")))
                .andExpect(status().isBadRequest());
    }
}
