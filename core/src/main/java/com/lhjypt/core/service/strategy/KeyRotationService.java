package com.lhjypt.core.service.strategy;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.StrategyAuditLogMapper;
import com.lhjypt.core.mapper.StrategyCryptoMapper;
import com.lhjypt.core.model.strategy.StrategyAuditLog;
import com.lhjypt.core.model.strategy.StrategyCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略加密密钥轮换（P3-005）：把全部策略密文从旧 keyId 重加密到新 keyId，并审计留痕。
 * 前置：新 keyId 必须已配置且可获取（否则快速失败，不做半途轮换）。
 */
@Service
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);
    /** 密钥轮换为全局动作，审计记录使用占位策略 ID（strategy_id NOT NULL） */
    static final String AUDIT_STRATEGY_ID = "__KEYSTORE__";

    private final StrategyCryptoMapper cryptoMapper;
    private final StrategyCryptoService cryptoService;
    private final CryptoKeyProvider keyProvider;
    private final StrategyAuditLogMapper auditLogMapper;

    public KeyRotationService(StrategyCryptoMapper cryptoMapper,
                              StrategyCryptoService cryptoService,
                              CryptoKeyProvider keyProvider,
                              StrategyAuditLogMapper auditLogMapper) {
        this.cryptoMapper = cryptoMapper;
        this.cryptoService = cryptoService;
        this.keyProvider = keyProvider;
        this.auditLogMapper = auditLogMapper;
    }

    /** 密钥状态：活跃 keyId + 各 keyId 使用计数。 */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeKeyId", keyProvider.activeKeyId());
        result.put("usage", cryptoMapper.selectKeyUsage());
        return result;
    }

    /** 轮换到新 keyId；返回统计（rotated/skipped/failed）并写审计。 */
    @Transactional
    public Map<String, Object> rotate(String newKeyId, String operator, String ip) {
        if (newKeyId == null || newKeyId.isBlank()) {
            throw new IllegalArgumentException("newKeyId 不能为空");
        }
        try {
            keyProvider.key(newKeyId); // 快速失败：新密钥必须可获取
        } catch (Exception error) {
            throw new IllegalStateException("新密钥不可用: " + newKeyId + "（" + error.getMessage() + "）");
        }

        List<StrategyCrypto> all = cryptoMapper.selectList(null);
        int rotated = 0;
        int skipped = 0;
        int failed = 0;
        for (StrategyCrypto crypto : all) {
            if (newKeyId.equals(crypto.getKeyId())) {
                skipped++;
                continue;
            }
            try {
                String plaintext = cryptoService.decrypt(crypto.getCiphertext(), crypto.getKeyId());
                crypto.setCiphertext(cryptoService.encrypt(plaintext, newKeyId));
                crypto.setKeyId(newKeyId);
                crypto.setChecksum(cryptoService.sha256(plaintext));
                crypto.setAlgo("AES_256_GCM");
                crypto.setUpdateTime(LocalDateTime.now());
                cryptoMapper.updateById(crypto);
                rotated++;
            } catch (Exception error) {
                failed++;
                log.warn("密钥轮换失败 strategy={}: {}", crypto.getStrategyId(), error.getMessage());
            }
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("newKeyId", newKeyId);
        detail.put("total", all.size());
        detail.put("rotated", rotated);
        detail.put("skipped", skipped);
        detail.put("failed", failed);
        StrategyAuditLog audit = new StrategyAuditLog();
        audit.setStrategyId(AUDIT_STRATEGY_ID);
        audit.setAction("KEY_ROTATE");
        audit.setOperator(operator);
        audit.setDetailJson(JSON.toJSONString(detail));
        audit.setIpAddr(ip);
        audit.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(audit);

        log.info("密钥轮换完成: ->{} 共{} 轮换{} 跳过{} 失败{}",
                newKeyId, all.size(), rotated, skipped, failed);
        Map<String, Object> result = new LinkedHashMap<>(detail);
        result.put("activeKeyId", keyProvider.activeKeyId());
        return result;
    }
}