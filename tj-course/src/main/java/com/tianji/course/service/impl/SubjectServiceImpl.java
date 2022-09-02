package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.Constant;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.course.constants.CourseErrorInfo;
import com.tianji.course.constants.SubjectConstants;
import com.tianji.course.domain.dto.SubjectPageParamDTO;
import com.tianji.course.domain.dto.SubjectSaveDTO;
import com.tianji.course.domain.po.CourseSubject;
import com.tianji.course.domain.po.Subject;
import com.tianji.course.domain.po.SubjectCategory;
import com.tianji.course.domain.vo.CateSimpleInfoVO;
import com.tianji.course.domain.vo.SubjectInfoVO;
import com.tianji.course.domain.vo.SubjectSimpleVO;
import com.tianji.course.domain.vo.SubjectVO;
import com.tianji.course.mapper.CourseCataSubjectMapper;
import com.tianji.course.mapper.CourseSubjectMapper;
import com.tianji.course.mapper.SubjectCategoryMapper;
import com.tianji.course.mapper.SubjectMapper;
import com.tianji.course.service.ICategoryService;
import com.tianji.course.service.ISubjectService;
import com.tianji.course.utils.SubjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 题目 服务实现类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-15
 */
@Service
public class SubjectServiceImpl extends ServiceImpl<SubjectMapper, Subject> implements ISubjectService {

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private SubjectCategoryMapper subjectCategoryMapper;

    @Autowired
    private CourseSubjectMapper courseSubjectMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private CourseCataSubjectMapper courseCataSubjectMapper;

    @Override
    public PageDTO<SubjectVO> page(SubjectPageParamDTO subjectPageParamDTO, PageQuery pageQuery) {
        //分查询条件
        QueryWrapper<Subject> queryWrapper = new QueryWrapper<>();
        //题目类型
        queryWrapper.lambda().in(StringUtils.isNotEmpty(subjectPageParamDTO.getSubjectTypes()),
                Subject::getSubjectType, StringUtils.split(subjectPageParamDTO.getSubjectTypes(), ","))
                //题目名称
                .like(StringUtils.isNotEmpty(subjectPageParamDTO.getName()), Subject::getName, subjectPageParamDTO.getName())
                //仅我录入的
                .and(
                        BooleanUtils.isTrue(subjectPageParamDTO.getOwn()),
                        subjectLambdaQueryWrapper ->
                                subjectLambdaQueryWrapper.or().eq(Subject::getCreater, UserContext.getUser())
                                        .or().eq(Subject::getUpdater, UserContext.getUser())
                )
                //难度
                .eq(subjectPageParamDTO.getDifficulty() != null, Subject::getDifficulty, subjectPageParamDTO.getDifficulty())
                .eq(Subject::getDeleted, Constant.DATA_NOT_DELETE);
        //题目分类 SubjectCategory联表查询条件， sc表示联表查询中SubjectCategory前缀
        queryWrapper.in(CollUtils.isNotEmpty(subjectPageParamDTO.getThirdCateIds()), "sc.third_cate_id", subjectPageParamDTO.getThirdCateIds());


        //分页查询数据
        Page<Subject> subjectPage = this.baseMapper.listForPage(pageQuery.toMpPage(), queryWrapper);
        if (CollUtils.isEmpty(subjectPage.getRecords())) {
            //返回空数据
            return PageDTO.empty(subjectPage);
        }
        List<Long> userIds = subjectPage.getRecords().stream().map(Subject::getUpdater).collect(Collectors.toList());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, String> userIdAndNameMap = CollUtils.isEmpty(userDTOS) ?
                new HashMap<>() : userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        //题目id列表
        List<Long> ids = subjectPage.getRecords().stream().map(Subject::getId).collect(Collectors.toList());
        //查询题目与课程分类关系
        List<SubjectCategory> subCates = this.baseMapper.listSubjectCategoryById(ids);
        //根据题目id将课程和题目关系分组
        Map<Long, List<SubjectCategory>> subCateMap = subCates.stream().collect(Collectors.groupingBy(SubjectCategory::getSubjectId));
        //课程分类id和name关系
        Map<Long, String> cateIdAndName = categoryService.getCateIdAndName();

        return PageDTO.of(subjectPage, SubjectVO.class, (subject, subjectVO) -> {
            //课程分类信息
            subjectVO.setCates(toCates(subCateMap.get(subject.getId()), cateIdAndName));
            //答案转化成list
            subjectVO.setAnswers(CollUtils.convertToInteger(StringUtils.split(subject.getAnswer(), ",")));
            //选项转换成list
            subjectVO.setOptions(SubjectUtils.getOptions(subject));
            //题目难度描述
            subjectVO.setDifficultDesc(SubjectConstants.Difficult.desc(subject.getDifficulty()));
            //题目类型描述
            subjectVO.setSubjectTypeDesc(SubjectConstants.Type.desc(subject.getSubjectType()));
            //更新人
            subjectVO.setUpdaterName(userIdAndNameMap.get(subject.getUpdater()));
        });
    }

    @Override
    public SubjectInfoVO get(Long id) {
        //题目信息
        Subject subject = this.baseMapper.selectById(id);
        if (subject == null) { //未查询到课程信息
            return new SubjectInfoVO();
        }
        //查询题目课程分类关系
        LambdaQueryWrapper<SubjectCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SubjectCategory::getSubjectId, id);
        List<SubjectCategory> subjectCategories = subjectCategoryMapper.selectList(queryWrapper);
        //所有课程分类id和name的映射关系
        Map<Long, String> cateIdAndName = categoryService.getCateIdAndName();


        LambdaQueryWrapper<CourseSubject> csQueryWrapper = new LambdaQueryWrapper<>();
        csQueryWrapper.eq(CourseSubject::getSubjectId, id);
        List<CourseSubject> courseSubjects = courseSubjectMapper.selectList(csQueryWrapper);
        List<Long> courseIds = new ArrayList<>();
        if (CollUtils.isNotEmpty(courseSubjects)) {
            courseIds.addAll(courseSubjects.stream().map(CourseSubject::getCourseId).collect(Collectors.toList()));
        }

        //题目PO转换成题目VO
        return BeanUtils.copyBean(subject, SubjectInfoVO.class, (sub, subjectInfoVO) -> {
            subjectInfoVO.setCates(BeanUtils.copyList(subjectCategories, CateSimpleInfoVO.class, (sc, csv) -> {
                csv.setFirstCateName(cateIdAndName.get(sc.getFirstCateId()));
                csv.setSecondCateName(cateIdAndName.get(sc.getSecondCateId()));
                csv.setThirdCateName(cateIdAndName.get(sc.getThirdCateId()));
            }));
            subjectInfoVO.setCourseIds(courseIds);
            subjectInfoVO.setOptions(SubjectUtils.getOptions(sub));
            subjectInfoVO.setAnswers(CollUtils.convertToInteger(StringUtils.split(sub.getAnswer(), ",")));
        });
    }

    @Override
    @Transactional(rollbackFor = {DbException.class, Exception.class})
    public void save(SubjectSaveDTO subjectSaveDTO) {

        //校验名称是否已经存在
        check(subjectSaveDTO.getName(), subjectSaveDTO.getId());

        //将题目组装成数据po
        Subject subject = BeanUtils.copyBean(subjectSaveDTO, Subject.class, (ssDTO, st) -> {
            st.setAnswer(CollUtils.join(ssDTO.getAnswers(), ","));
            //将答案中的选项列表转化成题目中的选项
            SubjectUtils.setOptions(st, ssDTO.getOptions());
        });

        if (subject.getId() == null) { //新增
            int result = this.baseMapper.insert(subject);
            if (result <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_SAVE_EXCEPTION);
            }
        } else { //编辑
            int result = this.baseMapper.updateById(subject);
            if (result <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
            }
            //将原有的题目和课程分类之间的联系删除
            LambdaUpdateWrapper<SubjectCategory> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(SubjectCategory::getSubjectId, subject.getId());
            int deleteResult = subjectCategoryMapper.delete(updateWrapper);
            if (deleteResult <= 0) {
                throw new DbException(ErrorInfo.Msg.DB_UPDATE_EXCEPTION);
            }
            //删除题目和课程的关系
            LambdaUpdateWrapper<CourseSubject> csQueryWrapper = new LambdaUpdateWrapper<>();
            csQueryWrapper.eq(CourseSubject::getSubjectId, subject.getId());
            //不需要校验删除了多少，题目并非强制有课程
            courseSubjectMapper.delete(csQueryWrapper);

        }
        //题目和课程分类之间的关系
        List<SubjectCategory> subjectCategories = BeanUtils.copyList(subjectSaveDTO.getCates(), SubjectCategory.class, (array, csi) -> {
            csi.setSubjectId(subject.getId());
            if (array.size() < 2) {
                throw new BizIllegalException(CourseErrorInfo.Msg.SUBJECT_SAVE_CATEGORY_INCOMPLETE);
            }
            csi.setFirstCateId(array.get(0)); //课程一级分类
            csi.setSecondCateId(array.get(1)); //课程二级分类
            csi.setThirdCateId(array.get(2)); //课程三级分类
        });


        //批量插入题目和课程分类的关系
        int result = subjectCategoryMapper.batchInsert(subjectCategories);
        if (result != subjectCategories.size()) {
            throw new BizIllegalException(ErrorInfo.Msg.OPERATE_FAILED);
        }
        if (CollUtils.isNotEmpty(subjectSaveDTO.getCourseIds())) {
            List<CourseSubject> courseSubjects = subjectSaveDTO.getCourseIds().stream().
                    map(courseId -> {
                        return new CourseSubject(null, courseId, subject.getId());
                    })
                    .collect(Collectors.toList());
            if (courseSubjectMapper.batchInsert(courseSubjects) != courseSubjects.size()) {
                throw new BizIllegalException(ErrorInfo.Msg.OPERATE_FAILED);
            }

        }


    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public void delete(Long id) {
        //查询课程校验是否可以删除课程
        Subject subject = this.baseMapper.selectById(id);
        if (subject == null) { //要删除的课程不在
            throw new DbException(ErrorInfo.Msg.DB_DELETE_EXCEPTION);
        }
        if (subject.getUseTimes() != null && subject.getUseTimes() > 0) { //当前课程被引用不能删除
            throw new BizIllegalException(CourseErrorInfo.Msg.SUBJECT_NO_DELETE_BY_USED);
        }
        //删除课程
        int result = this.baseMapper.deleteById(id);
        if (result <= 0) {
            throw new DbException(ErrorInfo.Msg.DB_DELETE_EXCEPTION);
        }
    }

    @Override
    public Map<Long, Long> countSubjectNumOfTeacher(List<Long> teacherIds) {

        if (CollUtils.isEmpty(teacherIds)) {
            return new HashMap<>();
        }

        LambdaQueryWrapper<Subject> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Subject::getCreater, teacherIds);
        List<Subject> subjects = baseMapper.selectList(queryWrapper);

        return CollUtils.isEmpty(subjects) ? new HashMap<>() :
                subjects.stream().collect(Collectors.groupingBy(Subject::getCreater, Collectors.counting()));
    }

    @Override
    public List<SubjectSimpleVO> queryByCataId(Long cataId) {

        //获取题目id列表
        List<Long> subjectIdList = courseCataSubjectMapper.querySubjectIdByCataId(cataId);
        if (CollUtils.isEmpty(subjectIdList)) {
            return new ArrayList<>();
        }
        //获取题目
        List<Subject> subjectList = baseMapper.selectBatchIds(subjectIdList);
        return BeanUtils.copyList(subjectList, SubjectSimpleVO.class,(subject,subjectVO)->{
            subjectVO.setOptions(SubjectUtils.getOptions(subject));
        });
    }


    /**
     * 组装题目的课程分类信息
     *
     * @param subjectCategories
     * @param cateIdAndName
     * @return
     */
    private List<String> toCates(List<SubjectCategory> subjectCategories, Map<Long, String> cateIdAndName) {
        if (CollUtils.isEmpty(subjectCategories)) {
            return new ArrayList<>();
        }
        //todo 分隔符
        String separator1 = "/";
        String separator2 = "、";
        //根据二级课程分类进行分组，课程分类展示格式 /一级课程分类/二级课程分类/三级课程分类A、三级课程分类B，
        //要想组装成这种效果，需要根据二级课程分类进行分组，然后再进行拼装就可以达到这种效果
        Map<Long, List<SubjectCategory>> collect = subjectCategories.stream().collect(Collectors.groupingBy(SubjectCategory::getSecondCateId));
        List<String> cateNames = new ArrayList<>();
        for (Map.Entry<Long, List<SubjectCategory>> entry : collect.entrySet()) {

            List<SubjectCategory> value = entry.getValue();
            SubjectCategory subjectCategory = value.get(0);
            //一级分类名称
            StringBuffer buffer = new StringBuffer(cateIdAndName.get(subjectCategory.getFirstCateId())).append(separator1);
            //二级分类名称
            buffer.append(cateIdAndName.get(subjectCategory.getSecondCateId())).append(separator1);

            //三级分类名称 多个用逗号隔开
            for (SubjectCategory sc : entry.getValue()) {
                buffer.append(cateIdAndName.get(sc.getThirdCateId())).append(separator2);
            }
            cateNames.add(buffer.substring(0, buffer.length() - 1));
        }
        return cateNames;
    }

    /**
     * 校验题目名称是否已经存在
     *
     * @param name
     * @param id
     */
    private void check(String name, Long id) {
        LambdaQueryWrapper<Subject> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Subject::getName, name);
        List<Subject> subjects = this.baseMapper.selectList(queryWrapper);
        //新增数据并且名称已经存在
        if (CollUtils.isNotEmpty(subjects) && id == null) {
            throw new BizIllegalException(CourseErrorInfo.Msg.SUBJECT_NAME_EXEISTS);
        }
        //更新并且名称不是当前题目的名称
        if (CollUtils.isNotEmpty(subjects) && subjects.get(0).getId() != id) {
            throw new BizIllegalException(CourseErrorInfo.Msg.SUBJECT_NAME_EXEISTS);
        }
    }
}
