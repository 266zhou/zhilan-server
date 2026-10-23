package com.zhilan.zhilanserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhilan.zhilanserver.entity.SeckillVoucher;

public interface SeckillVoucherService extends IService<SeckillVoucher> {

    /**
     * 把数据库库存预热到 Redis
     */
    void warmUpStock(Long voucherId);
}
