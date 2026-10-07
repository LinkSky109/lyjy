package com.lhjypt.core.service.strategy;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.common.tenant.TenantContextHolder;
import com.lhjypt.core.config.StrategySecurityProperties;
import com.lhjypt.core.mapper.StrategyAuditLogMapper;
import com.lhjypt.core.mapper.StrategyCryptoMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.strategy.StrategyAuditLog;
import com.lhjypt.core.model.strategy.StrategyCrypto;
import com.lhjypt.core.model.strategy.StrategyInfo;
import com.lhjypt.core.model.strategy.StrategyWatermark;
import com.lhjypt.core.model.tenant.Tenant;
import com.lhjypt.core.service.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 策略加密、沙箱与审计编排（PRD §5.8）。
 *
 * <p>明文边界：源码明文只在进程内流转，日志/审计/列表接口均不输出；
 * 仅携带一次性确认令牌的下载接口向归属人返回源码。
 */
@Service
@RequiredArgsConstructor
public class StrategySecurityService {

    private static final Logger log = LoggerFactory.getLogger(StrategySecurityService.class);

    private final StrategyInfoMapper strategyInfoMapper;
    private final StrategyCryptoMapper strategyCryptoMapper;
    private final StrategyAuditLogMapper auditLogMapper;
    private final StrategyCryptoService cryptoService;
    private final SandboxExecutor sandboxExecutor;
    private final ConfirmTokenService confirmTokenService;
    private final StrategyWatermarkService watermarkService;
    private final StrategySecurityProperties securityProperties;
    private final TenantService tenantService;

    @Transactional
    public Map<String, Object> upload(String strategyId, String name, String sourceCode,
                                      UserContext context, String ip) {
        requireText(strategyId, "strategyId 不能为空");
        requireText(sourceCode, "策略源码不能为空");

        StrategyInfo strategy = strategyInfoMapper.selectByStrategyId(strategyId);
        if (strategy == null) {
            strategy = new StrategyInfo();
            strategy.setStrategyId(strategyId);
            strategy.setName(name == null || name.isBlank() ? strategyId : name);
            strategy.setOwner(context.userId());
            strategy.setStatus(StrategyInfo.OFFLINE);
            strategy.setLang("PYTHON");
            strategy.setParamsJson("{}");
            strategy.setCreateTime(LocalDateTime.now());
            strategy.setUpdateTime(LocalDateTime.now());
            // 多租户：记录归属租户并校验配额
            String tenant = TenantContextHolder.get();
            tenantService.requireActive(tenant);
            if (!TenantContextHolder.DEFAULT_TENANT.equals(tenant)) {
                Tenant tenantInfo = tenantService.detail(tenant);
                if (tenantInfo != null && strategyInfoMapper.countByTenant(tenant)
                        >= tenantInfo.getMaxStrategies()) {
                    throw new IllegalStateException("租户策略配额已用完: " + tenant);
                }
            }
            strategy.setTenantId(tenant);
            strategyInfoMapper.insert(strategy);
        } else {
            requireOwner(strategy, context);
        }

        // 嵌入不可见水印（对原型透明、可随副本携带溯源），密文/校验和针对带水印源码
        String token = watermarkService.token(strategyId, context.userId(), System.currentTimeMillis());
        String watermarkSource = watermarkService.embed(sourceCode, token);
        String keyId = cryptoService.activeKeyId();
        String checksum = cryptoService.sha256(watermarkSource);
        String ciphertext = cryptoService.encrypt(watermarkSource, keyId);

        StrategyCrypto crypto = strategyCryptoMapper.selectByStrategyId(strategyId);
        int version = 1;
        if (crypto == null) {
            crypto = new StrategyCrypto();
            crypto.setStrategyId(strategyId);
            crypto.setCreateTime(LocalDateTime.now());
        } else {
            version = (crypto.getVersion() == null ? 1 : crypto.getVersion()) + 1;
        }
        crypto.setVersion(version);
        crypto.setAlgo(StrategyCrypto.ALGO_AES_GCM);
        crypto.setCiphertext(ciphertext);
        crypto.setKeyId(keyId);
        crypto.setChecksum(checksum);
        crypto.setUploadedBy(context.userId());
        crypto.setUpdateTime(LocalDateTime.now());
        if (crypto.getId() == null) {
            strategyCryptoMapper.insert(crypto);
        } else {
            strategyCryptoMapper.updateById(crypto);
        }

        audit(strategyId, StrategyAuditLog.UPLOAD, context.userId(), Map.of(
                "checksum", checksum,
                "keyId", keyId,
                "algo", StrategyCrypto.ALGO_AES_GCM,
                "bytes", watermarkSource.getBytes().length,
                "version", version), ip);

        // 记录水印与运行环境绑定指纹，泄露后可按令牌溯源
        watermarkService.save(strategyId, version, token, strategy.getOwner(),
                context.userId(), checksum, watermarkService.currentFingerprint());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyId", strategyId);
        result.put("version", version);
        result.put("algo", StrategyCrypto.ALGO_AES_GCM);
        result.put("keyId", keyId);
        result.put("checksum", checksum);
        result.put("uploadedBy", context.userId());
        return result;
    }

    @Transactional
    public Map<String, Object> download(String strategyId, String confirmToken,
                                        UserContext context, String ip) {
        if (!confirmTokenService.validate(confirmToken)) {
            throw new IllegalStateException("下载策略源码需要有效的二次确认令牌");
        }
        StrategyInfo strategy = requireStrategy(strategyId);
        requireOwner(strategy, context);
        StrategyCrypto crypto = requireCrypto(strategyId);

        String plaintext = cryptoService.decrypt(crypto.getCiphertext(), crypto.getKeyId());
        verifyChecksum(plaintext, crypto.getChecksum(), strategyId);

        audit(strategyId, StrategyAuditLog.DOWNLOAD, context.userId(), Map.of(
                "version", crypto.getVersion(),
                "checksum", crypto.getChecksum()), ip);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyId", strategyId);
        result.put("version", crypto.getVersion());
        result.put("checksum", crypto.getChecksum());
        result.put("sourceCode", plaintext);
        return result;
    }

    @Transactional
    /** 公开归属校验（值守/调度等系统场景复用）：ADMIN 豁免；非归属或跨租户拒绝。 */
    public void requireStrategyOwnerOrAdmin(String strategyId, UserContext context) {
        requireOwner(requireStrategy(strategyId), context);
    }

    public Map<String, Object> run(String strategyId, UserContext context, String ip) {
        StrategyInfo strategy = requireStrategy(strategyId);
        requireOwner(strategy, context);
        StrategyCrypto crypto = requireCrypto(strategyId);

        String plaintext = cryptoService.decrypt(crypto.getCiphertext(), crypto.getKeyId());
        verifyChecksum(plaintext, crypto.getChecksum(), strategyId);

        // 运行环境绑定：上传指纹与当前宿主不一致则拒绝（防止拷贝到其他机器运行）
        if (securityProperties.isEnvBindingEnabled()) {
            StrategyWatermark watermark = watermarkService.latest(strategyId);
            if (watermark != null && watermark.getEnvFingerprint() != null
                    && !watermark.getEnvFingerprint()
                    .equals(watermarkService.currentFingerprint())) {
                throw new IllegalStateException(
                        "策略运行环境绑定校验失败，疑似被拷贝到其他主机运行: " + strategyId);
            }
        }

        SandboxHandle handle = sandboxExecutor.start(new SandboxRequest(
                strategyId, plaintext, strategy.getParamsJson(), false));
        strategy.setStatus(StrategyInfo.RUNNING);
        strategy.setUpdateTime(LocalDateTime.now());
        strategyInfoMapper.updateById(strategy);

        audit(strategyId, StrategyAuditLog.RUN, context.userId(), Map.of(
                "pid", handle.getPid(),
                "version", crypto.getVersion()), ip);
        return toStatus(strategyId, handle);
    }

    @Transactional
    public Map<String, Object> stop(String strategyId, UserContext context, String ip) {
        StrategyInfo strategy = requireStrategy(strategyId);
        requireOwner(strategy, context);
        boolean stopped = sandboxExecutor.stop(strategyId);
        strategy.setStatus(StrategyInfo.OFFLINE);
        strategy.setUpdateTime(LocalDateTime.now());
        strategyInfoMapper.updateById(strategy);

        audit(strategyId, StrategyAuditLog.STOP, context.userId(),
                Map.of("stopped", stopped), ip);
        return toStatus(strategyId, sandboxExecutor.status(strategyId));
    }

    public Map<String, Object> sandboxStatus(String strategyId, UserContext context) {
        StrategyInfo strategy = requireStrategy(strategyId);
        requireOwner(strategy, context);
        SandboxHandle handle = sandboxExecutor.status(strategyId);
        // 对账：运行态已结束但库内仍 RUNNING 时回落
        if (handle != null && !handle.isRunning()
                && StrategyInfo.RUNNING.equals(strategy.getStatus())) {
            strategy.setStatus(handle.getStatus().equals(SandboxHandle.EXITED)
                    ? StrategyInfo.OFFLINE : StrategyInfo.ERROR);
            strategy.setUpdateTime(LocalDateTime.now());
            strategyInfoMapper.updateById(strategy);
        }
        return toStatus(strategyId, handle);
    }

    public List<StrategyAuditLog> audit(String strategyId, String action, UserContext context) {
        if (context.hasAnyRole(UserContext.ROLE_ADMIN, UserContext.ROLE_RISK_OFFICER,
                UserContext.ROLE_COMPLIANCE)) {
            return auditLogMapper.selectByFilter(blankToNull(strategyId), blankToNull(action));
        }
        if (strategyId == null || strategyId.isBlank()) {
            List<StrategyAuditLog> logs = new ArrayList<>();
            for (StrategyInfo owned : strategyInfoMapper.selectByOwner(context.userId())) {
                logs.addAll(auditLogMapper.selectByFilter(owned.getStrategyId(),
                        blankToNull(action)));
            }
            return logs;
        }
        requireOwner(requireStrategy(strategyId), context);
        return auditLogMapper.selectByFilter(strategyId, blankToNull(action));
    }

    /** 查看策略水印登记信息（仅作者/管理员；不含源码）。 */
    public Map<String, Object> watermark(String strategyId, UserContext context) {
        StrategyInfo strategy = requireStrategy(strategyId);
        requireOwner(strategy, context);
        StrategyWatermark watermark = watermarkService.latest(strategyId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyId", strategyId);
        if (watermark == null) {
            result.put("watermarked", false);
            return result;
        }
        result.put("watermarked", true);
        result.put("token", watermark.getToken());
        result.put("owner", watermark.getOwner());
        result.put("uploadedBy", watermark.getUploadedBy());
        result.put("version", watermark.getVersion());
        result.put("sourceHash", watermark.getSourceHash());
        result.put("envFingerprint", watermark.getEnvFingerprint());
        result.put("createTime", watermark.getCreateTime());
        return result;
    }

    /** 溯源：扫描疑似泄露源码/令牌，定位策略、作者与上传者（仅合规/管理员）。 */
    public Map<String, Object> trace(String content, UserContext context) {
        if (!context.hasAnyRole(UserContext.ROLE_ADMIN, UserContext.ROLE_COMPLIANCE)) {
            throw new IllegalStateException("仅合规/管理员可执行泄露溯源");
        }
        Optional<StrategyWatermark> found = watermarkService.trace(content);
        Map<String, Object> result = new LinkedHashMap<>();
        if (found.isEmpty()) {
            result.put("found", false);
            result.put("message", "未识别到水印标记");
            return result;
        }
        StrategyWatermark watermark = found.get();
        result.put("found", true);
        result.put("token", watermark.getToken());
        result.put("strategyId", watermark.getStrategyId());
        result.put("strategyVersion", watermark.getVersion());
        result.put("owner", watermark.getOwner());
        result.put("uploadedBy", watermark.getUploadedBy());
        result.put("sourceHash", watermark.getSourceHash());
        result.put("uploadedAt", watermark.getCreateTime());
        return result;
    }

    private Map<String, Object> toStatus(String strategyId, SandboxHandle handle) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyId", strategyId);
        if (handle == null) {
            result.put("status", "OFFLINE");
            return result;
        }
        result.put("status", handle.getStatus());
        result.put("pid", handle.getPid());
        result.put("startedAt", handle.getStartedAt());
        result.put("finishedAt", handle.getFinishedAt());
        result.put("exitCode", handle.getExitCode());
        result.put("outputTail", sandboxExecutor.outputTail(strategyId));
        return result;
    }

    private void audit(String strategyId, String action, String operator,
                       Map<String, Object> detail, String ip) {
        StrategyAuditLog logEntry = new StrategyAuditLog();
        logEntry.setStrategyId(strategyId);
        logEntry.setAction(action);
        logEntry.setOperator(operator);
        logEntry.setDetailJson(JSON.toJSONString(detail));
        logEntry.setIpAddr(ip);
        logEntry.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(logEntry);
        log.info("策略审计: strategy={} action={} operator={}", strategyId, action, operator);
    }

    private StrategyInfo requireStrategy(String strategyId) {
        StrategyInfo strategy = strategyInfoMapper.selectByStrategyId(strategyId);
        if (strategy == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }
        return strategy;
    }

    private StrategyCrypto requireCrypto(String strategyId) {
        StrategyCrypto crypto = strategyCryptoMapper.selectByStrategyId(strategyId);
        if (crypto == null) {
            throw new IllegalStateException("策略尚未上传源码: " + strategyId);
        }
        return crypto;
    }

    private void requireOwner(StrategyInfo strategy, UserContext context) {
        if (context.hasAnyRole(UserContext.ROLE_ADMIN)) {
            return;
        }
        // 多租户数据隔离：非 ADMIN 只能访问本租户策略
        if (strategy.getTenantId() != null
                && !strategy.getTenantId().equals(TenantContextHolder.get())) {
            throw new IllegalStateException("跨租户访问被拒绝: " + strategy.getStrategyId());
        }
        if (!strategy.getOwner().equals(context.userId())) {
            throw new IllegalStateException("无权访问该策略: " + strategy.getStrategyId());
        }
    }

    private void verifyChecksum(String plaintext, String expected, String strategyId) {
        if (!cryptoService.sha256(plaintext).equals(expected)) {
            throw new IllegalStateException("策略校验失败，源码可能被篡改: " + strategyId);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
