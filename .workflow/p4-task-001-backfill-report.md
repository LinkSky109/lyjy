# P4-001 行情分页回补 · 任务报告

- 任务：P4-001 行情分页翻页回补（更长历史）
- 依据：`.workflow/delivery-report-p3.md` §6「行情分页翻页回补（更长历史）」
- 分支：`feature/p4-001-market-backfill`
- 状态：DONE

## 背景

此前 `POST /api/market/real/refresh` 单次最多拉取 1000 根 K 线，无法回补更长历史。

## 改动

| 层 | 文件 | 说明 |
|----|------|------|
| SPI | `MarketDataSource` | 新增游标重载 `fetch(pair,interval,limit,endTimeSec)`（默认退化为单页）+ `supportsBackfill()` |
| 源 | `GateioKlineSource` / `GateioMarketDataSource` | 支持 `to`（秒）游标 + `buildUrl` |
| 源 | `BinanceVisionMarketDataSource` | 支持 `endTime`（毫秒）游标 + `buildUrl` |
| 服务 | `RealMarketDataService` | 新增 `backfill(...)`（游标翻页 + 跨页去重 + 粘性源 + 防死循环）+ `BackfillResult`；抽取 `persist`/`loadExisting(范围)` |
| 接口 | `RealMarketController` | `POST /api/market/real/backfill`（ADMIN） |
| 权限 | `AuthInterceptor` | `/api/market/real/backfill` 限 ADMIN 写 |
| 配置 | `RealMarketProperties` + `application.yaml` | `backfill-page-size`(1000) / `backfill-max-pages`(10) |

## 测试

- 单元：`RealMarketDataServiceTest` 新增 7 例（跨页翻页至不足一页 / 达目标停止 / 无新增防死循环 / 去重 / 指定源 / 非分页源退化单页 / 全失败抛错）；`GateioKlineSourceTest` +1（buildUrl 游标）；新增 `BinanceVisionMarketDataSourceTest` 2 例（buildUrl + 解析）。
- 全量：`mvn verify` **304 通过 / 0 失败**（新增 10；`SchemaMigrationIntegrationTest` 因本机 Docker API 版本不匹配被 `disabledWithoutDocker` 跳过，属既有环境限制，本任务无 schema 变更）。
- 真实 E2E（服务 8082 连本地中间件，`source=binance-vision`）：
  - 回补 `BTC_USDT@1h`：327 根 → 3 页 **fetched 3000 / inserted 2673** → ClickHouse **3000 根**（2026-06-07 ~ 2026-10-10），`uniqExact(bar_time)` 差值 **0**（无重复）。
  - 重复回补：`inserted=0`（跨 >1000 根去重生效）。
  - 默认源顺序单页：`pages=1, src=gateio` 成功（主源可用）。
  - 权限：TRADER 调用返回 **403**（ADMIN-only）。
  - 质量：3000 根 **0 缺口 / 0 异常 / 0 重复**。

## 已知项 / 后续
- 逐行写入较慢（3000 行 ~16s，含 3 次外呼）；如后续需要更大批量可改批量 insert（非本次范围）。
- P4-002 时区口径统一仍为候选（跨切面，需独立评估）。
