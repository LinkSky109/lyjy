package com.lhjypt.core.service.strategy;

import com.lhjypt.core.mapper.StrategyWatermarkMapper;
import com.lhjypt.core.model.strategy.StrategyWatermark;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 策略水印与运行环境绑定（P2 模块三 §3.4）。
 *
 * <p>上传时在源码末尾嵌入水印标记行 {@code # lhjypt-watermark-<token>}（对原作者透明、可复制携带），
 * 记录 策略/版本/作者/上传者/指纹 到 {@code strategy_watermark}；
 * 泄露出去的任何副本只要能扫到标记即可溯源到策略与责任人。
 * 运行前比对宿主指纹，防止拷到其他机器运行。
 */
@Service
@RequiredArgsConstructor
public class StrategyWatermarkService {

    public static final String MARKER_PREFIX = "# lhjypt-watermark-";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("# lhjypt-watermark-([0-9a-f]{64})");

    private final StrategyWatermarkMapper watermarkMapper;

    /** 由 策略 + 作者 + 上传时间 派生不可逆水印令牌。 */
    public String token(String strategyId, String owner, long uploadTimeMillis) {
        return sha256Hex(strategyId + "|" + owner + "|" + uploadTimeMillis);
    }

    /** 在源码末尾追加水印标记行。 */
    public String embed(String source, String token) {
        String base = source == null ? "" : source;
        if (!base.endsWith("\n")) {
            base += "\n";
        }
        return base + MARKER_PREFIX + token + "\n";
    }

    /** 从任意源码/复盘文本中提取水印令牌。 */
    public Optional<String> extract(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** 溯源：输入可能是带水印的源码或令牌本身。 */
    public Optional<StrategyWatermark> trace(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Optional<String> token = extract(content);
        String candidate = token.orElse(content.trim());
        StrategyWatermark watermark = watermarkMapper.selectByToken(candidate);
        return watermark == null ? Optional.empty() : Optional.of(watermark);
    }

    /** 运行环境指纹：hostname + 首张非回环网卡 MAC 的 SHA-256（跨重启稳定）。 */
    public String currentFingerprint() {
        return sha256Hex(hostname() + "|" + primaryMac());
    }

    @Transactional
    public StrategyWatermark save(String strategyId, int version, String token,
                                 String owner, String uploadedBy,
                                 String sourceHash, String envFingerprint) {
        StrategyWatermark existing = watermarkMapper.selectByToken(token);
        StrategyWatermark watermark = existing == null ? new StrategyWatermark() : existing;
        watermark.setStrategyId(strategyId);
        watermark.setVersion(version);
        watermark.setToken(token);
        watermark.setOwner(owner);
        watermark.setUploadedBy(uploadedBy);
        watermark.setSourceHash(sourceHash);
        watermark.setEnvFingerprint(envFingerprint);
        watermark.setCreateTime(LocalDateTime.now());
        if (watermark.getId() == null) {
            watermarkMapper.insert(watermark);
        } else {
            watermarkMapper.updateById(watermark);
        }
        return watermark;
    }

    public StrategyWatermark latest(String strategyId) {
        return watermarkMapper.selectLatestByStrategy(strategyId);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception error) {
            return "unknown-host";
        }
    }

    private static String primaryMac() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                byte[] hardware = networkInterface.getHardwareAddress();
                if (hardware != null && hardware.length > 0) {
                    StringBuilder builder = new StringBuilder();
                    for (byte octet : hardware) {
                        builder.append(String.format("%02X", octet & 0xFF));
                    }
                    return builder.toString();
                }
            }
        } catch (Exception ignored) {
            // 降级为 hostname 绑定
        }
        return "no-mac";
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("计算水印令牌失败", error);
        }
    }
}