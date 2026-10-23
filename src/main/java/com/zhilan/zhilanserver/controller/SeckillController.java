package com.zhilan.zhilanserver.controller;

import com.zhilan.zhilanserver.common.BusinessException;
import com.zhilan.zhilanserver.common.Result;
import com.zhilan.zhilanserver.entity.VoucherOrder;
import com.zhilan.zhilanserver.service.VoucherOrderService;
import com.zhilan.zhilanserver.service.SeckillVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final VoucherOrderService voucherOrderService;
    private final SeckillVoucherService seckillVoucherService;

    /**
     * 秒杀下单
     * 用户 id 从请求头取（真实项目应从登录态/JWT 取）
     */
    @PostMapping("/{voucherId}")
    public Result<Void> seckill(@PathVariable Long voucherId,
                                @RequestHeader(value = "X-User-Id",required = false) Long userId) {
        if (userId == null) {
            return Result.fail(401,"请先登录（请求头缺少X-User-Id）");
        }
        voucherOrderService.seckillVoucher(voucherId, userId);
        return Result.ok();
    }

    /**
     *  查询自己对某券的订单
     */
    @GetMapping("/order/{voucherId}")
    public Result<VoucherOrder> getOrder(@PathVariable Long voucherId,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return Result.fail(401, "请先登录（请求头缺少 X-User-Id）");
        }
        return Result.ok(voucherOrderService.getOrderByUser(voucherId,userId));
    }

    /**
     * 缓存预热（开发测试用；真实项目应由定时任务或运维脚本触发）
     */
    @PostMapping("/warmup/{voucherId}")
    public Result<Void> warmUp(@PathVariable Long voucherId) {
        seckillVoucherService.warmUpStock(voucherId);
        return Result.ok();
    }
}
