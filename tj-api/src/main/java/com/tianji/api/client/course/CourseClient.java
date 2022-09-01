package com.tianji.api.client.course;

import com.tianji.api.dto.course.*;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(contextId = "course", value = "course-service")
public interface CourseClient {

    /**
     * 根据老师id列表获取老师出题数据和讲课数据
     * @param teacherIds 老师id列表
     * @return 老师id和老师对应的出题数和教课数
     */
    @GetMapping("/course/infoByTeacherIds")
    List<SubNumAndCourseNumDTO> infoByTeacherIds(@RequestParam("teacherIds") Iterable<Long> teacherIds);

    /**
     * 根据小节id获取小节对应的mediaId和课程id
     *
     * @param sectionId 小节id
     * @return 小节对应的mediaId和课程id
     */
    @GetMapping("/course/section/{id}")
    @ApiImplicitParam(name = "id", value = "小节id，不支持章id或者练习id查询")
    SectionInfoDTO sectionInfo(@PathVariable("id") Long sectionId);

    /**
     * 根据媒资Id列表查询媒资被引用的次数
     *
     * @param mediaIds 媒资id列表
     * @return 媒资id和媒资被引用的次数的列表
     */
    @GetMapping("/course/media/useInfo")
    List<MediaQuoteDTO> mediaUserInfo(@RequestParam("mediaIds") Iterable<Long> mediaIds);

    /**
     * 获取课程信息包含草稿中的
     *
     * @param id 课程id
     * @param draft 是否查看草稿信息
     * @return
     */
    @GetMapping("/course/getSearchInfo/{id}")
    CourseDTO getSearchInfo(@PathVariable("id") Long id, @RequestParam("draft") Boolean draft);

    /**
     * 校验课程是否可以购买，并返回课程信息
     *
     * @param courseIds 课程id列表
     * @return
     */
    @GetMapping("/course/checkCoursePurchase")
    List<CoursePurchaseInfoDTO> checkCoursePurchase(@RequestParam("courseIds") Iterable<Long> courseIds);

    @GetMapping("/courses/simpleInfo/list")
    List<CourseSimpleInfoDTO> getSimpleInfoList(@RequestParam("ids") Iterable<Long> ids);

    @GetMapping("/courses/simpleInfo/{id}")
    CourseSimpleInfoDTO getSimpleInfoById(@PathVariable("id") Long id);

    /**
     * 课程完结任务
     * @return
     */
    @PostMapping("/course/finished")
    Integer courseFinished();

    /**
     * 根据课程id，查询课程信息
     * @param id
     * @return
     */
    @GetMapping("/course/{id}")
    @ApiOperation("获取课程的基本信息")
    CourseInfoDTO getCourseInfoById(@PathVariable("id") Long id);
}