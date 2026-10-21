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
        return Result.ok(shopService.createShop(shop));
    }

    /** 更新商家 */
    @PutMapping
    public Result<Void> update(@RequestBody Shop shop) {
        shopService.updateShop(shop);
        return Result.ok();
    }

    /** 删除商家（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shopService.deleteShop(id);
        return Result.ok();
    }
}