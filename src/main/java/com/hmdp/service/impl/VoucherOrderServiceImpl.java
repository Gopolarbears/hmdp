package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ClassName: VoucherOrderServiceImpl
 * Description:
 *
 * @Author Seth Neiman
 * @Create 2024/5/20 14:06
 * @Version 1.0
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                // 获取队列中的订单信息
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    handlePendingList();
                }
                // 创建订单
            }
        }

        private void handlePendingList() {
            while (true) {
                // 获取队列中的订单信息
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                // 创建订单
            }
        }
    }

//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                // 获取队列中的订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//                // 创建订单
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 使用redisson分布式锁赖解决问题
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 去lua脚本中执行查询库存, 判断一人一单操作, 下单成功返回0, 库存不足返回1, 重复购买返回2
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),voucherId.toString(),
                userId.toString(), String.valueOf(orderId)
        );

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1? "库存不足" : "请勿重复下单");
        }

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 去lua脚本中执行查询库存, 判断一人一单操作, 下单成功返回0, 库存不足返回1, 重复购买返回2
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate
//                .execute(SECKILL_SCRIPT, Collections.emptyList(),voucherId.toString(), userId.toString());
//
//        int r = result.intValue();
//        if (r != 0) {
//            return Result.fail(r == 1? "库存不足" : "请勿重复下单");
//        }
//
//        // 购买成功, 将下单信息保存到阻塞队列中
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        // 用户id
//
//        voucherOrder.setUserId(userId);
//        // 优惠券id
//        voucherOrder.setVoucherId(voucherId);
//
//        orderTasks.add(voucherOrder);
//
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        return Result.ok(orderId);
//    }

    // 未使用异步下单进行优化
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 提交优惠券id
//        // 判断是否大于开始时间, 小于结束时间
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束");
//        }
//        // 判断库存
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//        // 采用锁加事务的方式解决并发问题, 但是由于锁在不同JVM中不一样, 所以不适用于分布式环境
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        // 采用redis实现分布式锁来解决问题
//        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//        // 使用redisson分布式锁赖解决问题
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("一个人只允许下一单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getId();

        // 一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经重复购买过一次");
            return;
        }

        // 下单
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1").eq("voucher_id", voucherOrder).gt("stock", 0).update();
        if(!success) {
            log.error("库存不足");
            return;
        }
//        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        // 用户id
//
//        voucherOrder.setUserId(userId);
//        // 优惠券id
//        voucherOrder.setVoucherId(voucherOrder);

        save(voucherOrder);
    }
}
