package com.zhilan.zhilanserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhilan.zhilanserver.common.BusinessException;
import com.zhilan.zhilanserver.dto.ShopQueryDTO;
import com.zhilan.zhilanserver.entity.Shop;
import com.zhilan.zhilanserver.mapper.ShopMapper;
import com.zhilan.zhilanserver.service.ShopService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper,Shop> implements ShopService {

    private static final Set<String> SORT_FIELDS = Set.of("score","sales","avgPrice");

    @Override
    public Page<Shop> pageQuery(ShopQueryDTO query){
        //兜底：分页参数非法时给默认值
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10
                : Math.min(query.getPageSize(), 100);

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(query.getType()), Shop::getType, query.getType())
                .eq(StringUtils.isNotBlank(query.getCity()), Shop::getCity, query.getCity())
                .eq(query.getStatus() != null, Shop::getStatus, query.getStatus())
                .and(StringUtils.isNotBlank(query.getKeyword()),w -> w
                        .like(Shop::getName, query.getKeyword())
                        .or()
                        .like(Shop::getAddress, query.getKeyword()));

        //排序：只允许白名单字段，防SQL注入
        String sortBy = query.getSortBy();
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        if (StringUtils.isNotBlank(sortBy) && SORT_FIELDS.contains(sortBy)) {
            switch (sortBy) {
                case "score" -> wrapper.orderBy(true,asc,Shop::getScore);
                case "sales" -> wrapper.orderBy(true,asc,Shop::getSales);
                case "avgPrice" -> wrapper.orderBy(true,asc,Shop::getAvgPrice);
                default -> wrapper.orderByDesc(Shop::getId);
            }
        } else {
            wrapper.orderByDesc(Shop::getId);
        }
        return page(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public Shop getDetail(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400,"商家ID不合法");
        }
        Shop shop = getById(id);
        if (shop == null) {
            throw new BusinessException(404,"商家不存在或已下架");
        }
        return shop;
    }
}