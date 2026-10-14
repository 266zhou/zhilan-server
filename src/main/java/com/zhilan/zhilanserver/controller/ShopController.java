package com.zhilan.zhilanserver.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhilan.zhilanserver.common.Result;
import com.zhilan.zhilanserver.dto.ShopQueryDTO;
import com.zhilan.zhilanserver.entity.Shop;
import com.zhilan.zhilanserver.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 商家信息接口
 */
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
@Validated
public class ShopController {

    private final ShopService shopService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<Page<Shop>> page(ShopQueryDTO query) {
        return Result.ok(shopService.pageQuery(query));
    }

    /** 商家详情 */
    @GetMapping("/{id}")
    public Result<Shop> detail(@PathVariable Long id) {
        return Result.ok(shopService.getDetail(id));
    }

    /** 新增商家 */
    @PostMapping
    public Result<Long> save(@Valid @RequestBody Shop shop) {
        shop.setId(null);
        shop.setSales(shop.getSales() == null ? 0 : shop.getSales());
        shop.setComments(shop.getComments() == null ? 0 : shop.getComments());
        shop.setStatus(shop.getStatus() == null ? 1 : shop.getStatus());
        shopService.save(shop);
        return Result.ok(shop.getId());
    }

    /** 更新商家 */
    @PutMapping
    public Result<Void> update(@RequestBody Shop shop) {
        if (shop.getId() == null) {
            return Result.fail(400,"id 不能为空");
        }
        shopService.updateById(shop);
        return Result.ok();
    }

    /** 删除商家（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shopService.removeById(id);
        return Result.ok();
    }
}