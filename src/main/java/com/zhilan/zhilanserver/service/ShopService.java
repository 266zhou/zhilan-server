package com.zhilan.zhilanserver.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhilan.zhilanserver.dto.ShopQueryDTO;
import com.zhilan.zhilanserver.entity.Shop;

public interface ShopService extends IService<Shop> {

    /** 商家分页查询（关键词/类型/城市/排序） */
    Page<Shop> pageQuery(ShopQueryDTO query);

    /** 查询商家详情 */
    Shop getDetail(Long id);

    /** 新增商家，返回新 id */
    Long createShop(Shop shop);

    /** 更新商家 （更新数据库后删除缓存） */
    void updateShop(Shop shop);

    /** 删除商家 （逻辑删除，并删除缓存） */
    void deleteShop(Long id);
}