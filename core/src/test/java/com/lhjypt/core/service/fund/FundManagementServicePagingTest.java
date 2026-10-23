package com.lhjypt.core.service.fund;

import com.lhjypt.core.mapper.CashTransactionMapper;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.FundFlowMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.fund.CashTransaction;
import com.lhjypt.core.model.fund.FundFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundManagementServicePagingTest {

    private CashTransactionMapper txMapper;
    private FundFlowMapper flowMapper;
    private FundAccountMapper accountMapper;
    private FundManagementService service;

    @BeforeEach
    void setUp() {
        txMapper = mock(CashTransactionMapper.class);
        flowMapper = mock(FundFlowMapper.class);
        accountMapper = mock(FundAccountMapper.class);
        service = new FundManagementService(txMapper, flowMapper, accountMapper);
    }

    @Test
    @DisplayName("审核出金时按最新可用资金二次校验，不足则不执行")
    void approveRechecksWithdrawAvailability() {
        CashTransaction tx = transaction("WITHDRAW", "200");
        when(txMapper.selectOne(any())).thenReturn(tx);
        when(accountMapper.selectByAccountId("A1")).thenReturn(account("1000", "100"));

        assertThatThrownBy(() -> service.approve("T1", "admin", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("可用资金不足");

        assertThat(tx.getStatus()).isEqualTo("PENDING");
        verify(txMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("已处理的出入金单不可重复审核")
    void approveRejectsProcessedTransaction() {
        CashTransaction tx = transaction("DEPOSIT", "100");
        tx.setStatus("COMPLETED");
        when(txMapper.selectOne(any())).thenReturn(tx);

        assertThatThrownBy(() -> service.approve("T1", "admin", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    @DisplayName("账户分页裁剪参数并返回 total")
    void pageAccountsClampsPaging() {
        FundAccount account = account("1000", "900");
        when(accountMapper.selectPage(0L, 200)).thenReturn(List.of(account));
        when(accountMapper.countAll()).thenReturn(5L);

        Map<String, Object> page = service.pageAccounts(0, 500);

        assertThat(page.get("page")).isEqualTo(1);
        assertThat(page.get("size")).isEqualTo(200);
        assertThat(page.get("total")).isEqualTo(5L);
        assertThat(page.get("items")).isEqualTo(List.of(account));
    }

    @Test
    @DisplayName("流水分页按账户/时间/业务类型过滤")
    void pageFlowsFiltersAndPaginates() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        FundFlow flow = new FundFlow();
        flow.setFlowNo("FLOW-1");
        when(flowMapper.selectPageFiltered("A1", start, end, "DEPOSIT", 0L, 20))
                .thenReturn(List.of(flow));
        when(flowMapper.countFiltered("A1", start, end, "DEPOSIT")).thenReturn(1L);

        Map<String, Object> page = service.pageFlows(" A1 ", start, end, " DEPOSIT ", 1, 20);

        assertThat(page.get("total")).isEqualTo(1L);
        assertThat(page.get("items")).isEqualTo(List.of(flow));
        verify(flowMapper).selectPageFiltered("A1", start, end, "DEPOSIT", 0L, 20);
    }

    @Test
    @DisplayName("流水分页缺少账户直接拒绝")
    void pageFlowsRequiresAccount() {
        assertThatThrownBy(() -> service.pageFlows(" ", LocalDateTime.now(),
                LocalDateTime.now(), null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountId");
    }

    private static CashTransaction transaction(String type, String amount) {
        CashTransaction tx = new CashTransaction();
        tx.setTransactionNo("T1");
        tx.setAccountId("A1");
        tx.setType(type);
        tx.setAmount(new BigDecimal(amount));
        tx.setStatus("PENDING");
        return tx;
    }

    private static FundAccount account(String total, String available) {
        FundAccount account = new FundAccount();
        account.setAccountId("A1");
        account.setCurrency("CNY");
        account.setTotalAsset(new BigDecimal(total));
        account.setAvailableCash(new BigDecimal(available));
        account.setFrozenCash(BigDecimal.ZERO);
        account.setMargin(BigDecimal.ZERO);
        return account;
    }
}
