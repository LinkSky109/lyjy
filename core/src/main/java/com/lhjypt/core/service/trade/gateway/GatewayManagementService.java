package com.lhjypt.core.service.trade.gateway;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.GatewayInfoMapper;
import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayRouteRule;
import com.lhjypt.core.model.gateway.GatewayStatus;
import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.service.trade.TradingGateway;
import com.lhjypt.core.service.trade.impl.CTPGatewayAdapter;
import com.lhjypt.core.service.trade.impl.MockCTPGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 网关注册、连接、路由规则管理。
 */
@Service
@RequiredArgsConstructor
public class GatewayManagementService {

    private final GatewayInfoMapper gatewayInfoMapper;
    private final GatewayRouter router;
    private final CTPGatewayAdapter ctpGatewayAdapter;

    @EventListener(ApplicationReadyEvent.class)
    public void loadPersistedGateways() {
        for (GatewayInfo info : gatewayInfoMapper.selectAll()) {
            if (router.registry().get(info.getGatewayId()) == null) {
                registerInternal(info);
            }
        }
    }

    @Transactional
    public GatewayInfo register(GatewayInfo info) {
        validate(info);
        GatewayInfo existing = gatewayInfoMapper.selectByGatewayId(info.getGatewayId());
        info.setUpdateTime(LocalDateTime.now());
        if (info.getParamConfig() == null) {
            info.setParamConfig("{}");
        }
        if (info.getPriority() == null) {
            info.setPriority(100);
        }
        if (existing == null) {
            info.setStatus(GatewayStatus.DOWN.name());
            info.setCreateTime(LocalDateTime.now());
            gatewayInfoMapper.insert(info);
        } else {
            info.setId(existing.getId());
            info.setCreateTime(existing.getCreateTime());
            gatewayInfoMapper.updateById(info);
        }
        registerInternal(info);
        return info;
    }

    public void connect(String gatewayId, CTPConfig config) {
        GatewayInfo info = requireInfo(gatewayId);
        CTPConfig effective = config != null ? config : parseConfig(info.getParamConfig());
        if (effective.getMode() == null || effective.getMode().isBlank()) {
            effective.setMode("CTP".equalsIgnoreCase(info.getGatewayType()) ? "LIVE" : "MOCK");
        }
        router.registry().connect(gatewayId, effective);
    }

    public void disconnect(String gatewayId) {
        router.registry().disconnect(gatewayId);
    }

    public List<Map<String, Object>> listGateways() {
        return router.listGateways();
    }

    public Map<String, Object> health(String gatewayId) {
        return router.health(gatewayId);
    }

    public GatewayRouteRule upsertRoute(GatewayRouteRule rule) {
        if (rule.getGatewayId() == null || rule.getGatewayId().isBlank()) {
            throw new IllegalArgumentException("gatewayId 不能为空");
        }
        requireInfo(rule.getGatewayId());
        if (rule.getFallbackId() != null && !rule.getFallbackId().isBlank()) {
            requireInfo(rule.getFallbackId());
        }
        return router.routeService().upsert(rule);
    }

    public List<GatewayRouteRule> listRoutes() {
        return router.routeService().list();
    }

    public GatewayRouteRule switchAccount(String accountId, String gatewayId,
                                          String fallbackId, String operator) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        return router.switchAccount(accountId, gatewayId, fallbackId, operator);
    }

    private GatewayInfo requireInfo(String gatewayId) {
        GatewayInfo info = gatewayInfoMapper.selectByGatewayId(gatewayId);
        if (info == null) {
            ManagedGateway managed = router.registry().get(gatewayId);
            if (managed != null) {
                return managed.info();
            }
            throw new IllegalArgumentException("网关不存在: " + gatewayId);
        }
        return info;
    }

    private void registerInternal(GatewayInfo info) {
        TradingGateway gateway;
        if ("MOCK".equalsIgnoreCase(info.getGatewayType())) {
            gateway = new MockCTPGateway();
        } else if ("CTP".equalsIgnoreCase(info.getGatewayType())) {
            gateway = ctpGatewayAdapter;
        } else {
            throw new IllegalArgumentException("暂不支持的网关类型: " + info.getGatewayType());
        }
        router.register(info, gateway);
    }

    private static void validate(GatewayInfo info) {
        if (info == null || info.getGatewayId() == null || info.getGatewayId().isBlank()) {
            throw new IllegalArgumentException("gatewayId 不能为空");
        }
        if (info.getGatewayType() == null || info.getGatewayType().isBlank()) {
            throw new IllegalArgumentException("gatewayType 不能为空");
        }
    }

    private static CTPConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return new CTPConfig();
        }
        try {
            CTPConfig config = JSON.parseObject(json, CTPConfig.class);
            return config == null ? new CTPConfig() : config;
        } catch (Exception error) {
            throw new IllegalArgumentException("网关参数配置格式错误: " + error.getMessage());
        }
    }
}
