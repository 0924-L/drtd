package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券:只操作tb_voucher表
     * @param voucher 优惠券信息：Voucher只包含普通卷的信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券，不光要保存tb_voucher表信息，还要保存秒杀券的特有信息，库存，开始结束时间等到tb_seckill_voucher表
     * @param voucher 优惠券信息，Voucher包含秒杀信息（库存，开始结束时间）
     * @return 优惠券id
     */
    @PostMapping("/seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        //根据店铺id查询该店铺的优惠券信息
       return voucherService.queryVoucherOfShop(shopId);
    }
}
