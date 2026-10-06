package com.lhjypt.core.schema;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void appliesFreshSchemaAndIncrementalMigration() throws Exception {
        Path root = projectRoot();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(root.resolve("scripts/init-db.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p15_001_trade_closure.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p1_005_gateway_routing.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(root.resolve("scripts/init-p1.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p2_001_regulatory_aml.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p2_002_strategy_watermark.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p2_003_risk_budget.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p2_004_attribution.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p2_005_settlement_fx.sql")));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(
                            root.resolve("scripts/migration/p2_006_multi_tenant.sql")));

            assertTrue(columnExists(connection, "orders", "offset_flag"));
            assertTrue(columnExists(connection, "orders", "broker_order_ref"));
            assertTrue(columnExists(connection, "orders", "gateway_id"));
            assertTrue(tableExists(connection, "trade_record"));
            assertTrue(columnExists(connection, "trade_record", "gateway_id"));
            assertTrue(columnExists(connection, "trade_record", "gateway_session_id"));
            assertTrue(tableExists(connection, "order_fund_reservation"));
            assertTrue(tableExists(connection, "gateway_report_log"));
            assertTrue(tableExists(connection, "position_reconciliation"));
            assertTrue(tableExists(connection, "var_calculation"));
            assertTrue(columnExists(connection, "var_calculation", "calc_id"));
            assertTrue(columnExists(connection, "var_calculation", "var_value"));
            assertTrue(tableExists(connection, "strategy_info"));
            assertTrue(tableExists(connection, "strategy_crypto"));
            assertTrue(tableExists(connection, "strategy_audit_log"));
            assertTrue(columnExists(connection, "strategy_crypto", "ciphertext"));
            assertTrue(columnExists(connection, "strategy_audit_log", "detail_json"));
            assertTrue(tableExists(connection, "regulatory_report_batch"));
            assertTrue(tableExists(connection, "regulatory_report_item"));
            assertTrue(tableExists(connection, "aml_rule"));
            assertTrue(tableExists(connection, "aml_alert"));
            assertTrue(columnExists(connection, "aml_alert", "alert_id"));
            assertTrue(tableExists(connection, "strategy_watermark"));
            assertTrue(columnExists(connection, "strategy_watermark", "token"));
            assertTrue(columnExists(connection, "strategy_watermark", "env_fingerprint"));
            assertTrue(tableExists(connection, "risk_budget"));
            assertTrue(columnExists(connection, "risk_budget", "usage_rate"));
            assertTrue(columnExists(connection, "risk_budget", "budget_id"));
            assertTrue(tableExists(connection, "attribution_report"));
            assertTrue(columnExists(connection, "attribution_report", "result_json"));
            assertTrue(tableExists(connection, "settlement_rule"));
            assertTrue(tableExists(connection, "settlement_task"));
            assertTrue(columnExists(connection, "settlement_task", "expected_date"));
            assertTrue(tableExists(connection, "currency_rate"));
            assertTrue(columnExists(connection, "currency_rate", "rate"));
            assertTrue(tableExists(connection, "tenant"));
            assertTrue(columnExists(connection, "tenant", "max_strategies"));
            assertTrue(columnExists(connection, "strategy_info", "tenant_id"));
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws Exception {
        try (ResultSet result = connection.getMetaData()
                .getColumns(null, null, table, column)) {
            return result.next();
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet result = connection.getMetaData()
                .getTables(null, null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private static Path projectRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("scripts/init-db.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("project root not found");
    }
}
