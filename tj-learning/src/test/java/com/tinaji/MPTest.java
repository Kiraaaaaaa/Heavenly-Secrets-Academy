package com.tinaji;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(classes = LearningApplication.class)
public class MPTest {

    @Autowired
    ILearningLessonService lessonService;

    @Autowired
    ILearningRecordService recordService;


    /**
     * 几种分页查询写法比较
     *
     */
    @Test
    //mp分页功能常规写法测试
    public void test(){
        Page<LearningLesson> page = new Page<>(1, 2);
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId, "2");
        wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.page(page, wrapper);
        System.out.println(page.getPages());
        System.out.println(page.getTotal());
        for (LearningLesson record : page.getRecords()) {
            System.out.println(record.toString());
        }
    }

    @Test
    //mp分页功能使用orderItem + wrapper写法测试
    public void test1(){
        Page<LearningLesson> page = new Page<>(1, 2);
        ArrayList<OrderItem> orderItems = new ArrayList<>();
        OrderItem orderItem = new OrderItem();
        //以最新学习课程时间倒叙
        orderItem.setColumn("latest_learn_time");
        orderItem.setAsc(false);
        orderItems.add(orderItem);
        page.addOrder(orderItems);

        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId, "2");
        // 不用wrapper构造排序条件而是由mp提供的orderItem构造
        // wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.page(page, wrapper);


        System.out.println(page.getPages());
        System.out.println(page.getTotal());
        for (LearningLesson record : page.getRecords()) {
            System.out.println(record.toString());
        }
    }

    @Test
    //mp分页功能使用orderItem + 链式调用写法测试
    public void test2(){
        Page<LearningLesson> page = new Page<>(1, 2);
        ArrayList<OrderItem> orderItems = new ArrayList<>();
        OrderItem orderItem = new OrderItem();
        //以最新学习课程时间倒叙
        orderItem.setColumn("latest_learn_time");
        orderItem.setAsc(false);
        orderItems.add(orderItem);
        page.addOrder(orderItems);

        // 不用wrapper了
        // LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        // wrapper.eq(LearningLesson::getUserId, "2");
        // lessonService.page(page, wrapper);

        //使用lambdaQuery，链式调用eq、page
        lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, "2")
                .page(page);

        System.out.println(page.getPages());
        System.out.println(page.getTotal());
        for (LearningLesson record : page.getRecords()) {
            System.out.println(record.toString());
        }
    }

    @Test
    //天机项目默认写法
    //mp分页功能使用PageQuery + 链式调用写法测试
    //PageQuery是本项目自己封装的对象，原理还是构建orderItem
    public void test3(){
        // Page<LearningLesson> page = new Page<>(1, 2);
        // ArrayList<OrderItem> orderItems = new ArrayList<>();
        // OrderItem orderItem = new OrderItem();
        // //以最新学习课程时间倒叙
        // orderItem.setColumn("latest_learn_time");
        // orderItem.setAsc(false);
        // orderItems.add(orderItem);
        // page.addOrder(orderItems);

        //使用自己封装的page对象
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(1);
        pageQuery.setPageSize(2);
        pageQuery.setIsAsc(false);
        pageQuery.setSortBy("latest_learn_time");

        //注意这里与其它不同的是需要返回一个page
        Page<LearningLesson> page = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, "2")
                //这两个参数值如果前端传的为空，就使用这里默认的排序规则
                .page(pageQuery.toMpPage("latest_learn_time", false));

        System.out.println(page.getPages());
        System.out.println(page.getTotal());
        for (LearningLesson record : page.getRecords()) {
            System.out.println(record.toString());
        }
    }

    /**
     * 1.测试stream流获取课程列表ids
     * 2.测试stream流生成课程id对应课程对象{id, LearningLesson}
     */
    @Test
    public void test4(){
        //初始化一个课程列表
        List<LearningLesson> list = new ArrayList<>();
        LearningLesson lesson1 = new LearningLesson();
        lesson1.setId(1L);
        lesson1.setCourseId(1L);
        LearningLesson lesson2 = new LearningLesson();
        lesson2.setId(2L);
        lesson2.setCourseId(2L);
        list.add(lesson1);
        list.add(lesson2);
        //1.测试stream流获取课程列表ids
        List<Long> ids = list.stream().map(LearningLesson::getId).collect(Collectors.toList());
        //2.测试stream流生成课程id对应课程对象{id, LearningLesson}
        Map<Long, LearningLesson> lessonMap = list.stream().collect(Collectors.toMap(LearningLesson::getId, c -> c));
        System.out.println(lessonMap);
    }

    /**
     * mabatis-plus聚合查询测试
     * records表查询用户的每周计划总数
     */
    @Test
    public void test5(){
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("SUM(week_freq) AS plansTotal");
        wrapper.eq("user_id", 2);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = lessonService.getMap(wrapper);
        //得到结果
        Object plansTotal = map.get("plansTotal");
        //SUM类型默认为BigDecimal，所以需要转换为int

        //转换方式1
        int i = new BigDecimal(plansTotal.toString()).intValue();
        System.out.println("转换方式1："+i);

        //转换方式2
        BigDecimal plansTotal1 = (BigDecimal) plansTotal;
        int i1 = plansTotal1.intValue();
        System.out.println("转换方式2："+i1);

        //转换方式3
        Integer integer = Integer.valueOf(plansTotal.toString());
        System.out.println("转换方式3："+integer);
    }

    /**
     * 以下两种写法都不能[一次性]实现List<课表id, 本周完成小节数量>
     * 即 recordService无法实现这样先分组然后统计分组后数量的需求
     */
    @Test
    public void test6(){
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        // 方式1
        // List<LearningRecord> list = recordService.lambdaQuery()
        //         .select(LearningRecord::getLessonId, LearningRecord::getUserId, )
        //         .eq(LearningRecord::getUserId, 2)
        //         .eq(LearningRecord::getFinished, true)
        //         .eq(LearningRecord::getLessonId, 1)
        //         .gt(LearningRecord::getFinishTime, begin)
        //         .lt(LearningRecord::getFinishTime, end)
        //         .groupBy(LearningRecord::getLessonId, LearningRecord::getUserId)
        //         .list();

        // 方式2
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.select("lesson_id as id", "user_id", "count(1) as num");
        wrapper.eq("user_id", 2);
        wrapper.eq("finished ", 1);
        wrapper.gt("finish_time", begin);
        wrapper.lt("finish_time", end);
        wrapper.groupBy("lesson_id", "user_id");
        //只能返回LearningRecord对象的集合
        List<LearningRecord> list = recordService.list(wrapper);
        //只能返回一条数据
        //recordService.getMap();
        System.out.println(list);
    }
    /**
     * 这两种方式可以实现上述需求
     * 如果先查出所有小节记录，然后调用stream的Collectors.groupingBy()和Collectors.counting()
     * 这里用了baseMapper和recordService分别查出list
     */
    @Test
    public void test7(){
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        //方式1，使用recordService
        Map<Long, Long> collect = recordService.lambdaQuery()
                .eq(LearningRecord::getUserId, 2)
                .eq(LearningRecord::getFinished, 1)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
                .list()
                .stream().collect(Collectors.groupingBy(LearningRecord::getLessonId, Collectors.counting()));
        // Map<Long, Long> collect = list.stream().collect(Collectors.groupingBy(LearningRecord::getLessonId, Collectors.counting()));
        System.out.println("方式1");
        System.out.println(collect);

        //方式2，使用getBaseMapper
        Map<Long, Long> collect1 = recordService.getBaseMapper().selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, 2)
                        .eq(LearningRecord::getFinished, 1)
                        .gt(LearningRecord::getFinishTime, begin)
                        .lt(LearningRecord::getFinishTime, end)
        ).stream().collect(Collectors.groupingBy(LearningRecord::getLessonId, Collectors.counting()));
        System.out.println("方式2");
        System.out.println(collect1);
    }
}
