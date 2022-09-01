package com.tianji.course.controller;

import com.tianji.api.dto.course.*;
import com.tianji.api.dto.course.CourseInfoDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.course.service.ICourseCatalogueService;
import com.tianji.course.service.ICourseDraftService;
import com.tianji.course.service.ICourseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部服务接口调用
 *
 * @ClassName CourseInfoController
 * @Author wusongsong
 * @Date 2022/7/18 15:19
 * @Version
 **/
@RestController
@RequestMapping("course")
@Api(tags = "课程相关接口，内部调用")
public class CourseInfoController {

    @Autowired
    private ICourseCatalogueService courseCatalogueService;

    @Autowired
    private ICourseService courseService;

    @Autowired
    private ICourseDraftService courseDraftService;

    @GetMapping("infoByTeacherIds")
    @ApiOperation("通过老师id获取老师负责的课程和出的题目数量")
    public List<SubNumAndCourseNumDTO> infoByTeacherIds(@RequestParam("teacherIds") List<Long> teacherIds) {

        if (CollUtils.isEmpty(teacherIds)) {
            return new ArrayList<>();
        }
        return courseService.countSubjectNumAndCourseNumOfTeacher(teacherIds);
    }

    /**
     * 根据小节id获取小节对应的mediaId和课程id
     *
     * @param sectionId 小节id
     * @return 小节对应的mediaId和课程id
     */
    @GetMapping("/section/{id}")
    @ApiImplicitParam(name = "id", value = "小节id，不支持章id或者练习id查询")
    public SectionInfoDTO sectionInfo(@PathVariable("id") Long sectionId) {
        return courseCatalogueService.getSimpleSectionInfo(sectionId);
    }

    /**
     * 根据媒资Id列表查询媒资被引用的次数
     *
     * @param mediaIds 媒资id列表
     * @return 媒资id和媒资被引用的次数的列表
     */
    @GetMapping("/media/useInfo")
    public List<MediaQuoteDTO> mediaUserInfo(@RequestParam("mediaIds") List<Long> mediaIds) {
        return courseCatalogueService.countMediaUserInfo(mediaIds);
    }

    @GetMapping("/getSearchInfo/{id}")
    @ApiOperation("获取课程检索信息，状态发生变化时")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "draft", value = "是否获取草稿，待上架的课程获取课程的草稿信息，其他状态获取已上架信息"),
            @ApiImplicitParam(name = "id", value = "课程id")
    })
    public CourseDTO getSearchInfo(@PathVariable("id") Long id, @RequestParam("draft") Boolean draft) {
        if (draft) {
            return courseDraftService.getCourseDTOById(id);
        } else {
            return courseService.getCourseDTOById(id);
        }
    }

    @GetMapping("/checkCoursePurchase")
    @ApiOperation("校验课程是否可以购买，并且返回课程信息")
    public List<CoursePurchaseInfoDTO> checkCoursePurchase(@RequestParam("courseIds") List<Long> courseIds) {
        return courseService.checkCoursePurchase(courseIds);
    }

    @PostMapping("/finished")
    @ApiOperation("课程完结任务")
    public Integer courseFinished() {
        return courseService.courseFinished();
    }


    @GetMapping("/{id}")
    @ApiOperation("获取课程信息")
    @ApiImplicitParams(
            @ApiImplicitParam(name = "id", value = "获取课程信息")
    )
    public CourseInfoDTO getById(@PathVariable("id") Long id) {
        return courseService.getInfoById(id);
    }
    @GetMapping("/queryCourseInfosByIds")
    @ApiOperation("根据课程id查询课程信息")
    public List<CourseInfoDTO> queryCourseInfosByIds(@RequestParam("ids") List<Long> ids) {
        return courseService.queryCourseInfosByIds(ids);
    }
}
