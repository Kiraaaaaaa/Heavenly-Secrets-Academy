package com.tianji.message.handler;

import com.tianji.api.dto.sms.SmsInfoDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.message.service.ISmsService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SmsMessageHandler {

    @Autowired
    private ISmsService smsService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sms.message.queue", durable = "true"),
            exchange = @Exchange(MqConstants.Exchange.SMS_EXCHANGE),
            key = MqConstants.Key.SMS_MESSAGE
    ))
    public void listenSmsMessage(SmsInfoDTO smsInfoDTO){
        smsService.sendMessage(smsInfoDTO);
    }
}
