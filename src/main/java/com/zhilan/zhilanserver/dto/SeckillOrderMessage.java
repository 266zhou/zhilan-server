package com.zhilan.zhilanserver.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀订单消息（放进 RabbitMQ 的内容）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

    private Long voucherId;

    private Long userId;
}
