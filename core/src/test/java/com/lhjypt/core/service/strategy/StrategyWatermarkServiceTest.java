package com.lhjypt.core.service.strategy;

import com.lhjypt.core.mapper.StrategyWatermarkMapper;
import com.lhjypt.core.model.strategy.StrategyWatermark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyWatermarkServiceTest {

    private static final String SOURCE = "class Demo:\n    pass\n    \n";
    private static final String TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private StrategyWatermarkMapper mapper;
    private StrategyWatermarkService service;

    @BeforeEach
    void setUp() {
        mapper = mock(StrategyWatermarkMapper.class);
        service = new StrategyWatermarkService(mapper);
    }

    @Test
    @DisplayName("令牌派生确定且与作者/时间相关")
    void tokenIsDeterministicAndSensitive() {
        assertThat(service.token("S1", "link", 1000L))
                .isEqualTo(service.token("S1", "link", 1000L));
        assertThat(service.token("S1", "link", 1000L))
                .isNotEqualTo(service.token("S1", "link", 2000L));
        assertThat(service.token("S1", "link", 1000L))
                .isNotEqualTo(service.token("S1", "alice", 1000L));
    }

    @Test
    @DisplayName("嵌入后可从副本提取水印令牌")
    void embedAndExtractRoundtrip() {
        String watermarked = service.embed(SOURCE, TOKEN);
        assertThat(watermarked).contains(StrategyWatermarkService.MARKER_PREFIX + TOKEN);
        assertThat(service.extract(watermarked)).contains(TOKEN);
    }

    @Test
    @DisplayName("无标记时不误报")
    void extractAbsentReturnsEmpty() {
        assertThat(service.extract("print(1)")).isEmpty();
        assertThat(service.extract("")).isEmpty();
        assertThat(service.extract(null)).isEmpty();
    }

    @Test
    @DisplayName("按令牌溯源命中登记记录")
    void traceByTokenHitsRegistry() {
        StrategyWatermark watermark = new StrategyWatermark();
        watermark.setToken(TOKEN);
        watermark.setStrategyId("S1");
        watermark.setOwner("link");
        when(mapper.selectByToken(TOKEN)).thenReturn(watermark);

        assertThat(service.trace(TOKEN)).isPresent().get().extracting(StrategyWatermark::getOwner)
                .isEqualTo("link");
        // 带标记的泄露源码片段也能命中
        assertThat(service.trace(service.embed("pass", TOKEN))).isPresent();
    }

    @Test
    @DisplayName("trace 未命中返回空")
    void traceMissingReturnsEmpty() {
        when(mapper.selectByToken("deadbeef")).thenReturn(null);
        assertThat(service.trace("deadbeef")).isEmpty();
    }

    @Test
    @DisplayName("环境指纹非空且短时稳定")
    void fingerprintIsStable() {
        assertThat(service.currentFingerprint()).isNotBlank().hasSize(64);
        assertThat(service.currentFingerprint()).isEqualTo(service.currentFingerprint());
    }
}