package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;
@RequiredArgsConstructor
@Component
@Slf4j
public class LikedRecordListener {
    private final IInteractionReplyService replyService;

    /**
     * 监听回答或评论的点赞数变更，并更新数据库
     * @param dtos 被更新过的业务集合
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(name = LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = QA_LIKED_TIMES_KEY))
    public void listenReplyLikedTimesChange(List<LikedTimesDTO> dtos){
        log.debug("监听到回答或评论的点赞数变更:{}", dtos);
        ArrayList<InteractionReply> list = new ArrayList<>();
        for (LikedTimesDTO dto : dtos) {
            InteractionReply reply = new InteractionReply();
            reply.setId(dto.getBizId());
            reply.setLikedTimes(dto.getLikedTimes());
            list.add(reply);
        }
        //批量更新到数据库
        replyService.updateBatchById(list);
    }
}
