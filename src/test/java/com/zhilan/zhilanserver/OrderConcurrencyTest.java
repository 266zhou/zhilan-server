package com.zhilan.zhilanserver;

import com.zhilan.zhilanserver.entity.VoucherOrder;
import com.zhilan.zhilanserver.entity.SeckillVoucher;
import com.zhilan.zhilanserver.service.VoucherOrderService;
import com.zhilan.zhilanserver.service.SeckillVoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 支付 vs 关单 并发冲突测试（自动准备数据版）
 * 每次运行会：
 *   1. 用一个新的 userId（不重复）
 *   2. 把 DB 库存同步到 Redis，清空已下单集合
 *   3. 走完整链路抢一张券（Lua → MQ → 消费者）
 *   4. 同时发起「支付」和「关单」
 *   5. 自动校验结果是否符合预期
 * ✅ 不需要改常量，可以直接重复运行 10 次
 * ⚠️ 前提：数据库里 seckill_voucher.id=1 的 stock 必须 > 0
 */
@SpringBootTest
public class OrderConcurrencyTest {

    @Autowired
    private VoucherOrderService voucherOrderService;
    @Autowired
    private SeckillVoucherService seckillVoucherService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final Long VOUCHER_ID = 1L;
    private static final String STOCK_KEY = "seckill:stock:" + VOUCHER_ID;
    private static final String ORDER_SET_KEY = "seckill:order:" + VOUCHER_ID;
    private static final int PAY_DELAY_MS = 1;

    @Test
    void testPayVsClose() throws Exception {
        // ==================== ① 自动准备数据 ====================

        // 每次用不同 userId，避免和之前测试的订单冲突
        long userId = 100000 + System.currentTimeMillis() % 100000;

        //检查数据库库存
        SeckillVoucher voucher = seckillVoucherService.getById(VOUCHER_ID);
        if (voucher == null) {
            throw new IllegalStateException("优惠券" + VOUCHER_ID + "不存在");
        }
        if (voucher.getStock() == null || voucher.getStock() <= 0) {
            throw new IllegalStateException(
                    "数据库库存为 0，无法测试。请先执行：\n" +
                    "  USE zhilan;\n" +
                    "  UPDATE seckill_voucher SET stock = 10 WHERE id = 1;");
        }

        //把库存同步到 Redis（保证 Redis 与 DB 一致），并清空已下单集合
        stringRedisTemplate.opsForValue().set(STOCK_KEY, String.valueOf(voucher.getStock()));
        stringRedisTemplate.delete(ORDER_SET_KEY);

        //走完整链路抢券（Lua → MQ → 消费者异步建单）
        voucherOrderService.seckillVoucher(VOUCHER_ID, userId);

        // 等消费者把订单建好（异步，必须轮询等待）
        VoucherOrder order = null;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            order = voucherOrderService.getOrderByUser(VOUCHER_ID, userId);
            if (order != null) {
                break;
            }
        }
        if (order == null) {
            throw new IllegalStateException("准备失败：等待 3 秒订单仍未创建，检查 MQ 消费者是否正常");
        }

        Long orderId = order.getId();
        String stockBefore = stringRedisTemplate.opsForValue().get(STOCK_KEY);

        System.out.println("================ 准备完成 ================");
        System.out.println("userId       = " + userId);
        System.out.println("orderId      = " + orderId);
        System.out.println("status       = " + order.getStatus() + "  (1未支付)");
        System.out.println("activeFlag   = " + order.getActiveFlag() + "  (1有效)");
        System.out.println("version      = " + order.getVersion());
        System.out.println("关单前 Redis 库存 = " + stockBefore);
        System.out.println("=========================================");

        // ==================== ② 并发触发 ====================

        CountDownLatch startGun = new CountDownLatch(1);   //发令枪
        CountDownLatch done = new CountDownLatch(2);       //两个线程都结束
        ExecutorService poll = Executors.newFixedThreadPool(2);

        //线程 1：关单
        poll.submit(() -> {
            try {
                startGun.await();
                boolean ok = voucherOrderService.closeOrder(orderId);
                System.out.println(">>> 关单: " + (ok ? "成功" : "失败（未抢到）"));
            } catch (Exception e) {
                System.out.println(">>> 关单: 异常 - " + e.getMessage());
            } finally {
                done.countDown();
            }
        });


        //线程 2：支付
        poll.submit(() -> {
            try {
                startGun.await();
                Thread.sleep(PAY_DELAY_MS);
                voucherOrderService.payOrder(orderId, userId);
                System.out.println(">>> 支付: 成功");
            } catch (Exception e) {
                System.out.println(">>> 支付: 失败 - " + e.getMessage());
            } finally {
                done.countDown();
            }
        });

        startGun.countDown();                // 两个线程同时起跑
        done.await(10, TimeUnit.SECONDS);
        poll.shutdown();

        // ==================== ③ 读取最终状态 ====================

        VoucherOrder finalOrder = voucherOrderService.getById(orderId);
        String stockAfter = stringRedisTemplate.opsForValue().get(STOCK_KEY);
        Boolean inSet = stringRedisTemplate.opsForSet()
                .isMember(ORDER_SET_KEY, String.valueOf(userId));

        System.out.println("================ 最终状态 ================");
        System.out.println("status       = " + finalOrder.getStatus() + "  (2已支付 / 3已取消)");
        System.out.println("activeFlag   = " + finalOrder.getActiveFlag() + "  (1有效 / null已取消)");
        System.out.println("version      = " + finalOrder.getVersion() + "  (改过状态就是 1)");
        System.out.println("Redis 库存   = " + stockBefore + " → " + stockAfter);
        System.out.println("用户还在集合 = " + inSet);
        System.out.println("=========================================");

        // ==================== ④ 自动校验 ====================

        int status = finalOrder.getStatus();

        if (status == VoucherOrder.STATUS_PAID) {
            // ---------- 结果 A：支付赢了 ----------
            if (!Objects.equals(stockBefore,stockAfter)) {
                throw new AssertionError("【超卖】支付成功但库存被回补了！"
                        + stockBefore + " ->" + stockAfter);
            }
            if (!Integer.valueOf(VoucherOrder.ACTIVE_FLAG_VALID)
                    .equals(finalOrder.getActiveFlag())) {
                throw new AssertionError("支付成功但 active_flag 不是 1，实际 = "
                        + finalOrder.getActiveFlag());
            }
            if (!Boolean.TRUE.equals(inSet)) {
                throw new AssertionError("支付成功但用户被移出了已下单集合");
            }
            System.out.println("结果正常：支付赢了 → 库存不变、active_flag=1、用户在集合里");
        } else if (status == VoucherOrder.STATUS_CANCELED) {
            // ---------- 结果 B：关单赢了 ----------
            if (stockBefore == null || stockAfter == null) {
                throw new AssertionError("库存 key 不存在：" + STOCK_KEY
                        + "（before=" + stockBefore + ", after=" + stockAfter + "）");
            }
            int before = Integer.parseInt(stockBefore);
            int after = Integer.parseInt(stockAfter);
            if (after != before + 1) {
                throw new AssertionError("【少卖】关单成功但库存没 +1："
                        + before + "->" + after);
            }
            if (finalOrder.getActiveFlag() != null) {
                throw new AssertionError("关单成功但 active_flag 不是 NULL，实际 = "
                        + finalOrder.getActiveFlag() + "（用户将无法重新购买）" );
            }
            if (!Boolean.FALSE.equals(inSet)) {
                throw new AssertionError("关单成功但用户还在已下单集合里");
            }
            System.out.println("结果正常：关单赢了 → 库存 +1、active_flag=NULL、用户被移除");
        } else {
            // ---------- 结果 C：两个都没成功（异常情况） ----------
            throw new AssertionError("订单停留在 status=" + status
                    + "，说明支付和关单都没生效，检查日志");
        }
    }

    @Test
    void testCloseTwice() throws Exception {
        // 准备：抢一张券
        long userId = 200000 + System.currentTimeMillis() % 100000;
        stringRedisTemplate.delete(ORDER_SET_KEY);

        voucherOrderService.seckillVoucher(VOUCHER_ID, userId);
        VoucherOrder order = null;
        for (int i = 0; i < 30 && order == null; i++) {
            Thread.sleep(100);
            order = voucherOrderService.getOrderByUser(VOUCHER_ID, userId);
        }
        if (order == null) {
            throw new IllegalStateException("准备失败：订单未创建");
        }

        Long orderId = order.getId();
        String stockBefore = stringRedisTemplate.opsForValue().get(STOCK_KEY);

        //连续关两次
        boolean first = voucherOrderService.closeOrder(orderId);
        boolean second = voucherOrderService.closeOrder(orderId);

        String stockAfter = stringRedisTemplate.opsForValue().get(STOCK_KEY);

        System.out.println("第一次关单: " + first);
        System.out.println("第二次关单: " + second);
        System.out.println("Redis 库存: " + stockBefore + " → " + stockAfter);

        if (!first) {
            throw new AssertionError("第一次关单应该成功");
        }
        if (second) {
            throw new AssertionError("第二次关单应该失败（重复关单）");
        }
        if (stockBefore == null || stockAfter == null) {
            throw new AssertionError("库存 key 不存在：" + STOCK_KEY);
        }
        if (Integer.parseInt(stockAfter) != Integer.parseInt(stockBefore) + 1) {
            throw new AssertionError("库存被重复回补了！"
                    + stockBefore + "->" + stockAfter +"(应该只+1)");
        }
        System.out.println("重复关单只回补一次");
    }
}
