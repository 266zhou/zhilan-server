package com.zhilan.zhilanserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhilan.zhilanserver.dto.SeckillOrderMessage;
import com.zhilan.zhilanserver.entity.VoucherOrder;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 支付订单
     * @return true = 支付成功；false = 乐观锁冲突（订单已被修改）
     */
    boolean payOrder(Long orderId, Long userId);

    /**
     *关闭订单并回补库存（定时任务调用）
     * @return true = 成功关闭；false = 状态已变更，放弃
     */
    boolean closeOrder(Long orderId);


    /**
     * 查询超时未支付的订单
     * @param deadline 创建时间早于此时间的视为超时
     * @param limit 最多查多少条
     */
    List<VoucherOrder> listOverdueOrders(LocalDateTime deadline, int limit);
}
