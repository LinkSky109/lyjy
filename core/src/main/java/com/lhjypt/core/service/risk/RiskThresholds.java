package com.lhjypt.core.service.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 风控阈值 JSON 解析与数值校验。
 */
public final class RiskThresholds {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RiskThresholds() {
    }

    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("阈值参数必须是 JSON 对象");
            }
            return node;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("阈值参数不是合法 JSON: " + error.getMessage());
        }
    }

    public static int positiveInt(JsonNode node, String field, int fallback) {
        if (!node.has(field)) {
            return fallback;
        }
        int value = node.get(field).asInt(Integer.MIN_VALUE);
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正整数");
        }
        return value;
    }

    public static double ratio(JsonNode node, String field, double fallback) {
        if (!node.has(field)) {
            return fallback;
        }
        double value = node.get(field).asDouble(Double.NaN);
        if (!Double.isFinite(value) || value <= 0 || value > 1) {
            throw new IllegalArgumentException(field + " 必须在 (0, 1] 区间");
        }
        return value;
    }
}
