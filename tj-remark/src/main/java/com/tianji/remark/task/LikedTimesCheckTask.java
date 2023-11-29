package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    // 业务类型
    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE");
    // 每次发送30个
    private static final int MAX_BIZ_SIZE = 30;

    private final ILikedRecordService recordService;

    /**
     * 每20秒检查一次点赞次数，每个业务每次发送30个
     *
     */
    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes(){
        for (String bizType : BIZ_TYPES) {
            recordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }
}
