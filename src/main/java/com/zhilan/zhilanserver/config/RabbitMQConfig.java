package com.zhilan.zhilanserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明秒杀相关交换机、队列、绑定
 */
@Configuration
public class RabbitMQConfig {

    /** 秒杀交换机 */
    public static final String SECKILL_EXCHANGE = "seckill.exchange";

    /** 秒杀订单队列 */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";

    /** 路由键 */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    /**
     * 直连交换机（Direct）:按 routing key 精准匹配投递
     */
    @Bean
    public DirectExchange seckillExchange() {
        //参数：名称， durable（是否持久化）， autoDelete（无队列绑定时是否自动删除）
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    /**
     * 订单队列：durable=true 保证 RabbitMQ 重启后队列还在
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE).build();
    }

    /**
     * 绑定：把队列绑到交换机，指定 routing key
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    /**
     * 消息转换器：把 Java 对象转成 JSON 传输
     * 注意：要注入 Spring Boot 的 ObjectMapper，否则 LocalDateTime 会序列化失败
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
