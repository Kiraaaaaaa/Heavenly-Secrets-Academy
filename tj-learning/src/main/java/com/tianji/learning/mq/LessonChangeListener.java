package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;

    /**
     * 监听订单支付或课程报名的消息
     * @param order 订单信息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order){
        // 1.健壮性处理
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            // 数据有误，无需处理
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }
        // 2.添加课程
        log.debug("监听到用户{}的订单{}，需要添加课程{}到课表中", order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.addUserLessons(order.getUserId(), order.getCourseIds());
    }
    /**
     * 监听订单退款的消息
     * @param order 订单信息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "trade.refund.result.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO order){
        // 1.健壮性处理
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            // 数据有误，无需处理
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }
        // 2.删除课程表中删除该课程
        log.debug("监听到用户{}的订单{}，需要添加课程{}到课表中", order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.deleteCourseFromLesson(order.getUserId(), order.getCourseIds().get(0));
    }
}