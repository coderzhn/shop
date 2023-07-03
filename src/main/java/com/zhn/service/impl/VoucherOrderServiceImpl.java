package com.zhn.service.impl;

import com.zhn.dto.Result;
import com.zhn.entity.SeckillVoucher;
import com.zhn.entity.VoucherOrder;
import com.zhn.mapper.VoucherOrderMapper;
import com.zhn.service.ISeckillVoucherService;
import com.zhn.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhn.utils.RedisIdWorker;
import com.zhn.utils.SimpleRedisLock;
import com.zhn.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if( voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始!");
        }
        //3.判断秒杀是否结束
        if( voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束!");
        }
        //4.判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock(redisTemplate,"order:"+userId);
        //获取锁
        boolean isLock = lock.tryLock(1200);
        if(!isLock)
        {
            //获取锁失败，返回错误信息或重试
            return Result.fail("不允许重复下单");
        }
        //获取代理对象（事务）
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //6.一人一单
        Long userId = UserHolder.getUser().getId();
        //6.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //6.2 判断是否存在
        if (count > 0) {
            //用户已经购买过
            return Result.fail("用户已经购买过一次");
        }
        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId).gt("stock",0)
                .update();
        if (!success){
            //扣减失败
            return Result.fail("库存不足!");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2 用户id
        voucherOrder.setUserId(userId);
        //7.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
