package com.iot.backend.dto.mqtt;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.StreamReadFeature;

/** Validated firmware payload; persistence follows the original timestamp-only ERD. */
public record SensorMeasurementPayload(float temperature, float humidity, float light) {
    public SensorMeasurementPayload {
        if (!Float.isFinite(temperature) || !Float.isFinite(humidity) || !Float.isFinite(light)
                || humidity < 0 || humidity > 100 || light < 0) {
            throw new IllegalArgumentException("Invalid sensor values");
        }
    }

    public static SensorMeasurementPayload parse(ObjectMapper mapper, String payload) {
        JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION).readTree(payload);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Expected a JSON object");
        return new SensorMeasurementPayload(
                number(root, "temperature"), number(root, "humidity"), number(root, "light"));
    }

    private static float number(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(field + " must be a number");
        double number = value.asDouble();
        if (!Double.isFinite(number) || !Float.isFinite((float) number)) {
            throw new IllegalArgumentException(field + " must be finite and fit in a float");
        }
        // Check bounds before float conversion, which can round values just outside the range.
        if (("humidity".equals(field) && (number < 0 || number > 100))
                || ("light".equals(field) && number < 0)) throw new IllegalArgumentException(field + " is out of range");
        return (float) number;
    }
}
