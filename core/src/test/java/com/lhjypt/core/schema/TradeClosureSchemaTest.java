package com.lhjypt.core.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeClosureSchemaTest {

    @Test
    void freshSchemaContainsTradeClosureTables() throws IOException {
        String sql = readProjectFile("scripts/init-db.sql");

        assertTrue(sql.contains("offset_flag VARCHAR(16)"));
        assertTrue(sql.contains("broker_order_ref VARCHAR(64)"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS trade_record"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS order_fund_reservation"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS gateway_report_log"));
        assertTrue(sql.contains("consumed_margin DECIMAL(20,8)"));
        assertTrue(sql.contains("deleted SMALLINT DEFAULT 0"));
    }

    @Test
    void incrementalMigrationIsAdditiveAndIdempotent() throws IOException {
        String sql = readProjectFile("scripts/migration/p15_001_trade_closure.sql");

        List<String> requiredFragments = List.of(
                "ADD COLUMN IF NOT EXISTS offset_flag",
                "ADD COLUMN IF NOT EXISTS update_time",
                "CREATE TABLE IF NOT EXISTS trade_record",
                "CREATE TABLE IF NOT EXISTS order_fund_reservation",
                "CREATE TABLE IF NOT EXISTS gateway_report_log",
                "CREATE INDEX IF NOT EXISTS"
        );
        for (String fragment : requiredFragments) {
            assertTrue(sql.contains(fragment), "missing migration fragment: " + fragment);
        }

        String upper = sql.toUpperCase();
        assertFalse(upper.contains("DROP TABLE"));
        assertFalse(upper.contains("TRUNCATE"));
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
