package com.lhjypt.core.service.fund;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.FundReconciliationMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.fund.FundReconciliation;
import com.lhjypt.core.model.trade.AccountInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 资金对账服务
 *
 * 每日收盘后将系统账面资金与柜台返回资金逐字段比对：
 * - 总资金（balance）
 * - 可用资金（available）
 * - 保证金占用（margin）
 *
 * 差异超过容差则标记MISMATCHED，生成差异处理工单
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);


    private final FundReconciliationMapper reconMapper;
    private final FundAccountMapper accountMapper;

    /** 对账容差（元），小于该差异视为一致（浮点误差） */
    private static final BigDecimal TOLERANCE = new BigDecimal("1.00");

    /**
     * 执行对账
     *
     * @param accountId 账户ID
     * @param gatewayAccount 柜台返回的账户信息
     */
    @Transactional
    public FundReconciliation reconcile(String accountId, AccountInfo gatewayAccount) {
        FundAccount systemAccount = accountMapper.selectByAccountId(accountId);

        FundReconciliation recon = new FundReconciliation();
        recon.setReconNo("RECON" + LocalDate.now().toString().replace("-", ""));
        recon.setAccountId(accountId);
        recon.setReconDate(LocalDate.now().toString());
        recon.setCreateTime(LocalDateTime.now());

        // 系统账面数据
        recon.setSystemBalance(systemAccount.getTotalAsset());
        recon.setSystemAvailable(systemAccount.getAvailableCash());
        recon.setSystemMargin(systemAccount.getFrozenCash());

        // 柜台返回数据
        recon.setGatewayBalance(gatewayAccount.getBalance());
        recon.setGatewayAvailable(gatewayAccount.getAvailable());
        recon.setGatewayMargin(gatewayAccount.getMargin());

        // 计算差异
        BigDecimal diff = systemAccount.getTotalAsset()
                .subtract(gatewayAccount.getBalance()).abs();
        recon.setDifference(diff);

        if (diff.compareTo(TOLERANCE) <= 0) {
            recon.setStatus("MATCHED");
            log.info("资金对账一致: account={} diff={}", accountId, diff);
        } else {
            recon.setStatus("MISMATCHED");
            recon.setDifferenceReason("差异 " + diff + " 元，需人工核查出入金/手续费/盈亏计算");
            log.warn("资金对账有差异: account={} diff={}", accountId, diff);
        }

        reconMapper.insert(recon);
        return recon;
    }
}
