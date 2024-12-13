package com.hmdp;

import com.hmdp.utils.RedisGlobalUniqueIdCreater;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Scanner;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
   private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Test
    public void test01() {
        RLock lock = redissonClient.getLock("123");

        stringRedisTemplate.opsForSet().add("test","99999");
        stringRedisTemplate.opsForValue();
    }


}
