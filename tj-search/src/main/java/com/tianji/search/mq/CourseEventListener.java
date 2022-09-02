package com.tianji.search.mq;

import com.tianji.search.service.ICourseService;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.tianji.common.constants.MqConstants.Exchange.COURSE_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.*;

@Component
public class CourseEventListener {

    @Autowired
    private ICourseService courseService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "course.up.queue", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_UP_KEY
    ))
    public void listenCourseUp(Long courseId){
        courseService.handleCourseUp(courseId);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "course.down.queue", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_DOWN_KEY
    ))
    public void listenCourseDown(Long courseId){
        courseService.handleCourseDelete(courseId);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "course.expire.queue", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_EXPIRE_KEY
    ))
    public void listenCourseExpire(Long courseId){
        courseService.handleCourseDelete(courseId);
    }
}
