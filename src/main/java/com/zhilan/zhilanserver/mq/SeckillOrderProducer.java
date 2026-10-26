package com.zhilan.zhilanserver.mq;

import com.zhilan.zhilanserver.config.RabbitMQConfig;
import com.zhilan.zhilanserver.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 秒杀订单消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendSeckillOrder(SeckillOrderMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                message);

        log.info("[MQ] 已发送秒杀订单消息 voucherId={}, userId={}",
                message.getVoucherId(), message.getUserId());
    }
}
