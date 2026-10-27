package com.zhilan.zhilanserver.controller;

import com.zhilan.zhilanserver.common.Result;
import com.zhilan.zhilanserver.entity.VoucherOrder;
import com.zhilan.zhilanserver.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final VoucherOrderService voucherOrderService;

    /**
     * 支付订单 （模拟支付。直接置为已支付）
     */
    @PostMapping("/pay/{orderId}")
    public Result<Void> pay(@PathVariable Long orderId,
                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return Result.fail(401, "请先登录（请求头缺少X-User-Id ）");
        }
        voucherOrderService.payOrder(orderId, userId);
        return Result.ok();
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    public Result<VoucherOrder> getOrder(@PathVariable Long orderId) {
        return Result.ok(voucherOrderService.getById(orderId));
    }

}
