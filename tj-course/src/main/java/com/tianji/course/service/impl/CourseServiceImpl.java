package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.*;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.course.constants.CourseErrorInfo;
import com.tianji.course.domain.dto.CourseSimpleInfoListDTO;
import com.tianji.course.domain.po.*;
import com.tianji.course.domain.query.CoursePageQuery;
import com.tianji.course.domain.vo.ChapterVO;
import com.tianji.course.domain.vo.CourseAndSectionVO;
import com.tianji.course.domain.vo.CoursePageVO;
import com.tianji.course.domain.vo.SectionVO;
import com.tianji.course.mapper.CourseDraftMapper;
import com.tianji.course.mapper.CourseMapper;
import com.tianji.course.mapper.CourseTeacherMapper;
import com.tianji.course.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * <p>
 * 草稿课程 服务实现类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-20
 */
@Service
@Slf4j
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {


    @Autowired
    private CourseTeacherMapper courseTeacherMapper;

    @Autowired
    private CourseDraftMapper courseDraftMapper;

    @Autowired
    private ICourseDraftService courseDraftService;

    @Autowired
    private ISubjectService subjectService;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private RabbitMqHelper rabbitMqHelper;

    @Autowired
    private ICourseCatalogueService courseCatalogueService;

    @Autowired
    private ICourseTeacherService courseTeacherService;

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private UserClient userClient;

    @Autowired
    private TradeClient tradeClient;

    @Autowired
    private ExamClient examClient;

    @Autowired
    private LearningClient learningClient;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public void updateStatus(Long id, Integer status) {
        Course course = new Course();
        course.setId(id);
        course.setStatus(status);
        course.setUpdateTime(LocalDateTime.now());
        int result = baseMapper.updateById(course);
        if (result != 1) {
            throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
        }
    }

    @Override
    public CourseSearchDTO getCourseDTOById(Long id) {
        // 1.查询课程信息
        Course course = baseMapper.selectById(id);
        if (course == null) {
            return null;
        }
        LambdaQueryWrapper<CourseTeacher> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacher::getCourseId, id)
                .orderByDesc(CourseTeacher::getCIndex)
                .last(true, "limit 1");
        // 2.查询教师信息
        List<CourseTeacher> courseTeachers = courseTeacherMapper.selectList(queryWrapper);

        // 3.数据封装
        CourseSearchDTO courseSearchDTO = BeanUtils.toBean(course, CourseSearchDTO.class);
        courseSearchDTO.setCategoryIdLv1(course.getFirstCateId());
        courseSearchDTO.setCategoryIdLv2(course.getSecondCateId());
        courseSearchDTO.setCategoryIdLv3(course.getThirdCateId());
        courseSearchDTO.setPublishTime(course.getCreateTime());
        courseSearchDTO.setSections(course.getSectionNum());
        if (CollUtils.isNotEmpty(courseTeachers)) {
            courseSearchDTO.setTeacher(courseTeachers.get(0).getTeacherId());
        } else {
            courseSearchDTO.setTeacher(0L);
        }

        // 4.统计课程销量
        Map<Long, Integer> peoNumOfCourseMap = tradeClient.countEnrollNumOfCourse(CollUtils.singletonList(id));
        if (CollUtils.isNotEmpty(peoNumOfCourseMap)) {
            courseSearchDTO.setSold(peoNumOfCourseMap.getOrDefault(id, 0));
        }
        // TODO 5.课程评分，先随机生成，后期再做数据统计
        courseSearchDTO.setScore(40 + RandomUtils.randomInt(10));
        return courseSearchDTO;

    }

    @Override
    public void delete(Long id) {
        //删除草稿信息
        CourseDraft courseDraft = courseDraftMapper.selectById(id);
        if (courseDraft != null) {
            courseDraftService.delete(id);
        }
        rabbitMqHelper.send(MqConstants.Exchange.COURSE_EXCHANGE, MqConstants.Key.COURSE_DELETE_KEY, id);
    }

    @Override
    public List<CourseSimpleInfoDTO> getSimpleInfoList(CourseSimpleInfoListDTO courseSimpleInfoListDTO) {

        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(CollUtils.isNotEmpty(courseSimpleInfoListDTO.getThirdCataIds()),
                Course::getThirdCateId, courseSimpleInfoListDTO.getThirdCataIds())
                .in(CollUtils.isNotEmpty(courseSimpleInfoListDTO.getIds()),
                        Course::getId, courseSimpleInfoListDTO.getIds());
        List<Course> courses = baseMapper.selectList(queryWrapper);
        return BeanUtils.copyList(courses, CourseSimpleInfoDTO.class);
    }

    public List<CoursePurchaseInfoDTO> checkCoursePurchase(List<Long> courseIds) {
        if (CollUtils.isEmpty(courseIds)) {
            return new ArrayList<>();
        }
        //获取 courseIds中所有的课程
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Course::getId, courseIds);
        List<Course> courses = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courses)) {
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_CHECK_NOT_FOUND);
        }
        if (courses.size() != courseIds.size()) {
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_CHECK_NOT_EXISTS);
        }
        //校验课程是否可以购买，并且返回课程信息

        return BeanUtils.copyList(courses, CoursePurchaseInfoDTO.class, (course, coursePurchaseInfo) -> {
            if (course.getStatus() == CourseStatus.DOWN_SHELF.getValue()) { //课程已经下架
                //课程已经下架
                throw new BizIllegalException(course.getName() + CourseErrorInfo.Msg.COURSE_CHECK_DOWN_SHELF);
            }
            if (course.getStatus() == CourseStatus.FINISHED.getValue() ||
                    course.getPurchaseEndTime().isBefore(LocalDateTime.now())) {
                //课程已经完结
                throw new BizIllegalException(course.getName() + CourseErrorInfo.Msg.COURSE_CHECK_FINISHED);
            }
            if (course.getPurchaseStartTime().isAfter(LocalDateTime.now())) {
                //课程还未开始
                throw new BizIllegalException(course.getName() + CourseErrorInfo.Msg.COURSE_CHECK_NO_SALE);
            }
        });
    }

    @Override
    public List<SubNumAndCourseNumDTO> countSubjectNumAndCourseNumOfTeacher(List<Long> teacherIds) {

        //老师id和课程数量(已上架、已下架、已过期)
        Map<Long, Integer> teacherIdAndCourseNumMap =
                IdAndNumDTO.toMap(baseMapper.countCourseNumOfTeacher(teacherIds));
        //待上架
        Map<Long, Integer> teacherIdAndCourseNumMap2 =
                IdAndNumDTO.toMap(courseDraftMapper.countCourseNumOfTeacher(teacherIds));


        //老师id和出题数量
        Map<Long, Integer> teacherIdAndSubjectNumMap = examClient.countSubjectNumOfTeacher(teacherIds);

        List<SubNumAndCourseNumDTO> subNumAndCourseNumDTOS = new ArrayList<>();
        //遍历老师id，并为每个老师设置出题数量和课程数量
        for (Long teacherId : teacherIds) {
            subNumAndCourseNumDTOS.add(new SubNumAndCourseNumDTO(
                    teacherId,
                    //课程数量 待上架的数量+ （已上架、已下架、已过期）
                    NumberUtils.null2Zero(teacherIdAndCourseNumMap.get(teacherId)) +
                            NumberUtils.null2Zero(teacherIdAndCourseNumMap2.get(teacherId)),
                    //出题数量
                    NumberUtils.null2Zero(teacherIdAndSubjectNumMap.get(teacherId))));
        }
        return subNumAndCourseNumDTOS;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int courseFinished() {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.le(Course::getPurchaseEndTime, LocalDateTime.now())
                .in(Course::getStatus, List.of(CourseStatus.DOWN_SHELF.getValue(), CourseStatus.SHELF.getValue()));
        List<Course> courses = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courses)) {
            return 0;
        }
        List<Course> updateCourses = new ArrayList<>();
        for (Course course : courses) {
            Course updateCourse = new Course();
            updateCourse.setId(course.getId());
            updateCourse.setStatus(CourseStatus.FINISHED.getValue());
            updateCourses.add(updateCourse);
        }

        updateBatchById(updateCourses);
        sendFinishedCourse(courses);
        return updateCourses.size();
    }

    @Override
    public Map<Long, Integer> countCourseNumOfCategory(List<Long> categoryIds) {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.or().in(Course::getFirstCateId, categoryIds)
                .or().in(Course::getSecondCateId, categoryIds)
                .or().in(Course::getThirdCateId, categoryIds);
        List<Course> courses = baseMapper.selectList(queryWrapper);
        Map<Long, Integer> cateIdAndNumMap = new HashMap<>();
        for (Course course : courses) {
            //一级分类数量
            Integer firstCateNum = cateIdAndNumMap.get(course.getFirstCateId());
            cateIdAndNumMap.put(course.getFirstCateId(), firstCateNum == null ? 1 : firstCateNum + 1);
            //二级分类数量
            Integer secondCateNum = cateIdAndNumMap.get(course.getSecondCateId());
            cateIdAndNumMap.put(course.getSecondCateId(), secondCateNum == null ? 1 : secondCateNum + 1);
            //三级分类数量够
            Integer thirdCateNum = cateIdAndNumMap.get(course.getThirdCateId());
            cateIdAndNumMap.put(course.getThirdCateId(), thirdCateNum == null ? 1 : thirdCateNum + 1);
        }
        return cateIdAndNumMap;
    }

    @Override
    public Integer countCourseNumOfCategory(Long categoryId) {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.or().eq(Course::getFirstCateId, categoryId)
                .or().eq(Course::getSecondCateId, categoryId)
                .or().eq(Course::getThirdCateId, categoryId);
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public CourseFullInfoDTO getInfoById(Long id, boolean withCatalogue, boolean withTeachers) {
        // 1.查询课程基本信息
        Course course = baseMapper.selectById(id);
        if (course == null) {
            throw new BadRequestException(CourseErrorInfo.Msg.COURSE_CHECK_NOT_EXISTS);
        }
        // 2.转换vo
        CourseFullInfoDTO courseFullInfoDTO = BeanUtils.toBean(course, CourseFullInfoDTO.class);

        // 3.查询目录信息
        if (withCatalogue) {
            courseFullInfoDTO.setChapters(courseCatalogueService.queryCourseCatalogues(id, true));
        }
        // 4.查询教师信息
        if (withTeachers) {
            courseFullInfoDTO.setTeacherIds(courseTeacherService.getTeacherIdOfCourse(id));
        }
        return courseFullInfoDTO;
    }

    @Override
    public PageDTO<CoursePageVO> queryForPage(CoursePageQuery coursePageQuery) {
        //转换成查询条件
        LambdaQueryWrapper<Course> queryWrapper = SqlWrapperUtils.toLambdaQueryWrapper(coursePageQuery, Course.class);
        //课程更新时间查询条件
        queryWrapper.between(ObjectUtils.isNotEmpty(coursePageQuery.getBeginTime()) &&
                        ObjectUtils.isNotEmpty(coursePageQuery.getEndTime()), Course::getUpdateTime,
                coursePageQuery.getBeginTime(), coursePageQuery.getEndTime());
        //搜索关键字课程名称
        queryWrapper.like(StringUtils.isNotEmpty(coursePageQuery.getKeyword()),
                Course::getName, coursePageQuery.getKeyword());
        Page<Course> page = page(coursePageQuery.toMpPage(), queryWrapper);
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        //更新人
        List<Long> updaterList = page.getRecords().stream()
                .map(Course::getUpdater)
                .collect(Collectors.toList());
        //查询更新人用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(updaterList);
        Map<Long, String> updaterMap = CollUtils.isEmpty(updaterList) ? new HashMap<>()
                : userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        //课程分类
        List<Category> list = categoryService.list();
        Map<Long, String> categoryNameMap = CollUtils.isEmpty(list) ? new HashMap<>()
                : list.stream().collect(Collectors.toMap(Category::getId, Category::getName));
        //课程id列表
        List<Long> courseIdList = page.getRecords().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        //统计课程报名人数map
        Map<Long, Integer> peoNumOfCourseMap = tradeClient.countEnrollNumOfCourse(courseIdList);

        return PageDTO.of(page, CoursePageVO.class, (course, coursePageVO) -> {
            //课程所属分类
            String categories = StringUtils.format("{}/{}/{}",
                    categoryNameMap.get(course.getFirstCateId()),
                    categoryNameMap.get(course.getSecondCateId()),
                    categoryNameMap.get(course.getThirdCateId()));
            coursePageVO.setCategories(categories);
            //更新人
            coursePageVO.setUpdaterName(updaterMap.get(course.getUpdater()));
            //报名人数
            coursePageVO.setSold(NumberUtils.null2Zero(peoNumOfCourseMap.get(course.getId())));
            //评分
            coursePageVO.setScore(40 + course.getSectionNum() % 10); //临时使用 todo
            // 课时
            coursePageVO.setSections(course.getSectionNum());
        });
    }

    @Override
    public List<Course> queryByCategoryIdAndLevel(Long categoryId, Integer level) {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(level == 1, Course::getFirstCateId, categoryId) //一级课程分类
                .eq(level == 2, Course::getSecondCateId, categoryId) //二级课程分类
                .eq(level == 3, Course::getThirdCateId, categoryId); //三级课程分类
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public CourseAndSectionVO queryCourseAndCatalogById(Long courseId) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.查询课程详情
        CourseFullInfoDTO course = getInfoById(courseId, true, true);
        if (course == null) {
            return null;
        }
        // 3.组织VO
        CourseAndSectionVO vo = new CourseAndSectionVO();
        vo.setId(courseId);
        vo.setName(course.getName());
        vo.setSections(course.getSectionNum());
        vo.setCoverUrl(course.getCoverUrl());
        // 4.查询教师信息
        List<UserDTO> teachers = userClient.queryUserByIds(course.getTeacherIds());
        if (CollUtils.isNotEmpty(teachers)) {
            UserDTO teacher = teachers.get(0);
            vo.setTeacherName(teacher.getName());
            vo.setTeacherIcon(teacher.getIcon());
        }
        // 5.组装小节信息
        List<CatalogueDTO> catas = course.getChapters();
        List<ChapterVO> chapters = new ArrayList<>(catas.size());
        for (CatalogueDTO c : catas) {
            ChapterVO cv = new ChapterVO();
            cv.setId(c.getId());
            cv.setName(c.getName());
            cv.setIndex(c.getIndex());
            cv.setMediaDuration(c.getMediaDuration());
            List<SectionVO> sections = BeanUtils.copyToList(c.getSections(), SectionVO.class);
            cv.setSections(sections);
            chapters.add(cv);
        }
        vo.setChapters(chapters);
        // 6.查询学习进度
        if (learningClient == null) {
            return vo;
        }
        // 6.1.查询学习记录
        LearningLessonDTO lessonDTO = learningClient.queryLearningRecordByCourse(courseId);
        if (lessonDTO == null) {
            // 没有查询到课表信息，说明是免费试看，直接返回
            return vo;
        }
        vo.setLessonId(lessonDTO.getId());
        if (CollUtils.isEmpty(lessonDTO.getRecords())) {
            // 有课表信息，但是没有学习记录，不用处理进度问题了，直接返回
            return vo;
        }
        List<LearningRecordDTO> records = lessonDTO.getRecords();
        // 6.2.获取最近学习的记录。由于查询时按照学习时间排序，第一条记录就是最近学习的小节记录
        Long latestSectionId = lessonDTO.getLatestSectionId();
        if(latestSectionId == null) {
            latestSectionId = records.get(0).getSectionId();
        }
        vo.setLatestSectionId(latestSectionId);
        // 6.3.处理记录为一个map
        Map<Long, LearningRecordDTO> rMap = records.stream()
                .collect(Collectors.toMap(LearningRecordDTO::getSectionId, r -> r));
        // 6.4.填充学习进度到章节中
        for (ChapterVO chapter : vo.getChapters()) {
            for (SectionVO section : chapter.getSections()) {
                LearningRecordDTO r = rMap.get(section.getId());
                if (r == null) continue;
                section.setFinished(r.getFinished());
                section.setMoment(r.getMoment());
            }
        }
        return vo;
    }

    private void sendFinishedCourse(List<Course> finishedCourse) {
        CompletableFuture.runAsync(() -> {
            for (Course course : finishedCourse) {
                try {
                    rabbitMqHelper.send(MqConstants.Exchange.COURSE_EXCHANGE, MqConstants.Key.COURSE_EXPIRE_KEY, course.getId());
                } catch (Exception e) {
                    log.error("课程完结消息发送失败，id : {}", course.getId(), e);
                }
            }
        }, taskExecutor);
    }
}
