package com.lhjypt.core.service.fund;

import com.lhjypt.core.mapper.CashTransactionMapper;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.FundFlowMapper;
import com.lhjypt.core.model.FundAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundManagementServiceTest {

    @Test
    void commissionReducesTotalAssetAndAvailableCash() {
        CashTransactionMapper txMapper = mock(CashTransactionMapper.class);
        FundFlowMapper flowMapper = mock(FundFlowMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        FundManagementService service = new FundManagementService(
                txMapper, flowMapper, accountMapper);
        FundAccount account = account("1000000", "1000000", "0");
        when(accountMapper.selectByAccountId("DEFAULT-001")).thenReturn(account);

        service.chargeCommission("DEFAULT-001", new BigDecimal("2.1"), "ORD-001", "rb2410");

        assertEquals(0, account.getAvailableCash()
                .compareTo(new BigDecimal("999997.9")));
        assertEquals(0, account.getTotalAsset()
                .compareTo(new BigDecimal("999997.9")));
        verify(accountMapper).updateById(account);
        verify(flowMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void settlingMarginMovesFrozenToMarginAndChargesCommission() {
        CashTransactionMapper txMapper = mock(CashTransactionMapper.class);
        FundFlowMapper flowMapper = mock(FundFlowMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        FundManagementService service = new FundManagementService(
                txMapper, flowMapper, accountMapper);
        FundAccount account = account("999997.9", "999300", "702.1");
        when(accountMapper.selectByAccountId("DEFAULT-001")).thenReturn(account);

        service.settleMarginAndCommission(
                "DEFAULT-001", new BigDecimal("700"), new BigDecimal("2.1"),
                "ORD-001", "rb2410");

        assertEquals(0, account.getMargin().compareTo(new BigDecimal("700")));
        assertEquals(0, account.getFrozenCash().compareTo(new BigDecimal("2.1")));
        assertEquals(0, account.getAvailableCash()
                .compareTo(new BigDecimal("999297.9")));
        assertEquals(0, account.getTotalAsset()
                .compareTo(new BigDecimal("999995.8")));
    }

    private FundAccount account(String total, String available, String frozen) {
        FundAccount account = new FundAccount();
        account.setAccountId("DEFAULT-001");
        account.setTotalAsset(new BigDecimal(total));
        account.setAvailableCash(new BigDecimal(available));
        account.setFrozenCash(new BigDecimal(frozen));
        account.setMargin(BigDecimal.ZERO);
        account.setRealizedPnl(BigDecimal.ZERO);
        return account;
    }
}
