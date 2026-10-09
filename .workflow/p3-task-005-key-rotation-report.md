# P3-005 密钥与审计加固 · 测试报告

> 分支：feature/p3-005-key-rotation · 日期：2026-10-09 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `KeyRotationService` | 状态（活跃 keyId + 各 keyId 使用计数）；**轮换**：旧密钥解密 → 新密钥重加密（更新 ciphertext/keyId/checksum）+ 审计 `KEY_ROTATE`；新密钥不可用即**快速失败** |
| `KeyRotationController` | `GET /api/strategy/keys/status` · `POST /api/strategy/keys/rotate`（需 `X-Confirm-Token`） |
| `AuthInterceptor` | `/api/strategy/keys/*` **读+写均仅 ADMIN**（含敏感读限制） |
| `StrategyCryptoMapper.selectKeyUsage` | 各密钥使用计数 |
| `docs/security.md` | 密钥体系、轮换流程（含 ACTIVE_KEY_ID 切换）、审计覆盖清单、残留风险 |

## 2. 实测（env 双密钥真实 E2E）
| 步骤 | 结果 |
|------|------|
| env provider + k1/k2 启动 | 后端 UP |
| 上传策略（活跃 k1） | `keyId=k1` |
| 密钥状态 | `activeKeyId=k1, usage=[{k1:1}]` |
| 非 ADMIN 访问状态 | **HTTP 403** |
| 轮换到 k2（带确认令牌） | `rotated=1, skipped=0, failed=0` |
| 轮换后状态 + 下载 | usage `[{k2:1}]`；下载解密成功（含明文） |
| 审计 | `__KEYSTORE__ / KEY_ROTATE / admin / {newKeyId:k2,rotated:1}` |

## 3. 过程中的修复
- `StrategyCryptoMapper` 缺 `java.util.List` import → 补。
- 测试 stub `selectList(any())` 不匹配 `null` 参数 → 改显式 `selectList(null)`。
- 测试密钥 K2 Base64 解码为 35 字节（非法 AES-256）→ 换合法 32 字节密钥。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| `KeyRotationServiceTest` | 3/3（轮换+可解密+审计 / 跳过+未知密钥拒绝 / 状态） |
| 全量 `mvn verify` | **291/291**（0 跳过，JaCoCo 通过） |
| 启动验证 | env 双密钥下可启动并完成轮换 |

## 5. 说明
- 轮换不改变 `activeKeyId`（配置决定）——完整流程：`rotate` 存量 → 切换 `ACTIVE_KEY_ID` 重启 → 新上传用新密钥（见 `docs/security.md`）。
- 与 P3-001 呼应：备份归档不含密钥文件，恢复后需配置密钥。

## 6. 结论
密钥轮换（存量重加密+审计）与密钥接口权限加固完成（P3-005）。
