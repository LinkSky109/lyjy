package com.lhjypt.core.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 监管报送与 AML 表结构静态校验（init-p2.sql 幂等脚本）。
 */
class RegulatorySchemaTest {

    @Test
    void initP2ContainsRegulatoryAndAmlTables() throws IOException {
        String sql = readProjectFile("scripts/init-p2.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS regulatory_report_batch"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS regulatory_report_item"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS aml_rule"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS aml_alert"));
        assertTrue(sql.contains("UNIQUE(report_type, report_date)"));
        assertTrue(sql.contains("LARGE_SINGLE"));
        assertTrue(sql.contains("LARGE_CUMULATIVE"));
        assertTrue(sql.contains("ON CONFLICT (rule_id) DO NOTHING"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_watermark"));
        assertTrue(sql.contains("env_fingerprint"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS risk_budget"));
        assertTrue(sql.contains("UNIQUE(scope_type, scope_id, budget_type)"));
        assertTrue(sql.contains("usage_rate"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS attribution_report"));
        assertTrue(sql.contains("result_json"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS settlement_rule"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS settlement_task"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS currency_rate"));
        assertTrue(sql.contains("expected_date"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tenant"));
        assertTrue(sql.contains("max_strategies"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS capacity_snapshot"));
        assertTrue(sql.contains("load_pct"));
    }

    @Test
    void capacityMigrationIsAdditive() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_007_capacity.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS capacity_snapshot"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void tenantPortfolioMigrationScopesPortfolioAndAddsQuota() throws IOException {
        String init = readProjectFile("scripts/init-p2.sql");
        assertTrue(init.contains("ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS tenant_id"));
        assertTrue(init.contains("max_portfolios"));
        String sql = readProjectFile("scripts/migration/p2_008_tenant_portfolio.sql");
        assertTrue(sql.contains("ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS tenant_id"));
        assertTrue(sql.contains("ALTER TABLE tenant ADD COLUMN IF NOT EXISTS max_portfolios"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void tenantRiskBudgetMigrationScopesRiskBudget() throws IOException {
        String init = readProjectFile("scripts/init-p2.sql");
        assertTrue(init.contains("ALTER TABLE risk_budget ADD COLUMN IF NOT EXISTS tenant_id"));
        String sql = readProjectFile("scripts/migration/p2_009_tenant_risk_budget.sql");
        assertTrue(sql.contains("ALTER TABLE risk_budget ADD COLUMN IF NOT EXISTS tenant_id"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void optimizationMigrationIsAdditive() throws IOException {
        String init = readProjectFile("scripts/init-p2.sql");
        assertTrue(init.contains("CREATE TABLE IF NOT EXISTS strategy_optimization"));
        assertTrue(init.contains("best_fast"));
        String sql = readProjectFile("scripts/migration/p2_011_strategy_optimization.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_optimization"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void strategyCircuitMigrationIsAdditive() throws IOException {
        String init = readProjectFile("scripts/init-p2.sql");
        assertTrue(init.contains("CREATE TABLE IF NOT EXISTS strategy_circuit"));
        assertTrue(init.contains("max_drawdown_pct"));
        String sql = readProjectFile("scripts/migration/p2_012_strategy_circuit.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_circuit"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void portfolioCircuitMigrationIsAdditive() throws IOException {
        String init = readProjectFile("scripts/init-p2.sql");
        assertTrue(init.contains("CREATE TABLE IF NOT EXISTS portfolio_circuit"));
        assertTrue(init.contains("member_strategies"));
        String sql = readProjectFile("scripts/migration/p2_013_portfolio_circuit.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS portfolio_circuit"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void tenantMigrationIsAdditiveAndScopesStrategyInfo() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_006_multi_tenant.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tenant"));
        assertTrue(sql.contains("ALTER TABLE strategy_info ADD COLUMN IF NOT EXISTS tenant_id"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void settlementFxMigrationIsAdditive() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_005_settlement_fx.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS settlement_task"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS currency_rate"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void attributionMigrationIsAdditive() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_004_attribution.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS attribution_report"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void riskBudgetMigrationIsAdditive() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_003_risk_budget.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS risk_budget"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void watermarkMigrationIsAdditive() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_002_strategy_watermark.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS strategy_watermark"));
        assertTrue(sql.contains("token          VARCHAR(64) NOT NULL UNIQUE"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
    }

    @Test
    void migrationIsAdditive() throws IOException {
        String sql = readProjectFile("scripts/migration/p2_001_regulatory_aml.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS regulatory_report_batch"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS aml_alert"));
        String upper = sql.toUpperCase();
        assertTrue(!upper.contains("DROP TABLE"));
        assertTrue(!upper.contains("TRUNCATE"));
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