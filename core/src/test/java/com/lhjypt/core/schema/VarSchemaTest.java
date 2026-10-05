package com.lhjypt.core.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VaR 表结构静态校验（init-p1.sql 幂等脚本）。
 */
class VarSchemaTest {

    @Test
    void initP1ContainsVarCalculationTable() throws IOException {
        String sql = readProjectFile("scripts/init-p1.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS var_calculation"));
        assertTrue(sql.contains("calc_id"));
        assertTrue(sql.contains("scope_type"));
        assertTrue(sql.contains("method"));
        assertTrue(sql.contains("confidence"));
        assertTrue(sql.contains("horizon_days"));
        assertTrue(sql.contains("var_value"));
        assertTrue(sql.contains("DECIMAL(20,8)"));
        assertTrue(sql.contains("UNIQUE(scope_type, scope_id, calc_time)"));
    }

    private String readProjectFile(String relativePath) throws IOException {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("project file not found: " + relativePath);
    }
}
