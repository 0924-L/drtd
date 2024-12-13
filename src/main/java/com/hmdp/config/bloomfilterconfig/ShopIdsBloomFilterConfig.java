package com.hmdp.config.bloomfilterconfig;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.bloomfilter.ShopIdsBloomFilter;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ClassName:ShopIdsBloomFilterConfig
 * Package:com.hmdp.config.bloomfilterconfig
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/5 18:02
 * @Version 1.0
 */
@Configuration
public class ShopIdsBloomFilterConfig {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ShopMapper shopMapper;

    @Bean
    public ShopIdsBloomFilter shopIdsBloomFilter(){
        ShopIdsBloomFilter shopIdsBloomFilter1 = new ShopIdsBloomFilter(redissonClient);
        QueryWrapper<Shop> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id");
        List<Shop> shops = shopMapper.selectList(queryWrapper);
        List<Long> idcollects = shops.stream()
                .map(Shop::getId) // 根据你的实体类实际主键字段类型修改
                .collect(Collectors.toList());
        shopIdsBloomFilter1.initBloomFilter(idcollects);
        return shopIdsBloomFilter1;
    }
}
