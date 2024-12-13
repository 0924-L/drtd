package com.hmdp.bloomfilter;


import com.hmdp.mapper.ShopMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * ClassName:BloomFilter
 * Package:com.hmdp.bloomfilter
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/5 17:41
 * @Version 1.0
 */
//@Component
//解决缓存穿透问题
public class ShopIdsBloomFilter {
    private static final String BLOOM_FILTER_NAME = "shopIdsBloomFilter";
    private static final double FALSE_PROBABILITY = 0.01; // 误判率
    private static final int EXPECTED_INSERTIONS = 1000; // 预计插入的元素数量

    //@Resource
    private RedissonClient redissonClient;

    public ShopIdsBloomFilter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void initBloomFilter(List<Long> shopIds) {

        //从redissonClient根据过滤器名字获取一个布隆过滤器
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);
        // 初始化布隆过滤器
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        // 将所有商品ID插入布隆过滤器
        for (Long shopId : shopIds) {
            bloomFilter.add(shopId);
        }
    }

    public Boolean isExistById(Long shopId) {
        // 使用布隆过滤器检查商品ID是否可能存在
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);
        //判断店铺id是否存在
        return bloomFilter.contains(shopId);
    }

}
