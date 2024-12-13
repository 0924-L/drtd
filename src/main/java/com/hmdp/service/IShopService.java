package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
    /**
     * 查询店铺信息，若缓存中有数据，能直接从缓存拿数据返回，若缓存没有指定的店铺信息，则打到数据库查询，然后将查询结果存入缓存
     * @param id
     * @return
     */
    Result queryShopByIdWithCache(Long id);

    /**
     * 上面的升级版，解决了【缓存击穿问题：互斥锁解决】
     * 走缓存查询店铺信息，且互斥的重构缓存，避免高并发下，大量的sql语句打到数据库中，只有拿到互斥锁的线程，可以查询数据库重构缓存，其他的线程等待重试
     * @param id
     * @return
     */
    Result queryShopByIdWithCacheWithMutex(Long id);

    /**
     * 更新时，主动删除缓存，保证数据一致性
     * @param shop
     * @return
     */
    Result updateByIdWithCache(Shop shop);



}
