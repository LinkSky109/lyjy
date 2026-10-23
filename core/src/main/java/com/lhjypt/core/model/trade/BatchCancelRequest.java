package com.lhjypt.core.model.trade;

import java.util.List;

/**
 * 批量撤单请求。单次最多 50 笔，避免长事务与柜台限流。
 */
public record BatchCancelRequest(List<String> orderIds, String reason) {
}
