package com.hmdp.utils.lock;

/**
 * ClassName:ILock
 * Package:com.hmdp.utils.lock
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/7 19:06
 * @Version 1.0
 */
public interface ILock {
    boolean trylock(long locktime);

    void unlock();
}
