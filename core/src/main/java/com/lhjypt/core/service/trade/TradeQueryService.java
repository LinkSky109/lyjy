package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台账本查询。
 */
@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private final PositionMapper positionMapper;
    private final FundAccountMapper fundAccountMapper;
    private final TradeRecordMapper tradeRecordMapper;

    public List<Position> positions(String accountId, String strategyId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        return strategyId == null || strategyId.isBlank()
                ? positionMapper.selectByAccount(accountId)
                : positionMapper.selectByAccountAndStrategy(accountId, strategyId);
    }

    public FundAccount account(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }
        return account;
    }

    /**
     * 成交分页。accountId / symbol / 时间范围均可选，时间语义为 [from, to)。
     */
    public Map<String, Object> pageTrades(String accountId, String symbol,
                                          LocalDateTime from, LocalDateTime to,
                                          int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        long offset = (long) (safePage - 1) * safeSize;
        String account = blankToNull(accountId);
        String sym = blankToNull(symbol);
        List<TradeRecord> items = tradeRecordMapper.selectPageFiltered(
                account, sym, from, to, offset, safeSize);
        long total = tradeRecordMapper.countPageFiltered(account, sym, from, to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
