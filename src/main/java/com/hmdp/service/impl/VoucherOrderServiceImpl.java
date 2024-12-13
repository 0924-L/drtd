package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisGlobalUniqueIdCreater;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lock.RedisLock;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.print.attribute.standard.OrientationRequested;
import java.time.LocalDateTime;
import java.util.HashMap;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    //秒杀券service
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    //秒杀券订单的全局唯一id生成器
    @Resource
    private RedisGlobalUniqueIdCreater redisGlobalUniqueIdCreater;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     *
     * @param voucherId 秒杀券信息（券id，库存，开始结束时间等）的id
     * @return
     */
    @Override
    public Result seckillVoucherOrdering(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher==null)
        {
            return Result.fail("优惠券不存在");
        }
        //券存在
        // 2.判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        //这里存在多线程的超卖问题：线程1，读取了库存为1.去减库存+生成订单。在此期间，线程234，也读取了库存为1.此时线程1，库存减为0。线程234此时减库存为-1，-2，-3.超卖问题产生
        //解决：乐观锁，读取秒杀券信息时候读取版本号version，修改库存的时候，where条件中加上这个version=（读取的版本号）。等于说：读取和修改的数据中间没有被其他线程修改过才能减库存成功
        //      上面这种方式有问题：减库存的成功的概率太低，一个线程修改时候，在此期间，所有的线程读取的version在修改库存时都不会操作成功。
        //优化：我们只需防止超卖，即库存<0,还在买的情况。在where 条件中加上stock>0.减库存语句才能执行成功。
        //分析：线程1，读取了库存为1.去减库存+生成订单（此时库存=1>0）。在此期间，线程234，也读取了库存为1。此时线程1，库存减为0。线程234此时减库存(此时库存=0不大于0)。操作失败。【读取数据】
        //          其他情况，读取库存为10，该线程减去1.此时其他线程虽然读到的也是10，但在减库存时，会判断库存>0才减
        // 4.判断库存是否充足
        //乐观锁变种解决超卖问题
        Integer stock = seckillVoucher.getStock();
        //库存不足
        if (stock < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        //库存足
        //5.解决秒杀券的一人一单问题：在优惠券的订单表中根据当前用户id查询数据，若有数据（该用户已经买过），就不能下单。没有数据才能正常下单
        //存在多线程并发问题：多个线程同时查询订单信息，查不到。表示未购买过，多个线程进行订单插入操作，导致一人多单现象【用户用脚本抢票】
        //多线程安全问题解决：因为是插入操作，无法用乐观锁。需要用悲观锁解决
        // 锁的粒度：（锁是什么：当前用户的id）为什么不锁住整个方法。因为不同用户也不能并发执行。我们只需要解决一个用户的并发下单问题。锁住了整个方法。锁的粒度太大了
        //解决事务内部加锁的事务未提交但放锁问题：应该是事务提交之后再去释放锁
        //5.1 将下订单的业务抽取出一个方法，去掉原方法的声明式事务注解。加到下单方法中
        //      在进入下单方法逻辑之前获取锁，然后进入事务方法，方法执行完。事务提交。然后在放锁
        //新的问题：自调用事务函数在运行时不会导致实际的事务
        Long id = UserHolder.getUser().getId();

        //用分布式锁解决集群环境下的一人多单问题（解决不同服务锁不一致）
        RedisLock voucherorderLock = new RedisLock("voucherorder:"+id, stringRedisTemplate);
        boolean trylock = voucherorderLock.trylock(10L);//10秒过期，防止死锁
        //拿不到锁
        if (!trylock)
        {
            return Result.fail("不允许重复下单");
        }
        try {
            //能拿到锁
            //拿到IVoucherOrderService接口的代理对象
            //获取代理对象：1添加依赖2.暴露代理对象
            IVoucherOrderService proxy= (IVoucherOrderService) AopContext.currentProxy();
            //用代理对象调用事务方法，事务会生效
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            voucherorderLock.unlock();
        }

//用用户id作为锁，有分布式环境下的一人多单问题，因为不同服务的锁不是同一把锁（因为jvm里的锁监视器不同）。同个用户在不同服务器可以产生一人多单问题
//        synchronized (id.toString().intern()) {
//            //拿到IVoucherOrderService接口的代理对象
//            //获取代理对象：1添加依赖2.暴露代理对象
//            IVoucherOrderService proxy= (IVoucherOrderService) AopContext.currentProxy();
//            //用代理对象调用事务方法，事务会生效
//            return proxy.createVoucherOrder(voucherId);
//        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5.1查询用户id+查询优惠券订单数据
        Long id = UserHolder.getUser().getId();
        //给一人一单的并发问题代码上锁

            Integer count = query().eq("user_id", id).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }
            //用户没买过且库存够
            //6，扣减库存
            //解决超卖问题：where voucher_id=voucherId and stock > 0;
            boolean success = seckillVoucherService.update()
                    .setSql("stock= stock -1")
                    .eq("voucher_id", voucherId).gt("stock",0).update();

            if (!success) {
                //扣减库存
                return Result.fail("库存不足！");
            }

//有超卖问题的代码
//        Integer stock = seckillVoucher.getStock();
//        if (stock < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//        //库存足
//        //5，扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock= stock -1")
//                .eq("voucher_id", voucherId).update();
//        if (!success) {
//            //扣减库存
//            return Result.fail("库存不足！");
//        }

            //库存扣减成功
            //7.创建订单对象+存入数据库
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.生成全局唯一订单id
            long orderId = redisGlobalUniqueIdCreater.nextId("voucherorder");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            Long userId = UserHolder.getUser().getId();
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            //只为这三项赋了值
            //存入优惠券下单订单
            save(voucherOrder);
            //返回订单id
            return Result.ok(orderId);

    }

    @Override
    public Result asynchronousSeckillVoucherOrdering(Long voucherId) {
        //判断库存+限购
        String s=stringRedisTemplate.opsForValue().get("SeckillVoucher:stock:" + voucherId);
        Integer stock=Integer.valueOf(s);
        if (stock<=0)
        {
            return Result.fail("库存不足");
        }
        stringRedisTemplate.opsForValue().decrement("SeckillVoucher:stock:" + voucherId);//扣减库存

        Long userid = UserHolder.getUser().getId();
//        Boolean b= false;//判断是否买过
//        try {
//            b = stringRedisTemplate.opsForSet().isMember("order:SeckillVoucher:"+voucherId,String.valueOf(userid));
//        } catch (Exception e) {
//            System.out.println("set异常");
//            throw new RuntimeException(e);
//        }
        Boolean isbuy = stringRedisTemplate.opsForSet().isMember("order:SeckillVoucher:" + voucherId, String.valueOf(userid));
        if (isbuy)
        {
            return Result.fail("不允许重复下单");
        }
        //没下过单
        stringRedisTemplate.opsForSet().add("order:SeckillVoucher:"+voucherId, String.valueOf(userid));//没买过，给set加上。表示买过
        //库存足够+没买过
        // 队列名称
        String queueName = "voucherorders.queue";
        // 消息
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisGlobalUniqueIdCreater.nextId("SeckillVoucherOrder");
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //将订单的全局唯一id，用户id，优惠券id。放入消息队列
        rabbitTemplate.convertAndSend(queueName,voucherOrder);

        return Result.ok(id);


    }

    @Transactional
    public void createVoucherOrder1(VoucherOrder voucherOrder) {
        //5.1查询用户id+查询优惠券订单数据
        Long id = voucherOrder.getUserId();
        //给一人一单的并发问题代码上锁

        Integer count =query().eq("user_id", id).count();
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
        save(voucherOrder);

    }
}
