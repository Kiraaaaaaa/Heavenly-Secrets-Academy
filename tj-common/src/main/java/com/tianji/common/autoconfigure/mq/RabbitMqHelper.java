package com.tianji.common.autoconfigure.mq;

import cn.hutool.core.lang.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;

@Slf4j
public class RabbitMqHelper {

    private final RabbitTemplate rabbitTemplate;
    private final MessagePostProcessor processor = new BasicIdMessageProcessor();

    public RabbitMqHelper(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 根据exchange和routingKey发送消息
     */
    public <T> void send(String exchange, String routingKey, T t) {
        log.debug("准备发送消息，exchange：{}， RoutingKey：{}， message：{}", exchange, routingKey,t);
        // 1.设置消息标示，用于消息确认，消息发送失败直接抛出异常，交给调用者处理
        String id = UUID.randomUUID().toString(true);
        CorrelationData correlationData = new CorrelationData(id);
        // 2.设置发送超时时间为500毫秒
        rabbitTemplate.setReplyTimeout(500);
        // 3.发送消息，同时设置消息id
        rabbitTemplate.convertAndSend(exchange, routingKey, t, processor, correlationData);
    }

    /**
     * 根据exchange和routingKey发送消息，并且可以设置延迟时间
     */
    public <T> void sendDelayMessage(String exchange, String routingKey, T t, Duration delay) {
        // 1.设置消息标示，用于消息确认，消息发送失败直接抛出异常，交给调用者处理
        String id = UUID.randomUUID().toString(true);
        CorrelationData correlationData = new CorrelationData(id);
        // 2.设置发送超时时间为500毫秒
        rabbitTemplate.setReplyTimeout(500);
        // 3.发送消息，同时设置消息id
        rabbitTemplate.convertAndSend(exchange, routingKey, t, new DelayedMessageProcessor(delay), correlationData);
    }
}
