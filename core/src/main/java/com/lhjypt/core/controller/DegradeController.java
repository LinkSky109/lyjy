package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.model.degrade.DegradeSwitch;
import com.lhjypt.core.service.degrade.DegradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 性能降级预案 API（P1，TASK-P1-004）
 * 开关变更属敏感操作，需一次性确认令牌（PRD §3.5）。
 */
@RestController
@RequestMapping("/api/degrade")
@RequiredArgsConstructor
public class DegradeController {

    private final DegradeService degradeService;
    private final ConfirmTokenService confirmTokenService;

    /** 当前开关列表 */
    @GetMapping("/switch/list")
    public Result<List<DegradeSwitch>> list() {
        return Result.success(degradeService.listSwitches());
    }

    /** 设置开关（需确认令牌） */
    @PostMapping("/switch")
    public Result<DegradeSwitch> setSwitch(@RequestParam String key,
                                           @RequestParam String level,
                                           @RequestParam String operator,
                                           @RequestParam(required = false) String reason,
                                           @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        try {
            return Result.success(degradeService.setSwitch(key, level, operator,
                    reason == null ? "" : reason));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 当前降级状态与熔断器 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(degradeService.capacityWaterLevel());
    }

    /** 手动切换行情源降级（等价 setSwitch degrade.market.backup，便捷入口，需令牌） */
    @PostMapping("/backup-market")
    public Result<DegradeSwitch> backupMarket(@RequestParam(defaultValue = "ON") String level,
                                              @RequestParam String operator,
                                              @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        return Result.success(degradeService.setSwitch("degrade.market.backup", level,
                operator, "手动切换行情源降级"));
    }

    /** 容量水位（TPS/队列/开关/熔断器） */
    @GetMapping("/capacity")
    public Result<Map<String, Object>> capacity() {
        return Result.success(degradeService.capacityWaterLevel());
    }
}
