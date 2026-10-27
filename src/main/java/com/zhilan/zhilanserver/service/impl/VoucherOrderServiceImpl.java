package com.zhilan.zhilanserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhilan.zhilanserver.common.BusinessException;
import com.zhilan.zhilanserver.constant.RedisKeyConstants;
import com.zhilan.zhilanserver.dto.SeckillOrderMessage;
import com.zhilan.zhilanserver.entity.SeckillVoucher;
import com.zhilan.zhilanserver.entity.VoucherOrder;
import com.zhilan.zhilanserver.mapper.VoucherOrderMapper;
import com.zhilan.zhilanserver.service.VoucherOrderService;
import com.zhilan.zhilanserver.service.SeckillVoucherService;
import com.zhilan.zhilanserver.mq.SeckillOrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl
        extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements VoucherOrderService {

    private final SeckillOrderProducer seckillOrderProducer;
    private final SeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillScript;

    @Override
    public void seckillVoucher(Long voucherId, Long userId) {
        // 执行 Lua：原子判断库存 + 一人一单 + 扣减
        Long result = stringRedisTemplate.execute(
                seckillScript,
                List.of(RedisKeyConstants.SECKILL_STOCK_KEY + voucherId,
                        RedisKeyConstants.SECKILL_ORDER_KEY + voucherId),
                String.valueOf(userId));

        if (result == null) {
            throw new BusinessException(500,"秒杀脚本执行异常");
        }
        switch (result.intValue()) {
            case 1 -> throw new BusinessException(400,"您已经买过了，一人限购一张");
            case 2 -> throw new BusinessException(400,"手慢了，库存已售罄");
            case 3 -> throw new BusinessException(400,"活动未开始或已结束");
            default -> {/* 0 = 成功，继续 */}
        }

        // Part A 是同步建单，这里改成发 MQ
        seckillOrderProducer.sendSeckillOrder(
                new SeckillOrderMessage(voucherId,userId));
    }

// TODO : Part B 已改为 MQ 异步，此方法暂时保留用于对比
//@Transactional(rollbackFor = Exception.class)
/*    public void createOrder(Long voucherId, Long userId) {
        // -1 扣减数据库库存 （乐观锁兜底： stock > 0)
        boolean deductSuccess = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getId,voucherId)
                        .gt(SeckillVoucher::getStock,0));
        if (!deductSuccess) {
            throw new BusinessException(400,"库存不足（数据库拦截兜底）");
        }

        // -2 创建订单（唯一索引兜底：重复会抛 DuplicateKeyException）
        VoucherOrder order = new VoucherOrder();
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(VoucherOrder.STATUS_UNPAID);
        save(order);
    }
     // 数据库操作 （独立事务）
        try {
            createOrder(voucherId, userId);
        } catch (Exception e) {
            //数据库失败了，必须把 Redis 的预扣回滚，否则库存会"凭空消失"
            rollbackRedis(voucherId,userId);
            throw e;
        }
*/
/*
      // 把 Redis 里预扣的库存和用户标记回滚
    private void rollbackRedis(Long voucherId, Long userId) {
        try {
            stringRedisTemplate.opsForValue()
                    .increment(RedisKeyConstants.SECKILL_STOCK_KEY + voucherId);
            stringRedisTemplate.opsForSet()
                    .remove(RedisKeyConstants.SECKILL_ORDER_KEY + voucherId, String.valueOf(userId));
            log.warn("[秒杀] 数据库失败，已回滚 Redis 库存。voucherId={}, userId={}", voucherId, userId);
        } catch (Exception ex) {
            log.error("[秒杀] Redis 回滚也失败了，需要人工介入！voucherId={}, userId={}", voucherId, userId,ex);
        }
    }
*/

    @Override
    public VoucherOrder getOrderByUser(Long voucherId, Long userId) {
        return getOne(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getActiveFlag, VoucherOrder.ACTIVE_FLAG_VALID)
                .orderByDesc(VoucherOrder::getId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrderFromMq(SeckillOrderMessage message) {
        Long voucherId = message.getVoucherId();
        Long userId = message.getUserId();

        // 幂等检查 只查有效订单（⭐ 加了 active_flag 条件）
        long exists = count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId,userId)
                .eq(VoucherOrder::getVoucherId,voucherId)
                .eq(VoucherOrder::getActiveFlag,VoucherOrder.ACTIVE_FLAG_VALID));
        if (exists > 0) {
            log.info("[MQ] 有效订单已存在，跳过处理（幂等）voucherId={}, userId={}",voucherId,userId);
            return;
        }

        // 扣数据库库存（乐观锁兜底）
        boolean deductSuccess = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getId, voucherId)
                        .gt(SeckillVoucher::getStock, 0));

        if (!deductSuccess) {
            // 库存不足：说明 Redis 和 DB 数据不一致了，属于异常情况
            log.error("[MQ] 数据库库存不足，Redis 与 DB 不一致！voucherId={}", voucherId);
            throw new BusinessException(500, "数据库库存不足");
        }

        // 创建订单（唯一索引兜底，重复会抛 DuplicateKeyException）
        VoucherOrder order = new VoucherOrder();
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(VoucherOrder.STATUS_UNPAID);
        order.setActiveFlag(VoucherOrder.ACTIVE_FLAG_VALID);
        save(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(Long orderId, Long userId) {
        //先查（必须！ 乐观锁要求先拿到当前 version）
        VoucherOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(404,"订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403,"无权操作此订单");
        }
        if (order.getStatus() == VoucherOrder.STATUS_PAID) {
            throw new BusinessException(400,"订单已支付，请勿重复支付");
        }
        if (order.getStatus() == VoucherOrder.STATUS_CANCELED) {
            throw new BusinessException(400,"订单已关闭，无法支付");
        }

        // 乐观锁更新（插件自动加 version 条件）
        order.setStatus(VoucherOrder.STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        boolean success = updateById(order);

        if (!success) {
            // version 不匹配 -> 被人改过了 （可能是定时任务刚关单）
            log.warn("[支付] 乐观锁冲突，orderId={}",orderId);
            //重新查一次，给出准确提示
            VoucherOrder latest = getOne(new LambdaQueryWrapper<VoucherOrder>()
                    .eq(VoucherOrder::getId, orderId)
                    .last("FOR UPDATE"));

            if (latest != null && latest.getStatus() == VoucherOrder.STATUS_CANCELED) {
                throw new BusinessException(400,"订单已超时关闭，无法支付");
            }
            if (latest != null && latest.getStatus() == VoucherOrder.STATUS_PAID) {
                throw new BusinessException(400,"订单已支付");
            }
            throw new BusinessException(409,"订单状态已变更，请刷新后重试");
        }

        log.info("[支付] 订单 {} 支付成功 userId={}", orderId, userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(Long orderId) {
        // 先查（拿version）
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return false;
        }
        // 快速判断：不是未支付状态就不用管
        if (order.getStatus() != VoucherOrder.STATUS_UNPAID) {
            return false;
        }

        // 乐观锁更新状态：1（未支付） -> 3（已取消），同时让出唯一索引槽位
        order.setStatus(VoucherOrder.STATUS_CANCELED);
        order.setActiveFlag(null);     //   关键：置 NULL，允许用户重新购买
        boolean success = updateById(order);

        if (!success) {
            // 没抢到！说明：
            // -用户刚好支付了 （status 变为2 ， version 变了）
            // -或者别的线程已经关过了
            // -> 什么都不做，绝不能回补库存！
            log.info("[关单] 乐观锁冲突，跳过 orderId={}", orderId);
            return false;
        }

        // 到这里说明我成功抢到了关单权 -> 才回补库存
        Long voucherId = order.getVoucherId();
        Long userId = order.getUserId();

        // 回补数据库库存
        seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock + 1")
                        .eq(SeckillVoucher::getId, voucherId));

        // 回补 Redis 库存 + 移除用户下单标记
        stringRedisTemplate.opsForValue()
                .increment(RedisKeyConstants.SECKILL_STOCK_KEY + voucherId);

        // 把用户从“已下单集合”移除 -> 重新获得购买资格
        stringRedisTemplate.opsForSet()
                .remove(RedisKeyConstants.SECKILL_ORDER_KEY + voucherId, String.valueOf(userId));

        log.info("[关单] 订单 {} 已关闭，库存已回补，用户可重新购买 voucherId={}, userId={}", orderId, voucherId, userId);
        return true;
    }

    @Override
    public List<VoucherOrder> listOverdueOrders(LocalDateTime deadline, int limit) {
        return list(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getStatus,VoucherOrder.STATUS_UNPAID)
                .lt(VoucherOrder::getCreateTime, deadline)
                .orderByAsc(VoucherOrder::getCreateTime)
                .last("LIMIT " + limit));
    }
}
