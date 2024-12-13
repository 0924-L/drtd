package com.hmdp.mqlistener;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lock.RedisLock;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Proxy;

/**
 * ClassName:RabbitMQListener
 * Package:com.hmdp.mqlistener
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/13 20:00
 * @Version 1.0
 */
@Component
@Slf4j
@Data
public class RabbitMQListener implements Serializable {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RabbitMQListener() {
    }

//    public RabbitMQListener(ISeckillVoucherService seckillVoucherService, IVoucherOrderService voucherOrderService, StringRedisTemplate stringRedisTemplate) {
//        this.seckillVoucherService = seckillVoucherService;
//        this.voucherOrderService = voucherOrderService;
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @RabbitListener(queues = "voucherorders.queue")
    public void listenSimpleQueueMessage(VoucherOrder voucherOrder) throws InterruptedException {

        //用分布式锁解决集群环境下的一人多单问题（解决不同服务锁不一致）
        RedisLock voucherorderLock = new RedisLock("voucherorder:"+voucherOrder.getUserId(), stringRedisTemplate);
        boolean trylock = voucherorderLock.trylock(10L);//10秒过期，防止死锁
        //拿不到锁
        if (!trylock)
        {
            log.error("不允许重复下单");
            //return Result.fail("不允许重复下单");
            return;
        }
        try {
            //能拿到锁
            //拿到IVoucherOrderService接口的代理对象
            //获取代理对象：1添加依赖2.暴露代理对象
            RabbitMQListener proxy= (RabbitMQListener) AopContext.currentProxy();
            //用代理对象调用事务方法，事务会生效
             proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            voucherorderLock.unlock();
        }
    }
        //异步处理rabbitmq的消息
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.1查询用户id+查询优惠券订单数据
        Long id = voucherOrder.getUserId();
        //给一人一单的并发问题代码上锁

        Integer count =voucherOrderService.query().eq("user_id", id).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次!");
            //return Result.fail("用户已经购买过一次！");
            return;
        }
        //用户没买过且库存够
        //6，扣减库存
        //解决超卖问题：where voucher_id=voucherId and stock > 0;
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0).update();

        if (!success) {
            //扣减库存
            log.error("库存不足!");
            return;
            //return Result.fail("库存不足！");
        }
        //库存扣减成功
        //存入优惠券下单订单
       voucherOrderService.save(voucherOrder);

    }
}
