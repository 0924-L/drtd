package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucherOrdering(Long voucherId);

    /**
     * 生成订单的事务函数，因为代理对象需要调用它，来保证非自调用的事务不生效问题。因为是接口调用。且是代理对象，所以接口必须在接口中出现
     * @param voucherId
     * @return
     */
    Result createVoucherOrder(Long voucherId);

    /**
     * 异步下单，先判断库存和限购，若满足，直接返回下单成功（顺便扣减redis 的库存 和 添加该用户到set中）；将下单信息放入消息队列中；异步读取消息队列，异步完成下单
     * @param voucherId
     * @return
     */
    Result asynchronousSeckillVoucherOrdering(Long voucherId);

    /**
     * 异步下单逻辑
     * @param voucherOrder
     */
    void createVoucherOrder1(VoucherOrder voucherOrder);
}
