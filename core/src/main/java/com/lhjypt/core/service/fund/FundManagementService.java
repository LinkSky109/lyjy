package com.lhjypt.core.service.fund;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.mapper.*;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.fund.CashTransaction;
import com.lhjypt.core.model.fund.FundFlow;
import com.lhjypt.core.model.fund.FundTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 资金管理服务
 *
 * 核心能力：
 * 1. 出入金申请、审核、执行
 * 2. 资金冻结/解冻（保证金）
 * 3. 资金变动流水记录
 * 4. 透支控制（禁止可用资金为负）
 * 5. 账户余额管理
 */
@Service
@RequiredArgsConstructor
public class FundManagementService {
    private static final Logger log = LoggerFactory.getLogger(FundManagementService.class);


    private final CashTransactionMapper cashTransactionMapper;
    private final FundFlowMapper fundFlowMapper;
    private final FundAccountMapper fundAccountMapper;
    private final FundTransferMapper fundTransferMapper;

    private final AtomicInteger flowCounter = new AtomicInteger(0);
    private final AtomicInteger txCounter = new AtomicInteger(0);

    /**
     * 提交出入金申请
     */
    @Transactional
    public CashTransaction applyCashTransaction(CashTransaction request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        if (request.getAccountId() == null || request.getAccountId().isBlank()) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        FundAccount account = requireAccount(request.getAccountId());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()
                && account.getCurrency() != null
                && !request.getCurrency().equalsIgnoreCase(account.getCurrency())) {
            throw new IllegalArgumentException("币种与账户不一致: " + request.getCurrency()
                    + " / " + account.getCurrency());
        }
        if ("WITHDRAW".equals(request.getType())
                && account.getAvailableCash().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("可用资金不足，无法出金 " + request.getAmount());
        }

        request.setTransactionNo(generateNo("TXN"));
        request.setStatus("PENDING");
        request.setCreateTime(LocalDateTime.now());
        cashTransactionMapper.insert(request);
        log.info("出入金申请: no={} type={} amount={}",
                request.getTransactionNo(), request.getType(), request.getAmount());
        return request;
    }

    /**
     * 账户间资金划转。双边余额与流水在同一事务内完成，幂等键重复请求返回首次结果。
     */
    @Transactional
    public FundTransfer transfer(FundTransfer request) {
        if (request == null) {
            throw new IllegalArgumentException("划转请求不能为空");
        }
        String fromId = trim(request.getFromAccountId());
        String toId = trim(request.getToAccountId());
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException("转出/转入账户不能为空");
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("转出与转入账户不能相同");
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("划转金额必须大于 0");
        }
        String idempotencyKey = trim(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            FundTransfer existing = fundTransferMapper.selectByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                return existing;
            }
        }

        FundAccount from = requireAccount(fromId);
        FundAccount to = requireAccount(toId);
        String currency = trim(request.getCurrency());
        if (currency == null) {
            currency = from.getCurrency();
        }
        if (!sameCurrency(from.getCurrency(), currency)) {
            throw new IllegalArgumentException("转出账户币种与划转币种不一致");
        }
        if (!sameCurrency(to.getCurrency(), currency)) {
            throw new IllegalArgumentException("转入账户币种与划转币种不一致");
        }
        if (from.getAvailableCash().compareTo(amount) < 0) {
            throw new IllegalStateException("可用资金不足，当前 " + from.getAvailableCash());
        }

        BigDecimal fromBefore = from.getAvailableCash();
        BigDecimal toBefore = to.getAvailableCash();
        from.setAvailableCash(fromBefore.subtract(amount));
        from.setTotalAsset(from.getTotalAsset().subtract(amount));
        to.setAvailableCash(toBefore.add(amount));
        to.setTotalAsset(to.getTotalAsset().add(amount));
        fundAccountMapper.updateById(from);
        fundAccountMapper.updateById(to);

        FundTransfer transfer = new FundTransfer();
        transfer.setTransferNo(generateNo("TRF"));
        transfer.setFromAccountId(fromId);
        transfer.setToAccountId(toId);
        transfer.setAmount(amount);
        transfer.setCurrency(currency);
        transfer.setStatus("COMPLETED");
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setOperator(trim(request.getOperator()));
        transfer.setRemark(trim(request.getRemark()));
        transfer.setFromBalanceBefore(fromBefore);
        transfer.setFromBalanceAfter(from.getAvailableCash());
        transfer.setToBalanceBefore(toBefore);
        transfer.setToBalanceAfter(to.getAvailableCash());
        transfer.setCreateTime(LocalDateTime.now());
        transfer.setCompleteTime(LocalDateTime.now());
        fundTransferMapper.insert(transfer);

        recordFlow(fromId, "TRANSFER", transfer.getTransferNo(), amount.negate(),
                fromBefore, from.getAvailableCash(), null);
        recordFlow(toId, "TRANSFER", transfer.getTransferNo(), amount,
                toBefore, to.getAvailableCash(), null);
        log.info("账户划转: no={} from={} to={} amount={} operator={}",
                transfer.getTransferNo(), fromId, toId, amount, transfer.getOperator());
        return transfer;
    }

    public FundTransfer getTransfer(String transferNo) {
        FundTransfer transfer = fundTransferMapper.selectByTransferNo(transferNo);
        if (transfer == null) {
            throw new IllegalArgumentException("划转单不存在: " + transferNo);
        }
        return transfer;
    }

    /**
     * 审批列表分页（出入金单）。
     */
    public java.util.Map<String, Object> pageTransactions(String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        long offset = (long) (safePage - 1) * safeSize;
        String normalized = trim(status);
        List<CashTransaction> items = cashTransactionMapper.selectPageFiltered(
                normalized, offset, safeSize);
        long total = cashTransactionMapper.countFiltered(normalized);
        return pageResult(items, total, safePage, safeSize);
    }

    private static boolean sameCurrency(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return true;
        }
        return left.equalsIgnoreCase(right);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 审核出入金
     */
    @Transactional
    public CashTransaction approve(String transactionNo, String approver, boolean approved) {
        CashTransaction tx = findTransaction(transactionNo);
        if (!"PENDING".equals(tx.getStatus())) {
            throw new IllegalStateException("出入金单已处理: " + transactionNo
                    + " status=" + tx.getStatus());
        }
        if (approved) {
            executeCashTransaction(tx);
            tx.setStatus("COMPLETED");
            tx.setCompleteTime(LocalDateTime.now());
        } else {
            tx.setStatus("REJECTED");
        }
        tx.setApprover(approver);
        tx.setApproveTime(LocalDateTime.now());
        cashTransactionMapper.updateById(tx);
        return tx;
    }

    /**
     * 执行出入金，更新账户余额并记录流水
     */
    private void executeCashTransaction(CashTransaction tx) {
        FundAccount account = requireAccount(tx.getAccountId());
        if ("WITHDRAW".equals(tx.getType())
                && account.getAvailableCash().compareTo(tx.getAmount()) < 0) {
            throw new IllegalStateException("可用资金不足，无法执行出金 " + tx.getAmount());
        }
        BigDecimal before = account.getTotalAsset();
        BigDecimal change = "DEPOSIT".equals(tx.getType())
                ? tx.getAmount() : tx.getAmount().negate();

        BigDecimal after = before.add(change);
        account.setTotalAsset(after);
        account.setAvailableCash(account.getAvailableCash().add(change));
        fundAccountMapper.updateById(account);

        recordFlow(tx.getAccountId(), tx.getType(), tx.getTransactionNo(),
                change, before, after, null);
    }

    /**
     * 冻结资金（开仓保证金）
     */
    @Transactional
    public void freezeCash(String accountId, BigDecimal amount, String bizNo, String symbol) {
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("冻结金额必须大于0");
        }

        // 透支控制
        if (account.getAvailableCash().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "可用资金不足冻结，需 " + amount + " 实际 " + account.getAvailableCash());
        }

        BigDecimal before = account.getAvailableCash();
        account.setAvailableCash(before.subtract(amount));
        account.setFrozenCash(account.getFrozenCash().add(amount));
        fundAccountMapper.updateById(account);

        recordFlow(accountId, "MARGIN_FREEZE", bizNo, amount.negate(),
                before, account.getAvailableCash(), symbol);
    }

    /**
     * 解冻资金（平仓释放保证金）
     */
    @Transactional
    public void releaseCash(String accountId, BigDecimal amount, String bizNo, String symbol) {
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("释放金额必须大于0");
        }
        if (account.getFrozenCash().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "释放金额超过冻结资金，需 " + amount + " 实际 " + account.getFrozenCash());
        }
        BigDecimal before = account.getAvailableCash();

        account.setFrozenCash(account.getFrozenCash().subtract(amount));
        account.setAvailableCash(before.add(amount));
        fundAccountMapper.updateById(account);

        recordFlow(accountId, "MARGIN_RELEASE", bizNo, amount,
                before, account.getAvailableCash(), symbol);
    }

    /**
     * 记录手续费
     */
    @Transactional
    public void chargeCommission(String accountId, BigDecimal commission,
                                 String bizNo, String symbol) {
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }
        if (commission == null || commission.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal before = account.getAvailableCash();
        account.setAvailableCash(before.subtract(commission));
        account.setTotalAsset(account.getTotalAsset().subtract(commission));
        fundAccountMapper.updateById(account);

        recordFlow(accountId, "COMMISSION", bizNo, commission.negate(),
                before, account.getAvailableCash(), symbol);
    }

    /**
     * 成交后把冻结保证金转为已占用保证金，并扣收手续费。
     */
    @Transactional
    public void settleMarginAndCommission(String accountId, BigDecimal margin,
                                          BigDecimal commission, String bizNo, String symbol) {
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }

        BigDecimal settleMargin = margin == null ? BigDecimal.ZERO : margin;
        BigDecimal settleCommission = commission == null ? BigDecimal.ZERO : commission;
        if (settleMargin.compareTo(BigDecimal.ZERO) < 0
                || settleCommission.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("保证金和手续费不能为负数");
        }
        if (account.getFrozenCash().compareTo(settleMargin) < 0) {
            throw new IllegalStateException(
                    "冻结资金不足以结算保证金，需 " + settleMargin
                            + " 实际 " + account.getFrozenCash());
        }
        if (account.getAvailableCash().compareTo(settleCommission) < 0) {
            throw new IllegalStateException(
                    "可用资金不足以支付手续费，需 " + settleCommission
                            + " 实际 " + account.getAvailableCash());
        }

        BigDecimal frozenBefore = account.getFrozenCash();
        BigDecimal availableBefore = account.getAvailableCash();

        account.setFrozenCash(frozenBefore.subtract(settleMargin));
        account.setMargin(account.getMargin().add(settleMargin));
        account.setAvailableCash(availableBefore.subtract(settleCommission));
        account.setTotalAsset(account.getTotalAsset().subtract(settleCommission));
        fundAccountMapper.updateById(account);

        if (settleMargin.compareTo(BigDecimal.ZERO) > 0) {
            recordFlow(accountId, "MARGIN_SETTLE", bizNo, settleMargin.negate(),
                    frozenBefore, account.getFrozenCash(), symbol);
        }
        if (settleCommission.compareTo(BigDecimal.ZERO) > 0) {
            recordFlow(accountId, "COMMISSION", bizNo, settleCommission.negate(),
                    availableBefore, account.getAvailableCash(), symbol);
        }
    }

    /**
     * 记录平仓已实现盈亏并更新账户资产。
     */
    @Transactional
    public void applyRealizedPnl(String accountId, BigDecimal realizedPnl,
                                 String bizNo, String symbol) {
        if (realizedPnl == null || realizedPnl.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }

        BigDecimal before = account.getAvailableCash();
        account.setAvailableCash(before.add(realizedPnl));
        account.setTotalAsset(account.getTotalAsset().add(realizedPnl));
        account.setRealizedPnl(account.getRealizedPnl().add(realizedPnl));
        fundAccountMapper.updateById(account);

        recordFlow(accountId, "REALIZED_PNL", bizNo, realizedPnl,
                before, account.getAvailableCash(), symbol);
    }

    /**
     * 记录资金流水
     */
    private void recordFlow(String accountId, String bizType, String bizNo,
                            BigDecimal change, BigDecimal before, BigDecimal after, String symbol) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo(generateNo("FLOW"));
        flow.setAccountId(accountId);
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setChangeAmount(change);
        flow.setBalanceBefore(before);
        flow.setBalanceAfter(after);
        flow.setSymbol(symbol);
        flow.setCreateTime(LocalDateTime.now());
        fundFlowMapper.insert(flow);
    }

    /**
     * 查询资金流水
     */
    public List<FundFlow> getFlows(String accountId, LocalDateTime start, LocalDateTime end) {
        return fundFlowMapper.selectByAccountAndTime(accountId, start, end);
    }

    /**
     * 资金账户分页。
     */
    public java.util.Map<String, Object> pageAccounts(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        long offset = (long) (safePage - 1) * safeSize;
        List<FundAccount> items = fundAccountMapper.selectPage(offset, safeSize);
        long total = fundAccountMapper.countAll();
        return pageResult(items, total, safePage, safeSize);
    }

    /**
     * 资金流水分页（accountId 必填，bizType 可选）。
     */
    public java.util.Map<String, Object> pageFlows(String accountId, LocalDateTime start,
                                                   LocalDateTime end, String bizType,
                                                   int page, int size) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        long offset = (long) (safePage - 1) * safeSize;
        String biz = bizType == null || bizType.isBlank() ? null : bizType.trim();
        List<FundFlow> items = fundFlowMapper.selectPageFiltered(
                accountId.trim(), start, end, biz, offset, safeSize);
        long total = fundFlowMapper.countFiltered(accountId.trim(), start, end, biz);
        return pageResult(items, total, safePage, safeSize);
    }

    private static java.util.Map<String, Object> pageResult(List<?> items, long total,
                                                            int page, int size) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private FundAccount requireAccount(String accountId) {
        FundAccount account = fundAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new IllegalArgumentException("资金账户不存在: " + accountId);
        }
        return account;
    }

    private CashTransaction findTransaction(String no) {
        QueryWrapper<CashTransaction> wrapper = new QueryWrapper<>();
        wrapper.eq("transaction_no", no);
        CashTransaction tx = cashTransactionMapper.selectOne(wrapper);
        if (tx == null) throw new IllegalArgumentException("出入金单不存在: " + no);
        return tx;
    }

    private String generateNo(String prefix) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + time + String.format("%04d",
                (prefix.equals("FLOW") ? flowCounter : txCounter).incrementAndGet());
    }
}
