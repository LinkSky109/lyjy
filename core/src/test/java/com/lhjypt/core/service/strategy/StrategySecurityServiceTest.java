package com.lhjypt.core.service.strategy;

import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.config.KeyStoreProperties;
import com.lhjypt.core.mapper.StrategyAuditLogMapper;
import com.lhjypt.core.mapper.StrategyCryptoMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.strategy.StrategyAuditLog;
import com.lhjypt.core.model.strategy.StrategyCrypto;
import com.lhjypt.core.model.strategy.StrategyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategySecurityServiceTest {

    private static final String SOURCE = "class Demo:\n    def on_init(self):\n        pass\n";

    private StrategyInfoMapper strategyInfoMapper;
    private StrategyCryptoMapper strategyCryptoMapper;
    private StrategyAuditLogMapper auditMapper;
    private StrategyCryptoService cryptoService;
    private SandboxExecutor sandboxExecutor;
    private ConfirmTokenService confirmTokenService;
    private StrategySecurityService service;

    private final UserContext trader = UserContext.of("trader-1", "TRADER", null);

    @BeforeEach
    void setUp() {
        strategyInfoMapper = mock(StrategyInfoMapper.class);
        strategyCryptoMapper = mock(StrategyCryptoMapper.class);
        auditMapper = mock(StrategyAuditLogMapper.class);
        KeyStoreProperties properties = new KeyStoreProperties();
        cryptoService = new StrategyCryptoService(new LocalCryptoKeyProvider(properties));
        sandboxExecutor = mock(SandboxExecutor.class);
        confirmTokenService = new ConfirmTokenService();
        service = new StrategySecurityService(strategyInfoMapper, strategyCryptoMapper,
                auditMapper, cryptoService, sandboxExecutor, confirmTokenService);
    }

    @Test
    @DisplayName("上传：注册策略、密文落库、返回校验和且不含明文")
    void uploadStoresCiphertextOnly() {
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(null);

        Map<String, Object> result = service.upload("S1", "demo", SOURCE, trader, "127.0.0.1");

        assertThat(result).containsEntry("version", 1);
        assertThat(result.get("checksum")).isEqualTo(cryptoService.sha256(SOURCE));
        assertThat(result).doesNotContainValue(SOURCE);

        ArgumentCaptor<StrategyCrypto> cryptoCaptor =
                ArgumentCaptor.forClass(StrategyCrypto.class);
        verify(strategyCryptoMapper).insert(cryptoCaptor.capture());
        StrategyCrypto stored = cryptoCaptor.getValue();
        assertThat(stored.getCiphertext()).isNotEqualTo(SOURCE).doesNotContain("on_init");
        assertThat(cryptoService.decrypt(stored.getCiphertext(), stored.getKeyId()))
                .isEqualTo(SOURCE);

        ArgumentCaptor<StrategyAuditLog> auditCaptor =
                ArgumentCaptor.forClass(StrategyAuditLog.class);
        verify(auditMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(StrategyAuditLog.UPLOAD);
        assertThat(auditCaptor.getValue().getDetailJson()).doesNotContain("on_init");
    }

    @Test
    @DisplayName("下载：有效令牌返回明文、无令牌拒绝")
    void downloadRequiresConfirmToken() {
        seedStrategy("trader-1");
        seedCrypto(SOURCE);

        assertThatThrownBy(() -> service.download("S1", null, trader, "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("二次确认");

        String token = confirmTokenService.issue("TRADER");
        Map<String, Object> result = service.download("S1", token, trader, "ip");

        assertThat(result.get("sourceCode")).isEqualTo(SOURCE);
        verify(auditMapper).insert(org.mockito.ArgumentMatchers.argThat(
                log -> StrategyAuditLog.DOWNLOAD.equals(log.getAction())));
    }

    @Test
    @DisplayName("非归属 TRADER 无法下载/运行他人策略")
    void ownerIsolation() {
        seedStrategy("trader-1");
        seedCrypto(SOURCE);
        UserContext other = UserContext.of("trader-2", "TRADER", null);

        assertThatThrownBy(() -> service.run("S1", other, "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无权");
        verify(sandboxExecutor, never()).start(any());
    }

    @Test
    @DisplayName("校验和不一致时拒绝运行")
    void tamperedChecksumRejected() {
        seedStrategy("trader-1");
        StrategyCrypto crypto = seedCrypto(SOURCE);
        crypto.setChecksum("deadbeef");

        assertThatThrownBy(() -> service.run("S1", trader, "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("篡改");
        verify(sandboxExecutor, never()).start(any());
    }

    @Test
    @DisplayName("运行：解密校验通过后启动沙箱并置 RUNNING")
    void runStartsSandbox() {
        StrategyInfo strategy = seedStrategy("trader-1");
        seedCrypto(SOURCE);
        when(sandboxExecutor.start(any())).thenReturn(handle("RUNNING"));

        Map<String, Object> result = service.run("S1", trader, "ip");

        assertThat(result).containsEntry("status", "RUNNING");
        assertThat(strategy.getStatus()).isEqualTo(StrategyInfo.RUNNING);
        verify(sandboxExecutor).start(any());
        verify(auditMapper).insert(org.mockito.ArgumentMatchers.argThat(
                log -> StrategyAuditLog.RUN.equals(log.getAction())));
    }

    @Test
    @DisplayName("停止：调用沙箱停止并回落 OFFLINE")
    void stopSandbox() {
        StrategyInfo strategy = seedStrategy("trader-1");
        when(sandboxExecutor.stop("S1")).thenReturn(true);
        when(sandboxExecutor.status("S1")).thenReturn(handle("STOPPED"));

        Map<String, Object> result = service.stop("S1", trader, "ip");

        assertThat(result).containsEntry("status", "STOPPED");
        assertThat(strategy.getStatus()).isEqualTo(StrategyInfo.OFFLINE);
        verify(auditMapper).insert(org.mockito.ArgumentMatchers.argThat(
                log -> StrategyAuditLog.STOP.equals(log.getAction())));
    }

    @Test
    @DisplayName("审计查询：COMPLIANCE 可查全部，TRADER 仅自属")
    void auditScope() {
        when(auditMapper.selectByFilter(eq("S1"), any())).thenReturn(List.of());
        UserContext compliance = UserContext.of("c1", "COMPLIANCE", null);
        service.audit("S1", null, compliance);
        verify(auditMapper).selectByFilter("S1", null);

        verify(strategyInfoMapper, never()).selectByStrategyId(anyString());
    }

    private StrategyInfo seedStrategy(String owner) {
        StrategyInfo strategy = new StrategyInfo();
        strategy.setStrategyId("S1");
        strategy.setOwner(owner);
        strategy.setStatus(StrategyInfo.OFFLINE);
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(strategy);
        return strategy;
    }

    private StrategyCrypto seedCrypto(String source) {
        StrategyCrypto crypto = new StrategyCrypto();
        crypto.setId(1L);
        crypto.setStrategyId("S1");
        crypto.setVersion(1);
        crypto.setAlgo(StrategyCrypto.ALGO_AES_GCM);
        crypto.setKeyId(cryptoService.activeKeyId());
        crypto.setCiphertext(cryptoService.encrypt(source, cryptoService.activeKeyId()));
        crypto.setChecksum(cryptoService.sha256(source));
        when(strategyCryptoMapper.selectByStrategyId("S1")).thenReturn(crypto);
        return crypto;
    }

    private static SandboxHandle handle(String status) {
        SandboxHandle handle = new SandboxHandle("S1", 12345L, null, null);
        handle.setStatus(status);
        return handle;
    }
}
