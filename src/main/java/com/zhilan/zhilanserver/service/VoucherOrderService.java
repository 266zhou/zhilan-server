package com.zhilan.zhilanserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhilan.zhilanserver.dto.SeckillOrderMessage;
import com.zhilan.zhilanserver.entity.VoucherOrder;

public interface VoucherOrderService extends IService<VoucherOrder> {
    /**
     * 秒杀下单
     *
     * voucherId 优惠券 id
     * userId    用户 id （从登录态取）
     */
    void seckillVoucher(Long voucherId, Long userId);

    /**
     * 查询某用户对某券的订单 （没有则返回 null）
     */
    VoucherOrder getOrderByUser(Long voucherId, Long userId);

    /**
     * 从 MQ 消息创建订单（消费者调用）
     */
    void createOrderFromMq(SeckillOrderMessage message);
}
