package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.course.CourseDTO;
import com.tianji.course.domain.dto.CourseBaseInfoSaveDTO;
import com.tianji.course.domain.po.CourseDraft;
import com.tianji.course.domain.vo.CourseBaseInfoVO;
import com.tianji.course.domain.vo.CourseSaveVO;

/**
 * <p>
 * 草稿课程 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-18
 */
public interface ICourseDraftService extends IService<CourseDraft> {

    /**
     * 保存草稿
     *
     * @param courseBaseInfoSaveDTO
     * @return
     */
    CourseSaveVO save(CourseBaseInfoSaveDTO courseBaseInfoSaveDTO);

    /**
     * 如果用于编辑，需要先去草稿中拿出已经编辑的内容，如果不是用于编辑直接获取正式数据
     *
     * @param id 课程id
     * @param see 是否用于查看页面查看数据使用，不是的话就是编辑页面使用
     * @return 课程基本信息
     */
    CourseBaseInfoVO getCourseBaseInfo(Long id, Boolean see);

    /**
     * 修改课程草稿进行到哪一步了，步骤只能一步步升，不能跳填，不能往回填
     * @param id
     * @param step
     */
    void updateStep(Long id, Integer step);

    /**
     * 课程上架
     *
     * @param id 课程上架
     */
    void upShelf(Long id);

    /**
     * 课程下架
     *
     * @param id 课程id
     */
    void downShelf(Long id);

    /**
     * 获取课程的搜索信息
     * @param id
     * @return
     */
    CourseDTO getCourseDTOById(Long id);

    /**
     * 删除课程的草稿
     *
     * @param id 课程id
     */
    void delete(Long id);

}
