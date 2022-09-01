package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.course.*;
import com.tianji.course.domain.dto.CourseSimpleInfoListDTO;
import com.tianji.course.domain.po.Course;
import com.tianji.course.domain.vo.CourseSimpleInfoVO;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 草稿课程 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-20
 */
public interface ICourseService extends IService<Course> {

    /**
     * 修改课程状态
     *
     * @param id 课程id
     * @param status 课程状态
     */
    void updateStatus(Long id, Integer status);

    CourseDTO getCourseDTOById(Long id);


    void delete(Long id);

    /**
     * 根据条件查询课程简单信息
     *
     * @param courseSimpleInfoListDTO 课程三级分类列表
     * @return
     */
    List<CourseSimpleInfoVO> getSimpleInfoList(CourseSimpleInfoListDTO courseSimpleInfoListDTO);

    /**
     * 校验课程列表中这些课程当前是否可以购买,可以购买返回课程信息列表
     * @param courseIds
     * @return
     */
    List<CoursePurchaseInfoDTO> checkCoursePurchase(List<Long> courseIds);

    /**
     * 查询老师出题数目和课程数目
     * @param teacherIds
     * @return
     */
    List<SubNumAndCourseNumDTO> countSubjectNumAndCourseNumOfTeacher(List<Long> teacherIds);

    /**
     * 课程完结
     * @return
     */
    int courseFinished();

    /**
     * 统计每个分类id所拥有的课程数量
     * @param categoryIds
     * @return
     */
    Map<Long, Integer> countCourseNumOfCategory(List<Long> categoryIds);

    /**
     * 统计某个课程分类的课程数量
     *
     * @param categoryId 课程分类id
     * @return 课程数量
     */
    Integer countCourseNumOfCategory(Long categoryId);

    /**
     * 根据课程id批量查询订单信息
     *
     * @param ids
     * @return
     */
    List<CourseInfoDTO> queryCourseInfosByIds(@RequestParam("ids") List<Long> ids);

    CourseInfoDTO getInfoById(Long id);

    CourseSimpleInfoDTO getSimpleInfoById(Long id);
}
