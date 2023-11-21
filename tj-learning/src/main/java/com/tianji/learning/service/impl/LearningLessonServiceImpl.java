package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LessonStatusVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-11-20
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper recordMapper;

    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //1.查询课程过期时间
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cinfos)){
            log.error("课程列表为空，无法添加到课表");
            return;
        }
        //2.保存课程id-用户id-课程过期时间到课程表
        List<LearningLesson> collect = cinfos.stream().map((cinfo) -> {
            LearningLesson learningLesson = new LearningLesson();
            learningLesson.setCourseId(cinfo.getId());
            learningLesson.setUserId(userId);
            LocalDateTime now = LocalDateTime.now();
            //用数据库时间可能有一点时间偏差，所以使用系统当前时间
            learningLesson.setCreateTime(now);
            //过期时间=当前时间+课程有效时间
            learningLesson.setExpireTime(now.plusMonths(cinfo.getValidDuration()));
            return learningLesson;
        }).collect(Collectors.toList());
        //3.批量保存课程到课程表
        saveBatch(collect);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //1.查询用户信息
        Long user = UserContext.getUser();
        //2.查询分页结果
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                //前端不传排序条件默认按照最新学习课程倒序
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        //如果该用户没有课程则直接返回一个空记录的分页结果
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3.远程查询课程信息, 返回<课程id， 课程信息>
        Map<Long, CourseSimpleInfoDTO> infoDTOS = queryCourseSimpleInfoList(records);
        //4.设置LearningLesson到LearningLessonVO，添加额外属性
        List<LearningLessonVO> collect = records.stream().map(learningLesson -> {
            LearningLessonVO vo = BeanUtils.copyBean(learningLesson, LearningLessonVO.class);
            //设置远程调用查出的课程信息(课程名称、课程图片、课程章节)
            CourseSimpleInfoDTO infoDTO = infoDTOS.get(learningLesson.getCourseId());
            vo.setCourseName(infoDTO.getName());
            vo.setCourseCoverUrl(infoDTO.getCoverUrl());
            vo.setSections(infoDTO.getSectionNum());
            return vo;
        }).collect(Collectors.toList());
        //5.返回LearningLessonVO集合
        return PageDTO.of(page, collect);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //1.获取当前用户
        Long user = UserContext.getUser();
        //2.查询正在学习的课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(lesson == null){
            return null;
        }
        //3.查询该课程信息
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if(cinfo == null){
            throw new BizIllegalException("该课程不存在");
        }
        //4.统计用户课程数量
        Integer count = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .count();
        //5.查询正在学习课程的小结信息
        //由于batchQueryCatalogue是批量查询课程小结ids，并返回小结信息集合
        //所以使用singletonList可以将单个对象转为list集合，优点：无需创建初始化数组大小为10的ArrayList，节省空间
        List<CataSimpleInfoDTO> cataInfos = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        //6.设置vo
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        //设置课程基本信息
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setSections(cinfo.getSectionNum());
        //设置最新小结信息
        if(CollUtils.isNotEmpty(cataInfos)){
            CataSimpleInfoDTO cataSimpleInfoDTO = cataInfos.get(0);
            vo.setLatestSectionName(cataSimpleInfoDTO.getName());
            vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());
        }
        //设置有几门课程
        vo.setCourseAmount(count);
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        Long user = UserContext.getUser();
        if(user==null || courseId==null){
            return null;
        }
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        //用户不存在该课程
        if(lesson==null){
            return null;
        }
        //该课程非永久有效且已经过期
        if(lesson.getExpireTime() != null && LocalDateTime.now().isAfter(lesson.getExpireTime())){
            return  null;
        }
        return lesson.getId();
    }

    /**
     * 手动删除&退款删除课程
     * @param userId 用户id
     * @param courseId 课程id
     */
    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        remove(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    @Override
    public LessonStatusVO queryLessonByCourseId(Long couseId) {
        //1.获取用户信息
        Long user = UserContext.getUser();
        //2.查询用户是否拥有该课程
        LambdaQueryWrapper<LearningLesson> wrapper = buildUserIdAndCourseIdWrapper(user, couseId);
        LearningLesson lesson = getOne(wrapper);
        if(lesson==null){
            return null;
        }
        //3.如果有该课程则设置用户学习该课程的进度
        LessonStatusVO vo = BeanUtils.copyBean(lesson, LessonStatusVO.class);

        return vo;
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        Integer count = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count();
        return count;
    }

    //远程查询课程信息
    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        //1.获取课程ids
        Set<Long> ids = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //2.远程查询ids对应的课程信息集合
        List<CourseSimpleInfoDTO> infoList = courseClient.getSimpleInfoList(ids);
        if(CollUtils.isEmpty(infoList)){
            //没有课程信息如果不存在就无法添加
            throw new BizIllegalException("课程信息不存在");
        }

        //3.将查询的结果转化为<id, CourseSimpleInfoDTO>结构
        //原因：如果不转换
        //List<LearningLesson>复制到List<LearningLessonVo>时，还要加for循环查出LearningLesson对应CourseSimpleInfoDTO
        Map<Long, CourseSimpleInfoDTO> collect = infoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return collect;
    }

    //根据用户id和课程id查询lesson
    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userId, courseId));

    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        Long user = UserContext.getUser();
        LearningLesson lesson = queryByUserAndCourseId(user, courseId);
        if(lesson == null){
            throw new BizIllegalException("课表中没有该课程");
        }
        boolean update = this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, freq)
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
        if(!update){
            throw new DbException("课表更新失败");
        }
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO planPageVO = new LearningPlanPageVO();
        //1.获取用户
        Long user = UserContext.getUser();
        //2.todo 积分奖励
        //3.查询课程表信息
        //3.1获取本周内时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.beginOfDay(now);
        LocalDateTime end = DateUtils.endOfDay(now);
        //3.2获取本周已学习小节
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, user)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
        );
        planPageVO.setWeekFinished(weekFinished);
        //3.3获取用户所有计划数量
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("SUM(week_freq) AS plansTotal");
        wrapper.eq("user_id", user);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = getMap(wrapper);
        if(map != null && map.get("plansTotal") != null){
            //得到结果
            Object plansTotal = map.get("plansTotal");
            //SUM类型默认为BigDecimal，所以需要转换为int
            Integer plans = Integer.valueOf(plansTotal.toString());
            planPageVO.setWeekTotalPlan(plans);
        }
        //3.4查询所有有计划的课程
        Page<LearningLesson> lessonPage = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time", false));
        //如果没有计划的课程返回一个空分页
        if(CollUtils.isEmpty(lessonPage.getRecords())){
            planPageVO.setTotal(0L);
            planPageVO.setPages(0L);
            planPageVO.setList(CollUtils.emptyList());
            return planPageVO;
        }
        //4.查询课程信息

        //5.获取小节信息
        //6.封装vo

        return null;
    }

    //生成userId对应courseId的wrapper，供查询lesson使用
    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId){
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<LearningLesson>()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        return wrapper;
    }

}
