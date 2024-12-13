package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        //走缓存查店铺信息
        //return Result.ok(shopService.queryShopByIdWithCache(id));

        //走缓存且互斥的重构缓存【解决缓存击穿，复杂度高key的高并发问题】
        return Result.ok(shopService.queryShopByIdWithCacheWithMutex(id));


        //不走缓存直接查询数据库返回
       // return Result.ok(shopService.getById(id));
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {

        //更新店铺信息，若缓存中有该店铺信息，需要修改数据库后，删除缓存中这个店铺信息，保证数据一致性【主动更新部分】
        //为什么先更新数据库，后删除缓存：会避免长时间的不一致问题。如果在更新过程中，有请求来查数据，还是会查到老数据，但数据更新过后，此后的请求，就会打到数据库，拿到最新的数据，存在缓存中
        //如果先删除缓存，在更新数据库，会产生，在更新过程中，另一个请求就把老的数据，缓存到了redis中，此时会发生长时间的数据不一致问题，(后面来的请求都会从缓存拿这个错的数据)直到ttl超时
       return shopService.updateByIdWithCache(shop);

        // 写入数据库
        //shopService.updateById(shop);
        //return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
