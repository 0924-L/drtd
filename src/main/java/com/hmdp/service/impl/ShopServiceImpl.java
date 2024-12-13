package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.bloomfilter.ShopIdsBloomFilter;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopIdsBloomFilter shopIdsBloomFilter;
    /**
     * 查询店铺信息，若缓存中有数据，能直接从缓存拿数据返回，若缓存没有指定的店铺信息，则打到数据库查询，然后将查询结果存入缓存
     * @param id
     * @return
     */
    @Override
    public Result queryShopByIdWithCache(Long id) {
        //解决缓存击穿问题
        if (!shopIdsBloomFilter.isExistById(id))
        {
            return Result.fail("店铺不存在");
        }
        String key="shop:"+id;
        String shopjson = stringRedisTemplate.opsForValue().get(key);
        //若缓存有店铺信息，直接返回
        if (StrUtil.isNotBlank(shopjson))
        {
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return Result.ok(shop);
        }
        //若缓存没有店铺信息，从数据库查出，保存到redis中
        Shop shop1 = getById(id);
        if (shop1==null)
        {
            return Result.fail("商品不存在");
        }
        //将商品信息转为json类型字符串，存入redis中.
        String shopvalue=JSONUtil.toJsonStr(shop1);
        //设置超时时间：解决一致性问题【超时兜底+主动更新】
        stringRedisTemplate.opsForValue().set(key,shopvalue,60, TimeUnit.MINUTES);
        return Result.ok(shop1);
    }

    /**
     * 上面的升级版，解决了【缓存击穿问题：互斥锁解决】
     * 走缓存查询店铺信息，且互斥的重构缓存，避免高并发下，大量的sql语句打到数据库中，只有拿到互斥锁的线程，可以查询数据库重构缓存，其他的线程等待重试
     * @param id
     * @return
     */
    @Override
    public Result queryShopByIdWithCacheWithMutex(Long id)  {
        //布隆过滤器解决缓存击穿问题
        if (!shopIdsBloomFilter.isExistById(id))
        {
            return Result.fail("店铺不存在");
        }
        String key="shop:"+id;
        String shopjson = stringRedisTemplate.opsForValue().get(key);
        //若缓存有店铺信息，直接返回
        if (StrUtil.isNotBlank(shopjson))
        {
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return Result.ok(shop);
        }
        //若缓存没有店铺信息，从数据库查出，保存到redis中
        //避免高并发的从数据库重构缓存，需要用setnx互斥锁来，互斥的构建缓存数据【互斥锁：解决了缓存击穿问题，重构缓存互斥，避免大量请求打到数据库去重构缓存】
        //1.1 获取互斥锁
        String keylock="lock:shopquerylock";
        try {
            Boolean getlock = getlock(keylock);
            //能拿到锁
            if (getlock!=null&&getlock)
            {
                Shop shop1 = getById(id);
                if (shop1==null)
                {
                    return Result.fail("商品不存在");
                }
                //将商品信息转为json类型字符串，存入redis中.
                String shopvalue=JSONUtil.toJsonStr(shop1);
                //设置超时时间：解决一致性问题【超时兜底+主动更新】
                stringRedisTemplate.opsForValue().set(key,shopvalue,60, TimeUnit.MINUTES);
                return Result.ok(shop1);
            }
            else
            {
                //拿不到锁
                //4.3 失败，则休眠重试
                Thread.sleep(50);
                return queryShopByIdWithCacheWithMutex(id);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(keylock);
        }


    }

    /**
     * 更新时，主动删除缓存，保证数据一致性
     * @param shop
     * @return
     */
    @Override
    public Result updateByIdWithCache(Shop shop) {
        //1.先更新数据，不用回显，而且默认shop的id是存在的
        boolean b = updateById(shop);
        if (!b)
        {
            return Result.fail("更新失败");
        }
        //2.更新成功，需要删除缓存
        String key="shop:"+shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    /**
     * 取得互斥锁，如果没有key，那么setnx成功。返回ture。如果有key，setnx操作失败，返回false
     * @param key
     * @return
     */
    private Boolean getlock(String key){
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "mutexlock", 10, TimeUnit.SECONDS);
    }
    private Boolean unlock(String key){
        return stringRedisTemplate.delete(key);
    }
}
