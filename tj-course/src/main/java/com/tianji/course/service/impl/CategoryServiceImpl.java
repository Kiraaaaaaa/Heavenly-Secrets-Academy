package com.tianji.course.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.constants.Constant;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.enums.CommonStatus;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.course.constants.CourseConstants;
import com.tianji.course.constants.CourseErrorInfo;
import com.tianji.course.constants.RedisConstants;
import com.tianji.course.domain.dto.CategoryAddDTO;
import com.tianji.course.domain.dto.CategoryDisableOrEnableDTO;
import com.tianji.course.domain.dto.CategoryListDTO;
import com.tianji.course.domain.dto.CategoryUpdateDTO;
import com.tianji.course.domain.po.Category;
import com.tianji.course.domain.vo.CategoryInfoVO;
import com.tianji.course.domain.vo.CategoryVO;
import com.tianji.course.domain.vo.SimpleCategoryVO;
import com.tianji.course.mapper.CategoryMapper;
import com.tianji.course.mapper.SubjectCategoryMapper;
import com.tianji.course.service.ICategoryService;
import com.tianji.course.service.ICourseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 课程分类 服务实现类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-14
 */
@Service
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SubjectCategoryMapper subjectCategoryMapper;

    @Autowired
    private ICourseService courseService;

    @Override
    public List<CategoryVO> list(CategoryListDTO categoryListDTO) {

        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getPriority);
        //查询数据
        List<Category> list = super.list(queryWrapper);
        if (CollUtils.isEmpty(list)) {
            return new ArrayList<>();
        }

        //统计一级二级目录对应的三级目录的数量，做一个三分钟的redis缓存
        Map<Long, Integer> thirdCategoryNumMap = this.statisticThirdCategory();

        Map<Long, Integer> cateIdAndNumMap = courseService
                .countCourseNumOfCategory(list.stream().map(Category::getId).collect(Collectors.toList()));
        //通过map搭建分类之间的关系
        Map<Long, CategoryVO> resultMap = new HashMap<>();
        for (Category category : list) {
            CategoryVO current = resultMap.get(category.getId());
            CategoryVO categoryVO = BeanUtils.toBean(category, CategoryVO.class);
            if (current != null) {
                //之前创建子分类的时候添加了该分类，需要从map中拿出它已经设置好的信息（子分类列表）
                categoryVO.setChildren(current.getChildren());
            } else {
                categoryVO.setChildren(new ArrayList<>());
            }
            //分类信息设置
            categoryVO.setCourseNum(NumberUtils.null2Zero(cateIdAndNumMap.get(category.getId())));
            //获取当前分类拥有的三级分类的数量
            categoryVO.setThirdCategoryNum(NumberUtils.null2Zero(thirdCategoryNumMap.get(category.getId())));
            categoryVO.setIndex(category.getPriority());
            categoryVO.setStatusDesc(CommonStatus.desc(category.getStatus()));
            resultMap.put(category.getId(), categoryVO);
            //获取父分类
            CategoryVO parent = resultMap.get(category.getParentId());
            if (parent == null) { //父分类未创建，创建一个空的分分类
                parent = new CategoryVO();
                List<CategoryVO> children = new ArrayList<>();
                parent.setChildren(children);
            }
            //绑定和父分类关系（子分类添加到父分类的子分类列表中）
            parent.getChildren().add(categoryVO);
            resultMap.put(category.getParentId(), parent);
        }

        //采用递归实现数据过滤
        boolean pass = filter(resultMap.get(CourseConstants.CATEGORY_ROOT), categoryListDTO);
        if (pass) {
            return resultMap.get(CourseConstants.CATEGORY_ROOT).getChildren();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional(rollbackFor = {DbException.class, Exception.class})
    public void add(CategoryAddDTO categoryAddDTO) {

        //校验名称是否重复
        checkSameName(categoryAddDTO.getParentId(), categoryAddDTO.getName(), null);
        int level = 1; //默认一级分类
        if (CourseConstants.CATEGORY_ROOT != categoryAddDTO.getParentId()) {
            //校验父分类是否存在
            Category parentCategory = this.baseMapper.selectById(categoryAddDTO.getParentId());
            if (parentCategory == null) {
                throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_PARENT_NOT_FOUND);
            }
            //三级课程分类下不能在创建子分类
            if (parentCategory.getLevel() == 3) {
                throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_CREATE_ON_THIRD);
            }
            //分类级别，父分类+1
            level = parentCategory.getLevel() + 1;
        }
        if (level > 3) {
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_ADD_OVER_THIRD_LEVEL);
        }

        //将请求参数转换成PO
        Category category = BeanUtils.copyBean(categoryAddDTO, Category.class, (dto, po) -> {
            po.setPriority(dto.getIndex());
            po.setStatus(CommonStatus.ENABLE.getValue());
        });
        //设置分类级别
        category.setLevel(level);
        if (this.baseMapper.insert(category) <= 0) {
            throw new DbException(null);
        }
    }

    @Override
    public CategoryInfoVO get(Long id) {
        Category category = this.baseMapper.selectById(id);
        if (category == null) {
            return new CategoryInfoVO();
        }
        CategoryInfoVO categoryInfoVO = BeanUtils.toBean(category, CategoryInfoVO.class);
        categoryInfoVO.setCategoryLevel(category.getLevel());
        categoryInfoVO.setStatusDesc(CommonStatus.desc(category.getStatus()));
        categoryInfoVO.setIndex(category.getPriority());
        Long firstCategoryId = null; //所属一级分类
        if (category.getLevel() == 3) { //三级级分类
            Category secondCategory = this.baseMapper.selectById(category.getParentId()); //所在二级目录
            categoryInfoVO.setSecondCategoryName(secondCategory.getName());
            firstCategoryId = secondCategory.getParentId(); //所在一级分类
        } else if (category.getLevel() == 2) { //二级分类
            firstCategoryId = category.getParentId(); //所属一级分类
        }

        if (firstCategoryId != null) { //不为null，当前分类有所属一级分类
            Category firstCategory = this.baseMapper.selectById(firstCategoryId);
            categoryInfoVO.setFirstCategoryName(firstCategory.getName());
        }
        return categoryInfoVO;
    }

    @Override
    public void delete(Long id) {
        //1.有子分类不能删除
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(Category::getParentId, id);
        List<Category> categories = this.baseMapper.selectList(queryWrapper);
        if (CollectionUtil.isNotEmpty(categories)) { //分类有子分类
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_HAVE_CHILD);
        }
        Category category = this.baseMapper.selectById(id);
        if (category == null) {
            throw new DbException(ErrorInfo.Msg.DB_DELETE_EXCEPTION);
        }
        //2.分类已经有课程不能删除
        Integer courseNum = courseService.countCourseNumOfCategory(id);
        if(courseNum > 0) {
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_DELETE_HAVE_COURSE);
        }
        //3.分类有题目不能删除 题目数量
        int subjectNum = subjectCategoryMapper.countSubjectNum(category.getId(), category.getLevel());
        if (subjectNum > 0) { //课程含有题目
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_DELETE_HAVE_SUBJECT);
        }
        int result = this.baseMapper.deleteById(id);
        if (result <= 0) {
            throw new DbException(CourseErrorInfo.Msg.CATEGORY_DELETE_FAILD);
        }
    }

    /**
     * 功能点：
     * 1.启用或禁用课程，下一级或下一级的课程同时启用或禁用，
     * 联动启用或禁用
     */
    @Override
    @Transactional(rollbackFor = {DbException.class, Exception.class})
    public void disableOrEnable(CategoryDisableOrEnableDTO categoryDisableOrEnableDTO) {

        Category category = baseMapper.selectById(categoryDisableOrEnableDTO.getId());
        if(category == null){ //父id
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_CATEGORY_NOT_FOUND);
        }
        if(category.getParentId() != 0 ) { //校验父级分类
            if(categoryDisableOrEnableDTO.getStatus().equals(CommonStatus.ENABLE.getValue())){
                //启用分类
                Category parentCategory = baseMapper.selectById(category.getParentId());
                if(parentCategory == null) {
                    log.error("操作异常，根据父类id查询课程分类未查询到，parentId : {}", category.getParentId());
                    throw new BizIllegalException(ErrorInfo.Msg.OPERATE_FAILED);
                }
                if (CommonStatus.DISABLE.equalsValue(parentCategory.getStatus())){
                    // 分类的上一级分类被禁用了，无法开启
                    throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_ENABLE_CANNOT);
                }
            }
        }

        List<Long> childCategoryIds = new ArrayList<>();
        LambdaQueryWrapper<Category> directQueryWrapper = new LambdaQueryWrapper<>();
        directQueryWrapper.eq(Category::getParentId, categoryDisableOrEnableDTO.getId());
        List<Category> categories = baseMapper.selectList(directQueryWrapper);
        if(CollUtils.isNotEmpty(categories)) { //直接子分类
            childCategoryIds.addAll(categories.stream().map(Category::getId).collect(Collectors.toList()));
        }
        if(CollUtils.isNotEmpty(childCategoryIds)){ //间接子分类
            LambdaQueryWrapper<Category> inDirectQueryWrapper = new LambdaQueryWrapper<>();
            inDirectQueryWrapper.in(Category::getParentId, childCategoryIds);
            List<Category> inDirectCategorys = baseMapper.selectList(inDirectQueryWrapper);
            if(CollUtils.isNotEmpty(inDirectCategorys)){
                childCategoryIds.addAll(inDirectCategorys.stream().map(Category::getId).collect(Collectors.toList()));
            }
        }

        int result = this.baseMapper.updateById(BeanUtils.toBean(categoryDisableOrEnableDTO, Category.class));
        if (result <= 0) {
            throw new BizIllegalException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
        }
        //启用或禁用相关课程
        if(CollUtils.isNotEmpty(childCategoryIds)) {
            //子分类同时启用或禁用
            LambdaUpdateWrapper<Category> updateWrapper = new LambdaUpdateWrapper();
            updateWrapper.in(Category::getId, childCategoryIds);
            Category updateCategory = new Category();
            updateCategory.setStatus(categoryDisableOrEnableDTO.getStatus());
            baseMapper.update(updateCategory, updateWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = {DbException.class, Exception.class})
    public void update(CategoryUpdateDTO categoryUpdateDTO) {
        Category category = this.baseMapper.selectById(categoryUpdateDTO.getId());
        if (category == null) {
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_NOT_FOUND);
        }
        checkSameName(category.getParentId(), categoryUpdateDTO.getName(), categoryUpdateDTO.getId());
        Category updateCategory = new Category();
        updateCategory.setId(categoryUpdateDTO.getId()); //修改课程分类id
        updateCategory.setPriority(categoryUpdateDTO.getIndex());
        updateCategory.setName(categoryUpdateDTO.getName());
        int result = this.baseMapper.updateById(updateCategory);
        if (result <= 0) {
            throw new BizIllegalException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
        }
    }

    @Override
    public List<SimpleCategoryVO> all() {
        //升序查询所有未禁用的课程分类
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getStatus, CommonStatus.ENABLE.getValue())
                .orderByAsc(Category::getPriority);
        List<Category> categories = this.baseMapper.selectList(queryWrapper);

        if (CollectionUtil.isEmpty(categories)) {
            return new ArrayList<>();
        }
        Map<Long, SimpleCategoryVO> categoryVOMap = new HashMap<>();
        categories.forEach(category -> {
            SimpleCategoryVO simpleCategoryVO = categoryVOMap.get(category.getId());
            //分类已经添加上只补充名字就可以
            if (simpleCategoryVO != null) {
                simpleCategoryVO.setName(category.getName());
            } else {
                //分类未添加，生成SimpleCategoryVO
//                simpleCategoryVO = new SimpleCategoryVO(category.getId(), category.getName(), new ArrayList<>(), category.getLevel());
                simpleCategoryVO = BeanUtils.toBean(category, SimpleCategoryVO.class);
                simpleCategoryVO.setChildren(new ArrayList<>());
                //将课程分类放入到map中
                categoryVOMap.put(category.getId(), simpleCategoryVO);
            }

            //父类是否已经放入到map，没有造一个，但是缺少名字
            SimpleCategoryVO parentCategory = categoryVOMap.get(category.getParentId());
            if (parentCategory == null) {
                parentCategory = BeanUtils.toBean(category, SimpleCategoryVO.class);
                parentCategory.setChildren(new ArrayList<>());
                //将课程父分类放入到map中
                categoryVOMap.put(category.getParentId(), parentCategory);
            }
            //将分类加入到课程分类的父分类中
            parentCategory.getChildren().add(simpleCategoryVO);

        });
        //从map中拿出id为0的分类信息，父分类的children就是所有的一级分类
        if(CollUtils.isEmpty(categoryVOMap)){
            return new ArrayList<>();
        }
        //过滤掉二级分类children为空的
        for (SimpleCategoryVO simpleCategoryVO : categoryVOMap.values()){
            if(simpleCategoryVO.getLevel() == 2 && CollUtils.isEmpty(simpleCategoryVO.getChildren())) {
                //二级目录没有对应的三级目录
                SimpleCategoryVO firstCategory = categoryVOMap.get(simpleCategoryVO.getParentId());
                if(firstCategory != null) {
                    firstCategory.getChildren().remove(simpleCategoryVO);
                }
            }
        }
        List<SimpleCategoryVO> firstCategoryList = categoryVOMap.get(CourseConstants.CATEGORY_ROOT).getChildren();
        //过滤掉一级分类children为空的分类
        return firstCategoryList.stream().filter(simpleCategoryVO ->
                CollUtils.isNotEmpty(simpleCategoryVO.getChildren())).collect(Collectors.toList());
    }

    @Override
    public Map<Long, String> getCateIdAndName() {
        List<Category> categories = this.baseMapper.selectList(null);
        return categories.stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    @Override
    public List<CategoryVO> allOfOneLevel() {
        //查询数据
        List<Category> list = super.list();
        if (CollUtils.isEmpty(list)) {
            return new ArrayList<>();
        }

        //统计一级二级目录对应的三级目录的数量，做一个三分钟的redis缓存
        Map<Long, Integer> thirdCategoryNumMap = this.statisticThirdCategory();
        return BeanUtils.copyList(list, CategoryVO.class, (category, categoryVO) -> {
            categoryVO.setThirdCategoryNum(thirdCategoryNumMap.get(category.getId()));
        });
    }

    @Override
    public List<Category> queryByIds(List<Long> ids) {
        if(CollUtils.isEmpty(ids)){
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Category::getId, ids);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Map<Long, String> queryByThirdCateIds(List<Long> thirdCateIdList) {
        Map<Long, String> resultMap = new HashMap<>();
        //1.校验
        // 1.1判断参数是否为空
        if(CollUtils.isEmpty(thirdCateIdList)){
            return resultMap;
        }
        // 1.2校验分类id都是三级分类id
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getLevel, 3)
                .in(Category::getId, thirdCateIdList);
        int thirdCateNum = baseMapper.selectCount(queryWrapper);
        if(!NumberUtils.equals(thirdCateNum, thirdCateIdList.size())){
            throw new BizIllegalException(ErrorInfo.Msg.REQUEST_PARAM_ILLEGAL);
        }
        //2.查询所有分类，并将分类转化成map
        List<Category> categories = baseMapper.selectList(null);
        Map<Long, Category> categoryMap = categories.stream().collect(Collectors.toMap(Category::getId, p -> p));
        //3.遍历三级分类id
        for (Long thirdCateId : thirdCateIdList) {
            //3.1三级分类
            Category thirdCategory = categoryMap.get(thirdCateId);

            //3.2二级分类
            Category secondCategory = categoryMap.get(thirdCategory.getParentId());
            //3.3一级分类
            Category firstCategory = categoryMap.get(thirdCateId);
            resultMap.put(thirdCateId, StringUtils.format("{}/{}/{}",
                    firstCategory.getName(), secondCategory.getName(), thirdCategory.getName()));
        }
        return resultMap;
    }


    /**
     * 新增或更新时校验是否有同名的分类
     *
     */
    private void checkSameName(Long parentId, String name, Long currentId) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        //同一个父id的分类不能同名
        //不能和父类同名
        queryWrapper.or().eq(true, Category::getParentId, parentId)
                .eq(Category::getName, name)
                .eq(Category::getDeleted, Constant.DATA_NOT_DELETE);
        queryWrapper.or().eq(Category::getId, parentId)
                .eq(Category::getName, name);
        List<Category> categories = this.baseMapper.selectList(queryWrapper);
        //新增情况下，有同名的分类
        if (currentId == null && CollectionUtil.isNotEmpty(categories)) {
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_SAME_NAME);
        }
        //更新情况下出现同名，需要判断是否是当前分类的名称
        if (CollectionUtil.isNotEmpty(categories) && categories.get(0).getId() != currentId.longValue()) {
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_SAME_NAME);
        }
    }

    /**
     * 统计每个一级目录二级目录有多少个三级目录
     *
     * @return
     */
    private Map<Long, Integer> statisticThirdCategory() {
        Map<Long, Integer> result = new HashMap<>();

        Object resultObj = redisTemplate.opsForValue().get(RedisConstants.REDIS_KEY_CATEGORY_THIRD_NUMBER);
        if (resultObj != null) {
            return (Map<Long, Integer>) resultObj;
        }

        synchronized (RedisConstants.REDIS_KEY_CATEGORY_THIRD_NUMBER) {
            //再次查看
            resultObj = redisTemplate.opsForValue().get(RedisConstants.REDIS_KEY_CATEGORY_THIRD_NUMBER);
            if (resultObj != null) {
                return (Map<Long, Integer>) resultObj;
            }
            List<Category> list = super.list();
            if (CollectionUtil.isEmpty(list)) {
                return result;
            }
            Map<Long, List<Category>> map = list.stream().collect(Collectors.groupingBy(Category::getParentId));
            for (Map.Entry<Long, List<Category>> entry : map.entrySet()) {
                result.put(entry.getKey(), entry.getValue().size());
            }
            //一级分类
            List<Category> firstCategory = map.get(CourseConstants.CATEGORY_ROOT);
            if (CollUtils.isNotEmpty(firstCategory)) {
                firstCategory.forEach(category -> {
                    List<Category> categories = map.get(category.getId());
                    //当前一级分类没有二级分类
                    if (CollUtils.isEmpty(categories)) {
                        result.put(category.getId(), 0);
                        return;
                    }
                    //当前一级分类有二级分类
                    long sum = categories.stream().collect(Collectors.summarizingInt(c -> NumberUtils.null2Zero(result.get(c.getId())))).getSum();
                    result.put(category.getId(), (int) sum);
                });
            }
            redisTemplate.opsForValue().set(RedisConstants.REDIS_KEY_CATEGORY_THIRD_NUMBER, result, 3, TimeUnit.MINUTES);
        }
        return result;
    }

    /**
     * 递归过滤出查询的数据，当前分类是否符合条件 = 当前分类信息符合条件 OR 有符合条件的子分类
     * 1.校验信息状态和名称符合dto要求
     * 2.循环遍历子分类，子分类不符合条件将从子分类列表中删除
     * 3.步骤1中通过 + 是否还有子分类（步骤2删除了不符合条件的）
     *
     * @param categoryVO
     * @param categoryListDTO
     * @return 当前分类是否符合条件
     */
    private boolean filter(CategoryVO categoryVO, CategoryListDTO categoryListDTO) {

        //当前分类通过，或者子分类有一个通过则都通过
        //不需要过滤
        if (StringUtils.isEmpty(categoryListDTO.getName()) && categoryListDTO.getStatus() == null) {
            return true;
        }
        boolean pass = true;
        // 状态校验
        if (categoryListDTO.getStatus() != null) { //和查询状态一致pass
            pass = (categoryVO.getStatus() == categoryListDTO.getStatus());
        }
        //名称校验
        if (pass && StringUtils.isNotEmpty(categoryListDTO.getName())) {//状态pass通过后校验名称，包含名称关键字 通过
            pass = StringUtils.isNotEmpty(categoryVO.getName()) && categoryVO.getName().contains(categoryListDTO.getName());
        }
        //分类信息校验未通过，并且没有子分类，当前分类不符合条件
        if (!pass && CollUtils.isEmpty(categoryVO.getChildren())) { //告诉上一级没通过
            return false;
        }
        //遍历子分类是否符合条件
        for (int count = categoryVO.getChildren().size() - 1; count >= 0; count--) {
            CategoryVO child = categoryVO.getChildren().get(count);
            //子分类校验
            boolean childPass = filter(child, categoryListDTO);
            if (!childPass) { //子分类不符合条件，从子分类列表中删除
                categoryVO.getChildren().remove(count);
            }
        }
        return pass || CollUtils.isNotEmpty(categoryVO.getChildren());
    }
}
