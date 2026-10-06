# TASK-P2-002 策略水印与运行环境绑定

> 需求：`requirements-supplement.md` 模块三 §3.4（知识产权保护）
> 依赖：P1-009 策略加密与沙箱

## 节点
1. `strategy_watermark` 表（init-p2.sql + 迁移 p2_002）。
2. `StrategyWatermarkService`：令牌派生(SHA-256(策略|作者|上传时间))、嵌入/提取标记 `# lhjypt-watermark-<64hex>`、按令牌溯源、环境指纹(hostname+MAC)。
3. `StrategySecurityService.upload`：对带水印源码加密与校验，登记水印行；`run` 前比对指纹。
4. 接口：`GET /api/strategy/{id}/watermark`、`POST /api/strategy/trace`（仅合规/管理员）。
5. 测试 + E2E。

## 验收
- 上传自动打水印；密文/校验和针对带水印源码；下载返回含标记源码。
- 泄露副本扫描标记 → 溯源到策略/作者/上传者。
- 运行前指纹不一致拒绝（疑似拷贝）；关闭开关可豁免。
- TRADER 溯源 403。