package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName:RedisGlobalUniqueIdCreater
 * Package:com.hmdp.utils
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/6 15:23
 * @Version 1.0
 */
@Component
public class RedisGlobalUniqueIdCreater {
    //思想是：符号位0+31位时间戳+32位序列号。
    //时间戳以秒为单位，是严格递增的
    //序列号是存在redis中的，key为当天的date(YY:MM:DD),value为自增长的一个Integer数。每天从1开始（为了方便统计每天的营业额）
    //第二天从1开始，但是因为前面的时间戳变了，所以永远不可能重复
    //每秒可产生2^32个id。时间戳可以使用69年，69年后可能会发生重复

    //2024-01-01 00:00:00 的时间戳。为了使业务使用的时间更长。当前时间和这个时间相减，得到真正的时间戳。
    //为什么不直接使用当前时间的时间戳，因为当前时间戳是距离1970-01-01T00:00:00Z的秒数，已经用了大部分。
    private static final long BEGIN_TIMESTAMP=1704038400L;
   private static StringRedisTemplate stringRedisTemplate;

    public RedisGlobalUniqueIdCreater(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *
     * @param key 是业务名称，不同业务名称得到的全局id隔离开
     * @return
     */
   public  long nextId(String key){
        //生成时间戳
        //当前时间戳
        long Currenttimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        //真实的业务时间戳
        long timestamp=Currenttimestamp-BEGIN_TIMESTAMP;

        //生成序列号：每天从1开始
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long serialnumber = stringRedisTemplate.opsForValue().increment("GlobalId:" + key + ":" + date);//每天的第一个没有，从0开始，自增之后返回1.后面再1的基础上以此类推
        //拼接时间戳和序列号返回
        return timestamp<<32|serialnumber;
    }
}
