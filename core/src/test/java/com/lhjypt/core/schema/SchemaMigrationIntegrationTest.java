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

            assertTrue(columnExists(connection, "orders", "offset_flag"));
            assertTrue(columnExists(connection, "orders", "broker_order_ref"));
            assertTrue(columnExists(connection, "orders", "gateway_id"));
            assertTrue(tableExists(connection, "trade_record"));
            assertTrue(columnExists(connection, "trade_record", "gateway_id"));
            assertTrue(columnExists(connection, "trade_record", "gateway_session_id"));
            assertTrue(tableExists(connection, "order_fund_reservation"));
            assertTrue(tableExists(connection, "gateway_report_log"));
            assertTrue(tableExists(connection, "position_reconciliation"));
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
