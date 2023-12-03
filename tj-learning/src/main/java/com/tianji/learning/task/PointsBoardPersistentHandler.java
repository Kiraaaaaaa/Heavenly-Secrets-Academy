package com.tianji.learning.task;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;
import static com.tianji.learning.constants.RedisConstants.POINTS_BORAD_KEY_PREFIX;

@Component
@RequiredArgsConstructor
@Slf4j
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;

    // 将上个赛季的积分排行表创建出来
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

    // 从Redis中将上赛季积分排行表的数据同步到数据库（XxlJob分片广播执行）
    /**
     * 如果不进行分片操作，不管是轮询还是其它方式都只会让一台机器执行同步数据库的操作
     * 所以需要分片执行，假如有20条数据，两台实例，每台实例一次插入5条数据
     * 两台实例各自都会被调度两次，这样并发量就会提高
     */
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        log.debug("定时任务启动->从Redis中将上赛季积分排行表的数据同步到数据库");
        // 1. 获取上月时间
        LocalDateTime now = LocalDateTime.now().minusMonths(1);
        // 2. 从数据库points_season_board表获取上赛季id
        Integer season = seasonService.querySeasonByTime(now);
        // 3. 分页获取该赛季的积分排行信息
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        //分页页码，由于分片广播，页码就是当前实例的坐标0加1;
        int pageNo = shardIndex + 1;
        //每一页查找并保存5条到数据库
        int pageSize = 5;
        while(true){
            log.info("处理第{}页数据", pageNo);
            List<PointsBoard> pointsBoards = pointsBoardService.queryCurrentPoints(pageNo, pageSize, true);
            if(CollUtils.isEmpty(pointsBoards)){
                break;
            }
            // 4. 持久化该赛季信息到数据库
            List<PointsBoard> collect = pointsBoards.stream().map(i -> {
                i.setId(Long.valueOf(i.getRank())); //在数据库中，id字段就为rank字段
                i.setRank(null); //数据库表中没有该字段，需要清空
                return i;
            }).collect(Collectors.toList());
            // save之前执行动态替换表名
            // 将新表名存入threadLocal中，等待动态替换
            // 一定要把setInfo放在所有crud操作前面
            String tableName = POINTS_BOARD_TABLE_PREFIX + season;
            TableInfoContext.setInfo(tableName);
            log.info("待插入的动态表名为{}", tableName);
            pointsBoardService.saveBatch(collect);
            //第一轮结束，开始第二轮，页码为 当前页码 + 页跨度
            pageNo += shardTotal;
        }
        // 5. 清空threadLocal中的数据
        TableInfoContext.remove();
    }

    /**
     * 清除Redis上个月保存的排行榜
     */
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BORAD_KEY_PREFIX + format;
        // 3.删除
        redisTemplate.unlink(key);
    }
}