package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LessonStatusVO;
import com.tianji.learning.enums.LessonStatus;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author fenny
 * @since 2023-11-20
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();

    Long isLessonValid(Long courseId);

    void deleteCourseFromLesson(Long userId, Long courseId);

    LessonStatusVO queryLessonByCourseId(Long couseId);

    Integer countLearningLessonByCourse(Long courseId);

    LearningLesson queryByUserAndCourseId(Long user, Long courseId);

    void createLearningPlan(Long courseId, Integer freq);

    LearningPlanPageVO queryMyPlans(PageQuery query);
}
