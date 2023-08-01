package com.zhn.service.impl;

import com.zhn.dto.Result;
import com.zhn.entity.VoucherOrder;
import com.zhn.mapper.VoucherOrderMapper;
import com.zhn.service.ISeckillVoucherService;
import com.zhn.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhn.utils.RedisIdWorker;
import com.zhn.utils.UserHolder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Resource
    RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = redisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.返回订单id
        return Result.ok(orderId);
    }
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            save(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 5.一人一单
//        Long userId = UserHolder.getUser().getId();
//
//        // 创建锁对象
//        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//        // 尝试获取锁
//        boolean isLock = redisLock.tryLock();
//        // 判断
//        if(!isLock){
//            // 获取锁失败，直接返回失败或者重试
//            return Result.fail("不允许重复下单！");
//        }
//
//        try {
//            // 5.1.查询订单
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            // 5.2.判断是否存在
//            if (count > 0) {
//                // 用户已经购买过了
//                return Result.fail("用户已经购买过一次！");
//            }
//
//            // 6.扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1") // set stock = stock - 1
//                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
//                    .update();
//            if (!success) {
//                // 扣减失败
//                return Result.fail("库存不足！");
//            }
//
//            // 7.创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            // 7.1.订单id
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            // 7.2.用户id
//            voucherOrder.setUserId(userId);
//            // 7.3.代金券id
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//
//            // 7.返回订单id
//            return Result.ok(orderId);
//        } finally {
//            // 释放锁
//            redisLock.unlock();
//        }
//    }



//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if( voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀尚未开始!");
//        }
//        //3.判断秒杀是否结束
//        if( voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已经结束!");
//        }
//        //4.判断库存是否充足
//        if(voucher.getStock() < 1){
//            return Result.fail("库存不足!");
//        }
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
////            //获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock(redisTemplate,"order:"+userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        if(!isLock)
//        {
//            //获取锁失败，返回错误信息或重试
//            return Result.fail("不允许重复下单");
//        }
//        //获取代理对象（事务）
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//    }
}
