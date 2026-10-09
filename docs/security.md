# 密钥与审计加固（P3-005）

## 1. 密钥体系

| 组件 | 说明 |
|------|------|
| `CryptoKeyProvider` SPI | `activeKeyId()` + `key(keyId)`（32 字节 AES-256） |
| `LocalCryptoKeyProvider` | 默认：`config/key-store.properties`（已 gitignore；缺失时开发兜底） |
| `EnvCryptoKeyProvider` | `LHJYPT_CRYPTO_PROVIDER=env`；env 名 = `LHJYPT_STRATEGY_KEY_<KEYID大写·非字母数字→下划线>`，Base64 32B，非法快速失败 |
| `HttpKmsCryptoKeyProvider` | `provider=kms`：`GET {kms-url}/keys/{keyId}`（Bearer），生产 KMS/HSM 接入点 |
| 算法 | AES-256-GCM（随机 IV，密文 `Base64(IV‖cipher‖tag)`）+ SHA-256 校验和 |

## 2. 密钥轮换

```bash
# 1) 配置新密钥（以 env provider 为例：k1 活跃 + k2 待轮换）
LHJYPT_CRYPTO_PROVIDER=env LHJYPT_ACTIVE_KEY_ID=k1 \
LHJYPT_STRATEGY_KEY_K1=<base64-32B> LHJYPT_STRATEGY_KEY_K2=<base64-32B> java -jar ...

# 2) 查看密钥使用
curl http://localhost:8081/api/strategy/keys/status -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin'

# 3) 轮换存量密文到 k2（需一次性确认令牌，仅 ADMIN）
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/confirm-token -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
curl -X POST "http://localhost:8081/api/strategy/keys/rotate?newKeyId=k2" \
  -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin' -H "X-Confirm-Token: $TOKEN"

# 4) 切换新上传默认密钥：把 LHJYPT_ACTIVE_KEY_ID 改为 k2 并重启
```

- 轮换 = 存量密文用旧 key 解密 → 新 key 重加密（含校验和更新），全程审计 `KEY_ROTATE`。
- `rotate` 不改 `activeKeyId`（由配置决定），因此**第 4 步需切换 ACTIVE_KEY_ID** 以让新上传使用新密钥。
- 新密钥不可获取时**快速失败**（不做半途轮换）。

## 3. 审计覆盖清单

| 动作 | 落点 |
|------|------|
| 策略上传/下载/运行/停止/参数 | `strategy_audit_log`（UPLOAD/DOWNLOAD/RUN/STOP/EDIT_PARAM） |
| 密钥轮换 | `strategy_audit_log`（KEY_ROTATE，strategy_id=`__KEYSTORE__`） |
| 写操作（全局） | `AUDIT` logger（user/role/method/path） |
| 敏感操作二次确认 | 下载源码、报送提交、密钥轮换、强平、再平衡等（`X-Confirm-Token`，30s 一次性） |
| 风控处置 | `risk_disposition_log`、`alert_event`（含熔断 CRITICAL） |
| 权限边界 | `/api/strategy/keys/*` 仅 ADMIN（读+写）；跨租户/越权均 409/403 |

## 4. 残留风险与建议

- 本地 `key-store.properties` 为开发用；生产请用 `env`/`kms` 并提供密钥托管与轮换周期。
- 归档备份（`backups/`）**不含密钥文件**；恢复后需重新配置密钥方可解密策略。
- 建议密钥轮换周期 90 天，轮换后核对 `keys/status` usage 全为新 keyId。
