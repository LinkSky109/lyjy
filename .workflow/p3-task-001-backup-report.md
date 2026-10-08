# P3-001 数据备份与恢复 · 测试报告

> 分支：feature/p3-001-backup · 日期：2026-10-08 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `scripts/backup.sh` | `backup/list/verify/restore` 四命令；PG 逻辑备份 + CH 关键表 Native 导出 + 配置打包（排除密钥）+ manifest（行数/sha256）+ 归档 sha256 |
| `docs/backup.md` | 操作、归档内容、恢复行为、推荐节奏、演练记录 |
| `.gitignore` | 新增 `backups/`（归档不入库） |

## 2. 演练（真实数据端到端）
| 步骤 | 结果 |
|------|------|
| 备份基线 | tenants 1 / strategies 3 / aml_rules 2 / opt_archive 6 / bar_data 788 |
| 备份 | 173K 归档 + sha256 校验文件 |
| 破坏（模拟事故） | 删 PG 表数据 + `TRUNCATE bar_data` → 全部为 0 |
| 恢复 | PG `DROP/RE-CREATE schema` 导入 + CH 回灌（空表 tick_data 自动跳过） |
| 校验 | **全部与基线一致**：tenants 1 / strategies 3 / aml_rules 2 / opt_archive 6 / bar_data 788 / metric_sample 74475 |
| 服务 | `dev.sh up` 后健康 UP |

## 3. 过程中修复
- `trap` 在函数返回后引用已销毁的 `local work`（`set -u` 报 unbound）→ `trap '... "${work:-}"'`。
- CH 空表（tick_data）回灌触发 `NO_DATA_TO_INSERT` 中断 → 空表自动跳过。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| 脚本语法 | `bash -n` 通过 |
| 备份/恢复演练 | 通过（数据完整一致） |
| 后端 | 恢复后重启健康（无需代码测试；`mvn` 基线 288/288 不受影响） |

## 5. 结论
一键备份/校验/恢复可用，数据可靠性基础就绪（P3-001 完成）。