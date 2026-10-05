package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台账本查询。
 */
@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private final PositionMapper positionMapper;
    private final FundAccountMapper fundAccountMapper;

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
}
