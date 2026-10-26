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
                .eq(VoucherOrder::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrderFromMq(SeckillOrderMessage message) {
        Long voucherId = message.getVoucherId();
        Long userId = message.getUserId();

        // 幂等检查 订单已存在就直接返回
        long exists = count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId,userId)
                .eq(VoucherOrder::getVoucherId,voucherId));
        if (exists > 0) {
            log.info("[MQ] 订单已存在，跳过处理（幂等）voucherId={}, userId={}",voucherId,userId);
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
        save(order);
    }
}
