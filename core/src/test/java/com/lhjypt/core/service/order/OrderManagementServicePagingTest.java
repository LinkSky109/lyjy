package com.lhjypt.core.service.order;

import com.lhjypt.core.mapper.OrderMapper;
import com.lhjypt.core.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderManagementServicePagingTest {

    private OrderMapper orderMapper;
    private OrderManagementService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        service = new OrderManagementService(orderMapper, mock(OrderStateMachine.class),
                mock(OrderNumberGenerator.class), mock(OrderIdempotentService.class));
    }

    @Test
    @DisplayName("分页接口裁剪页码与页大小并返回 total")
    void pageOrdersClampsPaging() {
        Order order = new Order();
        order.setOrderId("LHJ-1");
        when(orderMapper.selectPageFiltered(isNull(), isNull(), eq(0L), eq(200)))
                .thenReturn(List.of(order));
        when(orderMapper.countFiltered(isNull(), isNull())).thenReturn(42L);

        Map<String, Object> page = service.pageOrders(null, null, 0, 500);

        assertThat(page.get("page")).isEqualTo(1);
        assertThat(page.get("size")).isEqualTo(200);
        assertThat(page.get("total")).isEqualTo(42L);
        assertThat(page.get("items")).isEqualTo(List.of(order));
    }

    @Test
    @DisplayName("空白过滤条件按 null 处理")
    void pageOrdersTrimsBlankFilters() {
        when(orderMapper.selectPageFiltered(isNull(), isNull(), eq(0L), eq(20)))
                .thenReturn(List.of());
        when(orderMapper.countFiltered(isNull(), isNull())).thenReturn(0L);

        service.pageOrders("  ", "  ", 1, 20);

        org.mockito.Mockito.verify(orderMapper)
                .selectPageFiltered(isNull(), isNull(), eq(0L), eq(20));
    }

    @Test
    @DisplayName("订单可撤与柜台确认状态由当前字段推导")
    void orderExposesCancelableFlags() {
        Order submitted = new Order();
        submitted.setStatus("SUBMITTED");
        assertThat(submitted.getCancelable()).isFalse();
        assertThat(submitted.getGatewayAckStatus()).isEqualTo("NONE");

        submitted.setExchangeOrderId("EX000001");
        assertThat(submitted.getCancelable()).isTrue();
        assertThat(submitted.getGatewayAckStatus()).isEqualTo("ACKED");

        Order filled = new Order();
        filled.setStatus("FILLED");
        filled.setExchangeOrderId("EX000002");
        assertThat(filled.getCancelable()).isFalse();

        Order rejected = new Order();
        rejected.setStatus("REJECTED");
        assertThat(rejected.getGatewayAckStatus()).isEqualTo("REJECTED");
    }
}
