package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisGlobalUniqueIdCreater;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;


    /**
     * 秒杀优惠卷下单
     * @param voucherId
     * @return
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        //同步完成库存判断和一人一单的判断，同步完成扣减库存和下单业务
        //return voucherOrderService.seckillVoucherOrdering(voucherId);
        //异步完成库存判断和一人一单的判断。【表示下单了】。扣减库存和下单逻辑，放到消息队列中异步执行。提高下单业务的时间
        return voucherOrderService.asynchronousSeckillVoucherOrdering(voucherId);
    }
}
