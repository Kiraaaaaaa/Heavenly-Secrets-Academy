package com.tianji.learning.task;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

@Component
@RequiredArgsConstructor
@Slf4j
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    // @Scheduled(cron = "0 0 3 1 * ?") // 每月1号，凌晨3点执行
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        log.debug("定时任务启动->创建上个赛季积分排行表");
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.在赛季表中查询上赛季id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            // 赛季不存在
            return;
        }
        // 3.创建赛季排行表
        pointsBoardService.createPointsBoardTableBySeason(season);
    }
}