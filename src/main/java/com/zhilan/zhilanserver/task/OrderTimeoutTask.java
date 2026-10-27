package com.zhilan.zhilanserver.task;

import com.zhilan.zhilanserver.entity.VoucherOrder;
import com.zhilan.zhilanserver.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final VoucherOrderService voucherOrderService;

    /** 超时时长（分钟），从配置读取，默认 15  */
    @Value("${zhilan.order.timeout-minutes:15}")
    private int timeoutMinutes;

    /**
     * 每分钟的第 0 秒执行一次
     * cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void closeTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<VoucherOrder> overdue = voucherOrderService.listOverdueOrders(deadline,100);
        if (overdue.isEmpty()) {
            return;            // 没有超时订单，直接返回（避免刷日志）
        }

        log.info("[定时关单] 发现 {} 个超时订单（超时阈值 {} 分钟）", overdue.size(), timeoutMinutes);

        int successCount = 0;
        for (VoucherOrder order : overdue) {
            try {
                // 每单单独处理：一单失败不影响其他单
                if (voucherOrderService.closeOrder(order.getId())) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("[定时关单] 关闭订单 {} 失败", order.getId(), e);
            }
        }

        log.info("[定时关单] 本轮处理完成：成功 {}/{}", successCount, overdue.size());
    }
}
