package com.hmdp.utils.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * ClassName:RedisLock
 * Package:com.hmdp.utils.lock
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/7 19:08
 * @Version 1.0
 */
public class RedisLock implements ILock{
    private static final String THREADID_PREFIX="Thread";

    private static final String KEY_PREFIX="lock:";
    //业务名称
    private  String name;
    private StringRedisTemplate stringRedisTemplate;

    public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean trylock(long locktime) {
        //获取当前线程标识，作为value存入。后面的解锁误删问题会用到
        long currentId = Thread.currentThread().getId();

        //获取锁:成功b==true。失败b==flase
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, THREADID_PREFIX+currentId, locktime, TimeUnit.SECONDS);
        return b;
    }

    @Override
    public void unlock() {
        //还有问题：redis的拿锁，比较锁，删除锁，不是原子操作。是三个独立的操作
        //极限的误删问题：拿锁，比较锁，删除锁（拿锁，比较锁锁都在。删除锁时候锁过期了，那么这个删除操作，就删除的是别人的锁）
        //解决：lua脚本，将三个redis（拿锁，比较锁，删除锁）原子化。【因为原子性，在删除那步过期了，就不会误删】
        //避免误删问题
        //当前线程id
        String currentid = THREADID_PREFIX + Thread.currentThread().getId();
        //从redis中取的线程id
        String rediscurrentid = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //作比较，避免误删问题
        if (currentid.equals(rediscurrentid))
        {
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
        //不相等，表示不是自己的锁，不做删除操作
        //只删除key，不判断value，会有误删问题【删除了其他线程的锁，导致那个线程会有并发安全问题，那个线程存在一人多单现象，
        // 为什么？线程1误删了线程2（用户1下单）的锁。线程3（用户1下单）来，可以拿到锁，执行下单业务，此时用户1下了两单】
//        Boolean delete = stringRedisTemplate.delete(KEY_PREFIX + name);
//        return delete;
    }
}
