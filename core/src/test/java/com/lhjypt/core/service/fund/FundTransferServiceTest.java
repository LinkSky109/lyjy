package com.lhjypt.core.service.fund;

import com.lhjypt.core.mapper.CashTransactionMapper;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.FundFlowMapper;
import com.lhjypt.core.mapper.FundTransferMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.fund.FundTransfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundTransferServiceTest {

    private FundTransferMapper transferMapper;
    private FundAccountMapper accountMapper;
    private FundFlowMapper flowMapper;
    private FundManagementService service;

    @BeforeEach
    void setUp() {
        transferMapper = mock(FundTransferMapper.class);
        accountMapper = mock(FundAccountMapper.class);
        flowMapper = mock(FundFlowMapper.class);
        service = new FundManagementService(
                mock(CashTransactionMapper.class), flowMapper, accountMapper, transferMapper);
    }

    @Test
    @DisplayName("划转在同一事务内更新双方余额并写双边流水")
    void transfersBetweenAccounts() {
        FundAccount from = account("A", "1000", "1000");
        FundAccount to = account("B", "200", "200");
        when(accountMapper.selectByAccountId("A")).thenReturn(from);
        when(accountMapper.selectByAccountId("B")).thenReturn(to);

        FundTransfer result = service.transfer(request("A", "B", "300", "key-1"));

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTransferNo()).startsWith("TRF");
        assertThat(from.getAvailableCash()).isEqualByComparingTo("700");
        assertThat(from.getTotalAsset()).isEqualByComparingTo("700");
        assertThat(to.getAvailableCash()).isEqualByComparingTo("500");
        assertThat(to.getTotalAsset()).isEqualByComparingTo("500");
        verify(accountMapper).updateById(from);
        verify(accountMapper).updateById(to);
        verify(flowMapper, org.mockito.Mockito.times(2)).insert(any());
        verify(transferMapper).insert(result);
    }

    @Test
    @DisplayName("可用资金不足时拒绝划转且不落流水")
    void rejectsInsufficientBalance() {
        when(accountMapper.selectByAccountId("A")).thenReturn(account("A", "1000", "50"));
        when(accountMapper.selectByAccountId("B")).thenReturn(account("B", "200", "200"));

        assertThatThrownBy(() -> service.transfer(request("A", "B", "300", "key-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("可用资金不足");
        verify(flowMapper, never()).insert(any());
        verify(transferMapper, never()).insert(any());
    }

    @Test
    @DisplayName("幂等键重复时返回首次结果")
    void returnsExistingTransferForIdempotencyKey() {
        FundTransfer existing = new FundTransfer();
        existing.setTransferNo("TRF-EXIST");
        when(transferMapper.selectByIdempotencyKey("key-3")).thenReturn(existing);

        FundTransfer result = service.transfer(request("A", "B", "300", "key-3"));

        assertThat(result).isSameAs(existing);
        verify(accountMapper, never()).selectByAccountId(any());
    }

    @Test
    @DisplayName("转出与转入账户相同或币种不一致时拒绝")
    void validatesAccountsAndCurrency() {
        assertThatThrownBy(() -> service.transfer(request("A", "A", "10", "k1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能相同");

        FundAccount from = account("A", "1000", "1000");
        from.setCurrency("CNY");
        FundAccount to = account("B", "200", "200");
        to.setCurrency("USD");
        when(accountMapper.selectByAccountId("A")).thenReturn(from);
        when(accountMapper.selectByAccountId("B")).thenReturn(to);
        FundTransfer request = request("A", "B", "10", "k2");
        request.setCurrency("CNY");

        assertThatThrownBy(() -> service.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("币种");
    }

    private static FundTransfer request(String from, String to, String amount, String key) {
        FundTransfer request = new FundTransfer();
        request.setFromAccountId(from);
        request.setToAccountId(to);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("CNY");
        request.setIdempotencyKey(key);
        request.setOperator("tester");
        return request;
    }

    private static FundAccount account(String id, String total, String available) {
        FundAccount account = new FundAccount();
        account.setAccountId(id);
        account.setCurrency("CNY");
        account.setTotalAsset(new BigDecimal(total));
        account.setAvailableCash(new BigDecimal(available));
        account.setFrozenCash(BigDecimal.ZERO);
        account.setMargin(BigDecimal.ZERO);
        account.setUnrealizedPnl(BigDecimal.ZERO);
        account.setRealizedPnl(BigDecimal.ZERO);
        return account;
    }
}
