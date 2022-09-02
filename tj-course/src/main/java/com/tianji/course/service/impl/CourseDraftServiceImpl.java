package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.order.OrderClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CourseSearchDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.course.constants.CourseConstants;
import com.tianji.course.constants.CourseErrorInfo;
import com.tianji.api.constants.CourseStatus;
import com.tianji.course.domain.dto.CourseBaseInfoSaveDTO;
import com.tianji.course.domain.po.*;
import com.tianji.course.domain.query.CoursePageQuery;
import com.tianji.course.domain.vo.CourseBaseInfoVO;
import com.tianji.course.domain.vo.CoursePageVO;
import com.tianji.course.domain.vo.CourseSaveVO;
import com.tianji.course.mapper.*;
import com.tianji.course.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.ValidatorFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 草稿课程 服务实现类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-18
 */
@Service
public class CourseDraftServiceImpl extends ServiceImpl<CourseDraftMapper, CourseDraft> implements ICourseDraftService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ICourseService courseService;

    @Autowired
    private CourseContentDraftMapper courseContentDraftMapper;

    @Autowired
    private CourseContentMapper courseContentMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ValidatorFactory validatorFactory;

    @Autowired
    private ICourseCatalogueDraftService courseCatalogueDraftService;

    @Autowired
    private ICourseTeacherDraftService courseTeacherDraftService;

    @Autowired
    private CourseCatalogueDraftMapper courseCatalogueDraftMapper;

    @Autowired
    private CourseTeacherDraftMapper courseTeacherDraftMapper;

    @Autowired
    private CourseCataSubjectDraftMapper courseCataSubjectDraftMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private RabbitMqHelper rabbitMqHelper;

    @Autowired
    private OrderClient orderClient;


    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public CourseSaveVO save(CourseBaseInfoSaveDTO courseBaseInfoSaveDTO) {

        //数据校验
        if (courseBaseInfoSaveDTO.getId() == null) { //新增数据需要校验参数
            ViolationUtils.process(validatorFactory.getValidator().validate(courseBaseInfoSaveDTO));
        } else {
            Course course = courseMapper.selectById(courseBaseInfoSaveDTO.getId());
            if (course == null) { //修改数据，但是数据还在草稿阶段，需要校验数据
                ViolationUtils.process(validatorFactory.getValidator().validate(courseBaseInfoSaveDTO));
            }
        }

        CourseDraft courseDraft = null;
        //内容草稿中没有重要信息，可以直接从DTO中设置
        CourseContentDraft courseContentDraft = new CourseContentDraft();
        courseContentDraft.setCourseIntroduce(courseBaseInfoSaveDTO.getIntroduce());
        courseContentDraft.setCourseDetail(courseBaseInfoSaveDTO.getDetail());
        courseContentDraft.setUsePeople(courseBaseInfoSaveDTO.getUsePeople());


        //将已经上架的课程信息copy到草稿中
        if (courseBaseInfoSaveDTO.getId() != null) {
            Course course = courseMapper.selectById(courseBaseInfoSaveDTO.getId());
            if (course != null) { //课程已经上架
                courseDraft = BeanUtils.toBean(course, CourseDraft.class);
            }
        }

        if (courseDraft != null) { //课程已经上架，设置参数
            //已经上架的课程可以修改封
            courseDraft.setCoverUrl(courseBaseInfoSaveDTO.getCoverUrl());
        } else {
            //只校验正式环境的名称
            int countSameNameNum = courseMapper.countSameName(courseBaseInfoSaveDTO.getName());
            if (countSameNameNum > 0) { //名称已经存在，提交时做双重校验
                throw new BadRequestException(CourseErrorInfo.Msg.COURSE_SAVE_NAME_EXISTS);
            }
            countSameNameNum = baseMapper.countByNameAndId(courseBaseInfoSaveDTO.getName(), courseBaseInfoSaveDTO.getId());
            if(countSameNameNum > 0){
                throw new BadRequestException(CourseErrorInfo.Msg.COURSE_SAVE_NAME_EXISTS);
            }
            courseDraft = BeanUtils.toBean(courseBaseInfoSaveDTO, CourseDraft.class);
            if (courseBaseInfoSaveDTO.getId() == null) {
                long id = IdWorker.getId();
                //课程id和内容id共用同一个id
                courseDraft.setId(id);
                courseContentDraft.setId(id);
                courseDraft.setStep(CourseConstants.CourseStep.BASE_INFO);
            }
            //三级分类
            Category thirdCategory = categoryMapper.selectById(courseBaseInfoSaveDTO.getThirdCateId());
            if (thirdCategory == null) {
                throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_SAVE_CATEGORY_NOT_FOUND);
            }
            //二级分类
            Category secondCategory = categoryMapper.selectById(thirdCategory.getParentId());
            if(secondCategory == null){
                throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_SAVE_CATEGORY_NOT_FOUND);
            }
            courseDraft.setFirstCateId(secondCategory.getParentId()); //一级分类id
            courseDraft.setSecondCateId(secondCategory.getId()); //二级分类id
            //数据库中存的是以分为单位
            courseDraft.setPrice(NumberUtils.null2Zero(courseBaseInfoSaveDTO.getPrice()));
            courseDraft.setMediaDuration(courseBaseInfoSaveDTO.getValidDuration());
            courseDraft.setStatus(CourseStatus.NO_UP_SHELF.getValue());
        }

        if (courseBaseInfoSaveDTO.getId() == null) {
            courseContentDraft.setId(courseDraft.getId());
            baseMapper.insert(courseDraft);
            courseContentDraftMapper.insert(courseContentDraft);
        } else {
            courseContentDraft.setId(courseDraft.getId());
            baseMapper.updateById(courseDraft);
            courseContentDraftMapper.updateById(courseContentDraft);
        }

        Course course = courseMapper.selectById(courseDraft.getId());
        if(course == null) {
            rabbitMqHelper.send(MqConstants.Exchange.COURSE_EXCHANGE, MqConstants.Key.COURSE_NEW_KEY, courseDraft.getId());
        }

        return CourseSaveVO.builder().id(courseDraft.getId()).build();
    }

    @Override
    public CourseBaseInfoVO getCourseBaseInfo(Long id, Boolean see) {

        CourseBaseInfoVO courseBaseInfoVO = null;
        Long creater = null;
        if (see) { //查看页面需要先查询正式数据，正式数据没有，再查草稿
            Course course = courseMapper.selectById(id);
            if (course != null) {
                courseBaseInfoVO = BeanUtils.toBean(course, CourseBaseInfoVO.class);
                CourseContent courseContent = courseContentMapper.selectById(id);
                courseBaseInfoVO.setCoureScore(NumberUtils.div(Math.random() * 1 + 4, 1,2)); //todo 评分默认
                courseBaseInfoVO.setEnrollNum(10); //todo 做完订单再做报名人数
                courseBaseInfoVO.setStudyNum(10); //todo 做完订单再做学习人数
                courseBaseInfoVO.setRefundNum(10); //做完退款再做退款人数
                courseBaseInfoVO.setRealPayAmount(1000); //做完支付再做实付金额



                courseBaseInfoVO.setDetail(courseContent.getCourseDetail());
                courseBaseInfoVO.setIntroduce(courseContent.getCourseIntroduce());
                courseBaseInfoVO.setUsePeople(courseContent.getUsePeople());
                courseBaseInfoVO.setCataTotalNum(course.getSectionNum());
                creater = course.getCreater();


            } else {
                CourseDraft courseDraft = baseMapper.selectById(id);
                if(courseDraft != null){
                    courseBaseInfoVO = BeanUtils.toBean(courseDraft, CourseBaseInfoVO.class);
                    CourseContentDraft courseContentDraft = courseContentDraftMapper.selectById(id);
                    courseBaseInfoVO.setDetail(courseContentDraft.getCourseDetail());
                    courseBaseInfoVO.setIntroduce(courseContentDraft.getCourseIntroduce());
                    courseBaseInfoVO.setUsePeople(courseContentDraft.getUsePeople());
                    courseBaseInfoVO.setCataTotalNum(courseDraft.getSectionNum());
                    creater = courseDraft.getCreater();
                }
            }
        } else { //编辑页面使用先查询草稿
            CourseDraft courseDraft = baseMapper.selectById(id);
            if (courseDraft != null) {
                courseBaseInfoVO = BeanUtils.toBean(courseDraft, CourseBaseInfoVO.class);
                CourseContentDraft courseContentDraft = courseContentDraftMapper.selectById(id);
                courseBaseInfoVO.setDetail(courseContentDraft.getCourseDetail());
                courseBaseInfoVO.setIntroduce(courseContentDraft.getCourseIntroduce());
                courseBaseInfoVO.setUsePeople(courseContentDraft.getUsePeople());
                creater = courseDraft.getCreater();
            }
        }
        if (courseBaseInfoVO == null) {
            return null;
        }
        //设置创建人
        if(creater != null && creater != 0){
            List<UserDTO> userDTOS = userClient.queryUserByIds(Collections.singletonList(creater));
            if(CollUtils.isNotEmpty(userDTOS)){
                courseBaseInfoVO.setCreaterName(userDTOS.get(0).getName());
            }
        }
        //分类信息
        List<Category> categories = categoryService.queryByIds(Arrays.asList(courseBaseInfoVO.getFirstCateId(),
                courseBaseInfoVO.getSecondCateId(), courseBaseInfoVO.getThirdCateId()));
        if(CollUtils.isNotEmpty(categories)){
            //分类id和名称关系
            Map<Long, String> categoryIdAndName = categories.stream().collect(Collectors.toMap(Category::getId, Category::getName));
            courseBaseInfoVO.setCateNames(
                    StringUtils.format("{}/{}/{}", categoryIdAndName.get(courseBaseInfoVO.getFirstCateId()),
                            categoryIdAndName.get(courseBaseInfoVO.getSecondCateId()), categoryIdAndName.get(courseBaseInfoVO.getThirdCateId()))
            );
        }
        return courseBaseInfoVO;
    }

    @Override
    public void updateStep(Long id, Integer step) {
        //1.查询课程草稿
        CourseDraft courseDraft = baseMapper.selectById(id);
        //2.设置课程步骤，课程步骤只能前进不能后退
        if(courseDraft.getStep() < step) {
            courseDraft.setStep(step);
        }
        //3.设置课时数，保存目录和保存题目两部进行修改
        if(CourseConstants.CourseStep.CATALOGUE == step ||
                CourseConstants.CourseStep.SUBJECT == step){
            //题目保存和目录保存都会修改课时数量
            courseDraft.setSectionNum(courseCatalogueDraftService.totalSectionNums(id));
        }
        baseMapper.updateById(courseDraft);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public void upShelf(Long id) {
        boolean isFirstUpShelf = false;
        //校验草稿当前是否可以提交
        CourseDraft courseDraft = baseMapper.selectById(id);
        Course course = courseMapper.selectById(id);
        if(courseDraft == null && course != null){
            //课程已经上架，不能重新上架
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_UP_SHELF_AREADY);
        }
        if(courseDraft == null){
            //课程已经不在
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_UP_SHELF_NOT_FOUND_COURSE);

        }
        //草稿信息不完整
        if (courseDraft.getStep() != CourseConstants.CourseStep.TEACHER) {
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_UP_SHELF_INFO_INCOMPLETE);
        }
        //课程
        //校验已经上架的课程状态是否已经下架
        if (course != null && course.getStatus() != CourseStatus.DOWN_SHELF.getValue()) {
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_UP_SHELF_STATE_WRONG);
        }
        //首次上架校验逻辑
        if (course == null) {
            isFirstUpShelf = true;
            int sameNameNum = courseMapper.countSameName(courseDraft.getName());
            if (sameNameNum > 0) {
                throw new BadRequestException(CourseErrorInfo.Msg.COURSE_SAVE_NAME_EXISTS);
            }
        }
        //校验目录数据是否完整， 小节是否上传视频  练习是否上传题目
        courseCatalogueDraftService.checkCataInfoImplated(id);

        //计算课程总时长
        Map<Long, Integer> mediaDurations = courseCatalogueDraftService.calculateMediaDuration(id);
        int totalMediaDuration = mediaDurations.values().stream().mapToInt(p -> p).sum();


        //4.整理数据分别copy或修改,并删除草稿数据
        //4.1课程老师信息
        courseTeacherDraftService.copyToShelf(id, isFirstUpShelf);
        //4.2 题目信息上架
        courseCatalogueDraftService.copySubjectToShelf(id, isFirstUpShelf);
        //4.3目录信息上架
        courseCatalogueDraftService.copyToShelf(id, isFirstUpShelf);
        //4.4 课程基本信息上架
        CourseContentDraft courseContentDraft = courseContentDraftMapper.selectById(id);
        CourseContent courseContent = BeanUtils.toBean(courseContentDraft, CourseContent.class);
        Course courseToShelf = BeanUtils.toBean(courseDraft, Course.class);
        courseToShelf.setStatus(CourseStatus.SHELF.getValue());
        courseToShelf.setMediaDuration(totalMediaDuration); //视频总时长
        courseToShelf.setValidDuration(courseDraft.getValidDuration());

        if (isFirstUpShelf) {
            int result = courseContentMapper.insert(courseContent);
            if (result <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
            }
            //还未统计每章的数据
            result = courseMapper.insert(courseToShelf);
            if (result <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
            }
            baseMapper.deleteById(id);
            courseContentDraftMapper.deleteById(id);
        } else {
            int result = courseContentMapper.updateById(courseContent);
            if (result <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
            }
            //还未统计每章的数据
            result = courseMapper.updateVariableById(courseToShelf);
            if (result <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
            }
            baseMapper.deleteById(id);
            courseContentDraftMapper.deleteById(id);

        }
        //上架mq广播
        rabbitMqHelper.send(MqConstants.Exchange.COURSE_EXCHANGE, MqConstants.Key.COURSE_UP_KEY, id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public void downShelf(Long id) {
        Course course = courseService.getById(id);
        if (course == null || !course.getStatus().equals(CourseStatus.SHELF.getValue())) {
            throw new BizIllegalException(CourseErrorInfo.Msg.COURSE_DOWN_SHELF_FAILD);
        }
        // 先更新课程状态
        courseService.updateStatus(id, CourseStatus.DOWN_SHELF.getValue());
        //1.课程基本信息和内容信息copy到草稿中
        baseMapper.insertFromCourse(id);
        //2.课程内容copy到草稿中
        courseContentDraftMapper.insertFromCourseContent(id);
        //3.目录内容copy到草稿中
        courseCatalogueDraftMapper.insertFromCourseCatalogue(id);
        //4.课程题目copy到草稿中
        courseCataSubjectDraftMapper.insertFromCourseCataSubject(id);
        //5.课程老师copy到草稿中
        courseTeacherDraftMapper.insertFromCourseTeacher(id);
        //下架mq广播
        rabbitMqHelper.send(MqConstants.Exchange.COURSE_EXCHANGE, MqConstants.Key.COURSE_DOWN_KEY, id);
    }

    @Override
    public CourseSearchDTO getCourseDTOById(Long id) {

        CourseDraft courseDraft = baseMapper.selectById(id);
        if (courseDraft == null) {
            return new CourseSearchDTO();
        }
        LambdaQueryWrapper<CourseTeacherDraft> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacherDraft::getCourseId, id)
                .orderByDesc(CourseTeacherDraft::getCIndex)
                .last(true, "limit 1");
        List<CourseTeacherDraft> courseTeacherDrafts = courseTeacherDraftMapper.selectList(queryWrapper);
        CourseSearchDTO courseSearchDTO = BeanUtils.toBean(courseDraft, CourseSearchDTO.class);
        courseSearchDTO.setCategoryIdLv1(courseDraft.getFirstCateId()); //一级分类id
        courseSearchDTO.setCategoryIdLv2(courseDraft.getSecondCateId()); //二级分类id
        courseSearchDTO.setCategoryIdLv3(courseDraft.getThirdCateId()); //三级分类id
        courseSearchDTO.setDuration(courseDraft.getMediaDuration()); //视频播放时长
        courseSearchDTO.setPublishTime(courseDraft.getCreateTime()); //创建时间
        courseSearchDTO.setSections(courseDraft.getSectionNum()); //小节或练习数量
        if (CollUtils.isNotEmpty(courseTeacherDrafts)) {
            courseSearchDTO.setTeacher(courseTeacherDrafts.get(0).getTeacherId());
        }else {
            courseSearchDTO.setTeacher(0L);
        }

        return courseSearchDTO;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public void delete(Long id) {
        //删除课程草稿
        baseMapper.deleteById(id);
        //删除课程内容草稿
        courseContentDraftMapper.deleteById(id);
        //删除课程题目关系草稿
        courseCataSubjectDraftMapper.deleteByCourseId(id);
        //删除课程目录草稿
        courseCatalogueDraftMapper.deleteByCourseId(id, Arrays.asList(
                CourseConstants.CataType.CHAPTER,
                CourseConstants.CataType.SECTION,
                CourseConstants.CataType.PRATICE
        ));
        //删除课程老师关系草稿
        courseTeacherDraftMapper.deleteByCourseId(id);
    }

    @Override
    public PageDTO<CoursePageVO> queryForPage(CoursePageQuery coursePageQuery) {
        //转换成查询条件
        LambdaQueryWrapper<CourseDraft> queryWrapper = SqlWrapperUtils.
                toLambdaQueryWrapper(coursePageQuery, CourseDraft.class);
        //课程更新时间查询条件
        queryWrapper.between(ObjectUtils.isNotEmpty(coursePageQuery.getBeginTime()) &&
                        ObjectUtils.isNotEmpty(coursePageQuery.getEndTime()), CourseDraft::getUpdateTime,
                coursePageQuery.getBeginTime(), coursePageQuery.getEndTime());
        //搜索关键字课程名称
        queryWrapper.like(StringUtils.isNotEmpty(coursePageQuery.getKeyword()),
                CourseDraft::getName, coursePageQuery.getKeyword());
        Page<CourseDraft> page = page(coursePageQuery.toMpPage(), queryWrapper);
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        //更新人
        List<Long> updaterList = page.getRecords().stream()
                .map(CourseDraft::getUpdater)
                .collect(Collectors.toList());
        //查询更新人用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(updaterList);
        Map<Long, String> updaterMap = CollUtils.isEmpty(updaterList) ? new HashMap<>()
                : userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        //课程分类
        List<Category> list = categoryService.list();
        Map<Long, String> categoryNameMap = CollUtils.isEmpty(list) ? new HashMap<>()
                : list.stream().collect(Collectors.toMap(Category::getId, Category::getName));
        //课程id列表
        List<Long> courseIdList = page.getRecords().stream()
                .map(CourseDraft::getId)
                .collect(Collectors.toList());
        //统计课程报名人数map
        Map<Long, Integer> peoNumOfCourseMap = orderClient.countEnrollNumOfCourse(courseIdList);

        return PageDTO.of(page, CoursePageVO.class, (course, coursePageVO) -> {
            //课程所属分类
            String categories = StringUtils.format("{}/{}/{}",
                    categoryNameMap.get(course.getFirstCateId()),
                    categoryNameMap.get(course.getFirstCateId()),
                    categoryNameMap.get(course.getFirstCateId()));
            coursePageVO.setCategories(categories);
            //更新人
            coursePageVO.setUpdaterName(updaterMap.get(course.getUpdater()));
            //报名人数
            coursePageVO.setSold(NumberUtils.null2Zero(peoNumOfCourseMap.get(course.getId())));
            //评分 已下架的才有评分，待上架没有评分，临时使用 todo
            if(CourseStatus.DOWN_SHELF.equalsValue(coursePageQuery.getStatus())){
                coursePageVO.setScore(40 + course.getSectionNum() % 5); //临时使用 todo
            }
            // 课时
            coursePageVO.setSections(course.getSectionNum());
        });
    }
}
