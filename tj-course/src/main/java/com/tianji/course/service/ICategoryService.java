package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.course.domain.dto.CategoryAddDTO;
import com.tianji.course.domain.dto.CategoryDisableOrEnableDTO;
import com.tianji.course.domain.dto.CategoryListDTO;
import com.tianji.course.domain.dto.CategoryUpdateDTO;
import com.tianji.course.domain.po.Category;
import com.tianji.course.domain.vo.CategoryInfoVO;
import com.tianji.course.domain.vo.CategoryVO;
import com.tianji.course.domain.vo.SimpleCategoryVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 课程分类 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-14
 */
public interface ICategoryService extends IService<Category> {

    /**
     * 分页查询课程信息
     *
     * @param categoryPageDTO
     * @return
     */
    List<CategoryVO> list( CategoryListDTO categoryPageDTO);

    /**
     * 新增课程分页
     *
     * @param categoryAddDTO 分类信息
     */
    void add(CategoryAddDTO categoryAddDTO);

    /**
     * 获取课程分类信息
     * @param id 课程id
     * @return
     */
    CategoryInfoVO get(Long id);

    /**
     * 删除课程分类
     * @param id
     */
    void delete(Long id);

    /**
     * 课程分类启用或禁用
     *
     * @param categoryDisableOrEnableDTO
     */
    void disableOrEnable(CategoryDisableOrEnableDTO categoryDisableOrEnableDTO);

    /**
     * 更新课程分类信息
     *
     * @param categoryUpdateDTO
     */
    void update(CategoryUpdateDTO categoryUpdateDTO);

    /**
     * 获取所有分类的数据及结构
     * @return
     */
    List<SimpleCategoryVO> all();

    /**
     * 获取课程分类id和名称
     * @return
     */
    Map<Long, String> getCateIdAndName();

    List<CategoryBasicDTO> allOfOneLevel();

    /**
     * 根据课程分类id查询分类列表
     * @param ids
     * @return
     */
    List<Category> queryByIds(List<Long> ids);


}
