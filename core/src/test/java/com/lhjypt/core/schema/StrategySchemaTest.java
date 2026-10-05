package com.lhjypt.core.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略加密/沙箱表结构静态校验（init-p1.sql）。
 */
class StrategySchemaTest {

    @Test
    void initP1ContainsStrategyTables() throws IOException {
        String sql = readProjectFile("scripts/init-p1.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_info"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_crypto"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_audit_log"));
        assertTrue(sql.contains("ciphertext"));
        assertTrue(sql.contains("checksum"));
        assertTrue(sql.contains("key_id"));
        assertTrue(sql.contains("detail_json"));
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
