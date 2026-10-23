package com.zhilan.zhilanserver.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhilan.zhilanserver.common.BusinessException;
import com.zhilan.zhilanserver.constant.RedisKeyConstants;
import com.zhilan.zhilanserver.entity.SeckillVoucher;
import com.zhilan.zhilanserver.mapper.SeckillVoucherMapper;
import com.zhilan.zhilanserver.service.SeckillVoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillVoucherServiceImpl
        extends ServiceImpl<SeckillVoucherMapper,SeckillVoucher>
        implements SeckillVoucherService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void warmUpStock(Long voucherId) {
        SeckillVoucher voucher = getById(voucherId);
        if (voucher == null) {
            throw new BusinessException(404,"优惠券不存在");
        }

        String stockKey = RedisKeyConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisKeyConstants.SECKILL_ORDER_KEY + voucherId;

        //写入库存
        stringRedisTemplate.opsForValue().set(stockKey,String.valueOf(voucher.getStock()));

        //清空已下单集合（重新预热时应该是全新活动）
        stringRedisTemplate.delete(orderKey);

        log.info("[预热] 券 {} 库存 {} 已写入 Redis",voucherId,voucher.getStock());

    }
}
