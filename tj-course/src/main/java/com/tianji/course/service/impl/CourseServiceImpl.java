package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.course.*;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.course.constants.CourseErrorInfo;
import com.tianji.course.constants.CourseStatus;
import com.tianji.course.domain.dto.CourseSimpleInfoListDTO;
import com.tianji.course.domain.dto.IdAndNumDTO;
import com.tianji.course.domain.po.Course;
import com.tianji.course.domain.po.CourseDraft;
import com.tianji.course.domain.po.CourseTeacher;
import com.tianji.course.domain.vo.CataSimpleInfoVO;
import com.tianji.course.domain.vo.CataVO;
import com.tianji.course.domain.vo.CourseSimpleInfoVO;
import com.tianji.course.mapper.*;
import com.tianji.course.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
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
    @Lazy
    private ICourseDraftService courseDraftService;

    @Autowired
    private ISubjectService subjectService;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private RabbitMqHelper rabbitMqHelper;

    @Autowired
    @Lazy
    private ICourseCatalogueService courseCatalogueService;

    @Autowired
    @Lazy
    private ICourseTeacherService courseTeacherService;

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
    public CourseDTO getCourseDTOById(Long id) {
        Course course = baseMapper.selectById(id);
        if (course == null) {
            return null;
        }
        LambdaQueryWrapper<CourseTeacher> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacher::getCourseId, id)
                .orderBy(true, false, CourseTeacher::getCIndex)
                .last(true, "limit 1");
        List<CourseTeacher> courseTeachers = courseTeacherMapper.selectList(queryWrapper);

        CourseDTO courseDTO = BeanUtils.toBean(course, CourseDTO.class);
        courseDTO.setCategoryIdLv1(course.getFirstCateId());
        courseDTO.setCategoryIdLv2(course.getSecondCateId());
        courseDTO.setCategoryIdLv3(course.getThirdCateId());
        courseDTO.setDuration(course.getMediaDuration());
        courseDTO.setPublishTime(course.getCreateTime());
        courseDTO.setSections(course.getSectionNum());
        if (CollUtils.isNotEmpty(courseTeachers)) {
            courseDTO.setTeacher(courseTeachers.get(0).getTeacherId());
        } else {
            courseDTO.setTeacher(0L);
        }


        return courseDTO;

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
    public List<CourseSimpleInfoVO> getSimpleInfoList(CourseSimpleInfoListDTO courseSimpleInfoListDTO) {

        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(CollUtils.isNotEmpty(courseSimpleInfoListDTO.getThirdCataIds()),
                Course::getThirdCateId, courseSimpleInfoListDTO.getThirdCataIds())
                .in(CollUtils.isNotEmpty(courseSimpleInfoListDTO.getIds()),
                        Course::getId, courseSimpleInfoListDTO.getIds());
        List<Course> courses = baseMapper.selectList(queryWrapper);
        return BeanUtils.copyList(courses, CourseSimpleInfoVO.class);
    }

    @Override
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
            if (course.getStatus() == CourseStatus.DOWN_SHELF.getStatus()) { //课程已经下架
                //课程已经下架
                throw new BizIllegalException(course.getName() + CourseErrorInfo.Msg.COURSE_CHECK_DOWN_SHELF);
            }
            if (course.getStatus() == CourseStatus.FINISHED.getStatus() ||
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

        List<IdAndNumDTO> idAndNumDTOS = baseMapper.countCourseNumOfTeacher(teacherIds);
        //老师id和课程数量(已上架、已下架、已过期)
        Map<Long, Long> teacherIdAndCourseNumMap = CollUtils.isEmpty(idAndNumDTOS) ? new HashMap<>() :
                idAndNumDTOS.stream().collect(Collectors.toMap(IdAndNumDTO::getId, IdAndNumDTO::getNum));
        //待上架
        List<IdAndNumDTO> idAndNumDTOS2 = courseDraftMapper.countCourseNumOfTeacher(teacherIds);
        Map<Long, Long> teacherIdAndCourseNumMap2 = CollUtils.isEmpty(idAndNumDTOS2) ? new HashMap<>() :
                idAndNumDTOS2.stream().collect(Collectors.toMap(IdAndNumDTO::getId, IdAndNumDTO::getNum));


        //老师id和出题数量
        Map<Long, Long> teacherIdAndSubjectNumMap = subjectService.countSubjectNumOfTeacher(teacherIds);

        List<SubNumAndCourseNumDTO> subNumAndCourseNumDTOS = new ArrayList<>();
        //遍历老师id，并为每个老师设置出题数量和课程数量
        for (Long teacherId : teacherIds) {
            subNumAndCourseNumDTOS.add(new SubNumAndCourseNumDTO(
                    teacherId,
                    //课程数量 待上架的数量+ （已上架、已下架、已过期）
                    NumberUtils.null2Zero(teacherIdAndCourseNumMap.get(teacherId)).intValue() +
                            NumberUtils.null2Zero(teacherIdAndCourseNumMap2.get(teacherId)).intValue(),
                    //出题数量
                    NumberUtils.null2Zero(teacherIdAndSubjectNumMap.get(teacherId)).intValue()));
        }
        return subNumAndCourseNumDTOS;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int courseFinished() {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.le(Course::getPurchaseEndTime, LocalDateTime.now())
                .in(Course::getStatus, List.of(CourseStatus.DOWN_SHELF.getStatus(), CourseStatus.SHELF.getStatus()));
        List<Course> courses = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courses)) {
            return 0;
        }
        List<Course> updateCourses = new ArrayList<>();
        for (Course course : courses) {
            Course updateCourse = new Course();
            updateCourse.setId(course.getId());
            updateCourse.setStatus(CourseStatus.FINISHED.getStatus());
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

    public List<CourseInfoDTO> queryCourseInfosByIds(List<Long> ids) {
        if (CollUtils.isEmpty(ids)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Course::getId, ids);
        List<Course> courses = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courses)) {
            return new ArrayList<>();
        }
        //课程分类id
        Set<Long> cataIds = new HashSet<>();
        for (Course course : courses) {
            cataIds.add(course.getFirstCateId());
            cataIds.add(course.getSecondCateId());
            cataIds.add(course.getThirdCateId());
        }
        //课程分类信息
        List<CataSimpleInfoVO> cataSimpleInfoVOS = courseCatalogueService.getManyCataSimpleInfo(new ArrayList<>(cataIds));
        Map<Long, String> cataSimpleInfoVOMap = CollUtils.isEmpty(cataSimpleInfoVOS) ?
                new HashMap<>() : cataSimpleInfoVOS.stream().collect(
                Collectors.toMap(CataSimpleInfoVO::getId, CataSimpleInfoVO::getName));

        return BeanUtils.copyList(courses, CourseInfoDTO.class, (course, courseInfoDto) -> {
            courseInfoDto.setCates(Arrays.asList(
                    StringUtils.nullToEmpty(cataSimpleInfoVOMap.get(course.getFirstCateId())), //一级目录分类
                    StringUtils.nullToEmpty(cataSimpleInfoVOMap.get(course.getSecondCateId())), //二级目录分类
                    StringUtils.nullToEmpty(cataSimpleInfoVOMap.get(course.getThirdCateId())) //三级目录分类
            ));
        });
    }

    @Override
    public CourseInfoDTO getInfoById(Long id) {
        Course course = baseMapper.selectById(id);
        CourseInfoDTO courseInfoDTO = BeanUtils.toBean(course, CourseInfoDTO.class);
        if (courseInfoDTO == null) {
            return new CourseInfoDTO();
        }
        List<CataVO> cataVOS = courseCatalogueService.queryCourseCatalogues(id, true);
        courseInfoDTO.setCataDTOS(BeanUtils.copyList(cataVOS, CataDTO.class));
        courseInfoDTO.setTeacherIds(courseTeacherService.getTeacherIdOfCourse(id));
        return courseInfoDTO;
    }

    @Override
    public CourseSimpleInfoDTO getSimpleInfoById(Long id) {
        Course course = getById(id);
        return BeanUtils.copyBean(course, CourseSimpleInfoDTO.class);
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
