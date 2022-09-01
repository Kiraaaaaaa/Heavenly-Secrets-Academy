package com.tianji.search.mq;

import com.tianji.search.enums.CourseStatus;
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
            value = @Queue(name = "queue.course.new", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_NEW_KEY
    ))
    public void listenCourseNew(Long courseId){
        courseService.handleNewCourse(courseId);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue.course.up", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_UP_KEY
    ))
    public void listenCourseUp(Long courseId){
        courseService.handleCourseUp(courseId);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue.course.down", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_DOWN_KEY
    ))
    public void listenCourseDown(Long courseId){
        courseService.handleCourseStatus(courseId, CourseStatus.NO_LONGER_BE_SOLD);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue.course.expire", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_EXPIRE_KEY
    ))
    public void listenCourseExpire(Long courseId){
        courseService.handleCourseStatus(courseId, CourseStatus.EXPIRED);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue.course.del", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_DELETE_KEY
    ))
    public void listenCourseDelete(Long courseId){
        courseService.handleCourseDelete(courseId);
    }

}
