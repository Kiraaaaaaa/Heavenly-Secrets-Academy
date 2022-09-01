package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.course.domain.dto.CourseTeacherSaveDTO;
import com.tianji.course.domain.po.CourseTeacherDraft;
import com.tianji.course.domain.vo.CourseTeacherVO;

import java.util.List;

/**
 * <p>
 * 课程老师关系表草稿 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-20
 */
public interface ICourseTeacherDraftService extends IService<CourseTeacherDraft> {

    /**
     * 保存课程指定的老师
     * @param courseTeacherSaveDTO
     */
    void save(CourseTeacherSaveDTO courseTeacherSaveDTO);

    /**
     * 查询指定课程对应的老师
     *
     * @param courseId
     * @param see 是否用于查看
     * @return
     */
    List<CourseTeacherVO> queryTeacherOfCourse(Long courseId,Boolean see);

    /**
     * 课程老师上架
     * @param courseId
     */
    void copyToShelf(Long courseId, Boolean isFirstShelf);

    /**
     * 将已上架的老师信息下载到草稿中
     *
     * @param courseId
     */
    void copyToDraft(Long courseId);
}
