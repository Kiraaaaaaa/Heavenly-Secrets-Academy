package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

/**
 * 延迟阻塞队列工具
 * 主要包含方法：
 *  + 读取Redis中学习记录
 *  + 添加播放记录到Redis，并添加一个延迟检测任务到DelayQueue
 *  + 删除Redis缓存中的指定小节的播放记录
 *  + 异步执行DelayQueue中的延迟检测任务，检测播放进度是否变化，如果无变化则写入数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private static volatile boolean begin = true;
    //PostConstruct：在此类初始化后，并在属性被输入之后执行
    @PostConstruct
    //使用该工具类会创建一个新线程，来执行handleDelayTask方法
    public void init(){
            CompletableFuture.runAsync(this::handleDelayTask);
    }
    //PreDestroy：在此类实例销毁前设置destroy为false，使其延时队列任务监测方法停止，否则用户断开连接后，在新线程还会一直执行handleDelayTask
    @PreDestroy
    public void destroy(){
        log.debug("关闭学习记录处理的延迟任务");
        begin = false;
    }

    //由新线程开启的延时队列任务监测
    private void handleDelayTask(){
        while (begin){
            try {
                // 1.尝试获取任务
                DelayTask<RecordTaskData> task = queue.take();
                log.debug("获取到要处理的播放记录任务");
                RecordTaskData data = task.getData(); //由于是阻塞队列(不会返回null)，拿不到元素就会阻塞在这里一直等
                // 2.读取Redis缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                log.info("从延迟队列获取到学习记录数据:{}", data);
                log.info("从Redis缓存中获取到学习记录数据:{}", record);
                if (record == null) {
                    continue;
                }
                // 3.比较数据
                if(!Objects.equals(data.getMoment(), record.getMoment())){
                    // 4.如果不一致，播放进度在变化，无需持久化
                    continue;
                }
                // 5.如果一致，证明用户离开了视频，需要持久化
                // 5.1.更新学习记录
                record.setFinished(null);
                recordMapper.updateById(record);
                // 5.2.更新课表
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);

                log.debug("准备持久化学习记录信息");
            } catch (Exception e) {
                log.error("处理播放记录任务发生异常", e);
            }
        }
    }

    /**
     * 添加学习记录到Redis并提交到延时队列(异步)
     * @param record 学习记录
     */
    public void addLearningRecordTask(LearningRecord record){
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    /**
     * 将学习记录存储到Redis
     * @param record 学习记录对象{课表id, 小节id, 播放时长}
     */
    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2.写入Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 3.添加Redis的缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常", e);
        }
    }

    /**
     * 读取Redis中学习记录
     * @param lessonId 课表id
     * @param sectionId 小节id
     * @return LearningRecord 学习记录
     */
    public LearningRecord readRecordCache(Long lessonId, Long sectionId){
        try {
            // 1.读取Redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            // 2.数据检查和转换
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常", e);
            return null;
        }
    }

    /**
     * 删除Redis中已完成的学习记录缓存
     * @param lessonId 课表id
     * @param sectionId 小节id
     */
    public void cleanRecordCache(Long lessonId, Long sectionId){
        // 删除数据
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }

    /**
     * Redis中保存的学习记录对象
     * 该对象与Redis中存储的学习记录属性保持一致
     */
    @Data
    @NoArgsConstructor
    private static class RecordCacheData{
        private Long id; //小节id，不需要lessonId，lessonId是作为key保存在redis中
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    /**
     * 延迟阻塞队列任务对象中保存的学习记录对象
     * 该对象和上面的RecordCacheData有何不同？
     * + 该任务对象需要有lessonId方便查找再次redis的记录进行对比
     * + 没有finished字段，此对象只是拿来和redis中的记录比较moment值
     */
    @Data
    @NoArgsConstructor
    private static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}
