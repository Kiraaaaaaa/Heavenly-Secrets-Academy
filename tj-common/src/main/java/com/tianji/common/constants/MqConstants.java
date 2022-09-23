package com.tianji.common.constants;

public interface MqConstants {
    interface Exchange{
        /*课程有关的交换机*/
        String COURSE_EXCHANGE = "course.topic";

        /*订单有关的交换机*/
        String ORDER_EXCHANGE = "order.topic";

        /*学习有关的交换机*/
        String LEARNING_EXCHANGE = "learning.topic";

        /*信息中心短信相关的交换机*/
        String SMS_EXCHANGE = "sms.direct";

        /*异常信息的交换机*/
        String ERROR_EXCHANGE = "error.topic";

        /*支付有关的交换机*/
        String PAY_EXCHANGE = "pay.topic";
        /*交易服务延迟任务交换机*/
        String TRADE_DELAY_EXCHANGE = "trade.delay.topic";
    }
    interface Queue {
        String ERROR_QUEUE_TEMPLATE = "error.{}.queue";
    }
    interface Key{
        /*课程有关的 RoutingKey*/
        String COURSE_NEW_KEY = "course.new";
        String COURSE_UP_KEY = "course.up";
        String COURSE_DOWN_KEY = "course.down";
        String COURSE_EXPIRE_KEY = "course.expire";
        String COURSE_DELETE_KEY = "course.delete";

        /*订单有关的RoutingKey*/
        String ORDER_PAY_KEY = "order.pay";
        String ORDER_REFUND_KEY = "order.refund";

        /*点赞互动问答的RoutingKey*/
        String LIKE_REPLY_KEY = "reply.like";
        String UNLIKE_REPLY_KEY = "reply.unlike";
        String WRITE_REPLY = "reply.new";

        /*签到有关key*/
        String SIGN_IN = "sign.in";

        /*学习有关key*/
        String LEARN_SECTION = "section.learned";

        /*笔记*/
        String WRITE_NOTE = "note.new";
        String NOTE_GATHERED = "note.gathered";

        /*短信系统发送短信*/
        String SMS_MESSAGE = "sms.message";

        /*异常RoutingKey的前缀*/
        String ERROR_KEY_PREFIX = "error.";
        String DEFAULT_ERROR_KEY = "error.#";

        /*支付有关的key*/
        String PAY_SUCCESS = "pay.success";
        String REFUND_CHANGE = "refund.status.change";

        String ORDER_DELAY_KEY = "delay.order.query";
    }
}
