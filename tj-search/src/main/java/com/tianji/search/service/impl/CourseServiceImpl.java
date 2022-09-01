package com.tianji.search.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.RandomUtils;
import com.tianji.search.domain.po.Course;
import com.tianji.search.enums.CourseStatus;
import com.tianji.search.repository.CourseRepository;
import com.tianji.search.service.ICourseService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

@Service
public class CourseServiceImpl implements ICourseService {

    @Resource
    private CourseRepository courseRepository;
    @Resource
    private CourseClient courseClient;

    @Override
    public void handleNewCourse(Long courseId) {
        // 1.根据id查询课程信息
        CourseDTO courseDTO = courseClient.getSearchInfo(courseId, true);
        if (courseDTO == null) {
            return;
        }
        // 2.数据转换
        Course course = BeanUtils.toBean(courseDTO, Course.class);
        course.setType(courseDTO.getCourseType());
        course.setScore(41 + RandomUtils.randomInt(10));
        course.setSold(0);
        // 3.写入索引库
        courseRepository.save(course);
    }

    @Override
    public void handleCourseDelete(Long courseId) {
        // 1.直接删除
        courseRepository.deleteById(courseId);
    }

    @Override
    public void handleCourseStatus(Long courseId, CourseStatus status) {
        courseRepository.updateById(courseId, CourseRepository.STATUS, status.getValue());
    }

    @Override
    public void handleCourseUp(Long courseId) {
        // 1.根据id查询课程信息
        CourseDTO courseDTO = courseClient.getSearchInfo(courseId, false);
        if (courseDTO == null) {
            return;
        }
        // 2.查询旧数据
        Optional<Course> optional = courseRepository.findById(courseId);

        // 3.数据转换
        Course course = BeanUtils.toBean(courseDTO, Course.class);
        course.setType(courseDTO.getCourseType());
        // 4.判断是否已经存在
        if (optional.isPresent()) {
            // 4.1.已经存在，更新
            Course old = optional.get();
            course.setSold(old.getSold());
            course.setScore(old.getScore());
        }else{
            // 4.2.不存在，直接新增
            course.setScore(41 + RandomUtils.randomInt(10));
            course.setSold(0);
        }
        // 5.写入索引库
        courseRepository.save(course);

    }

    @Override
    public void updateCourseSold(List<Long> courseIds, int amount) {
        courseRepository.incrementSold(courseIds, amount);
    }
}
