package com.zhilan.zhilanserver.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhilan.zhilanserver.dto.ShopQueryDTO;
import com.zhilan.zhilanserver.entity.Shop;

public interface ShopService extends IService<Shop> {

    /**
     * 商家分页查询（关键词/类型/城市/排序）
     */
    Page<Shop> pageQuery(ShopQueryDTO query);

    /**
     * 查询商家详情（待完成）
     */
    Shop getDetail(Long id);
}