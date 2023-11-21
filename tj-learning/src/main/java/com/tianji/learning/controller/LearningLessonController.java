package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.SPELUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LessonStatusVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author fenny
 * @since 2023-11-20
 */
@Api(tags = "我的课程相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/lessons")
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    /**
     * 分页查询用户购买的课程
     * @param query 自定义分页参数
     * @return 包含了LearningLessonVO的自定义分页对象
     */
    @ApiOperation("分页查询我的课表")
    @GetMapping("page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return lessonService.queryMyLessons(query);
    }

    /**
     * 查询正在学习的课程
     * @return LearningLessonVO，该vo包含了课程基本信息和当前小结信息
     */
    @GetMapping("/now")
    @ApiOperation("查询正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson() {
        return lessonService.queryMyCurrentLesson();
    }


    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    /**
     * 用户手动删除&退款自动删除课程
     * @param couseId 课程id
     */
    @ApiOperation("用户手动删除当前课程")
    @DeleteMapping("/{courseId}")
    public void deleteCourseFromLesson(@PathVariable("courseId") Long couseId){
        Long user = UserContext.getUser();
        lessonService.deleteCourseFromLesson(user, couseId);
    }
    @ApiOperation("用户是否拥有该课程并返回学习进度")
    @GetMapping("/{courseId}")
    public LessonStatusVO queryLessonByCourseId(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long couseId){
        LessonStatusVO lessonStatusVO = lessonService.queryLessonByCourseId(couseId);
        return lessonStatusVO;
    }

    /**
     * 统计课程学习人数
     * @param courseId 课程id
     * @return 学习人数
     */
    @ApiOperation("查询该课程的报名人数")
    @GetMapping("/lessons/{courseId}/count")
    Integer countLearningLessonByCourse(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId){
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO){
        lessonService.createLearningPlan(planDTO.getCourseId(), planDTO.getFreq());
    }

    @ApiOperation("查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return lessonService.queryMyPlans(query);
    }
}
