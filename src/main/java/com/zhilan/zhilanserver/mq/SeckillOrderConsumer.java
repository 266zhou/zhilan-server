package com.zhilan.zhilanserver.mq;

import com.rabbitmq.client.Channel;
import com.zhilan.zhilanserver.config.RabbitMQConfig;
import com.zhilan.zhilanserver.dto.SeckillOrderMessage;
import com.zhilan.zhilanserver.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀订单消息消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final VoucherOrderService voucherOrderService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(SeckillOrderMessage message, Channel channel, Message amqpMessage)
        throws IOException {

        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("[MQ] 收到订单消息 voucherId={}, userId={}",
                message.getVoucherId(),message.getUserId());

        try {
            voucherOrderService.createOrderFromMq(message);
            //处理成功 → 告诉 RabbitMQ "这条我处理完了，可以删了"
            channel.basicAck(deliveryTag,false);
        } catch (DuplicateKeyException e) {
            // 重复消费 / 用户已经下过单 → 幂等，直接确认，不重试
            log.warn("[MQ] 重复订单，忽略 voucherId={}, userId={}",
                    message.getVoucherId(),message.getUserId());
            channel.basicAck(deliveryTag,false);
        } catch (Exception e) {
            log.error("[MQ] 处理订单失败 voucherId={}, userId={}",
                    message.getVoucherId(),message.getUserId());
            // 拒绝消息，requeue=false 表示不重新入队（避免无限重试打爆日志）
            channel.basicNack(deliveryTag,false,false);
        }
    }
}
