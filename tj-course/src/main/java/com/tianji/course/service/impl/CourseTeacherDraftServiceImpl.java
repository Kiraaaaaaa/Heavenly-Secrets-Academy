package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.course.constants.CourseConstants;
import com.tianji.course.domain.dto.CourseTeacherSaveDTO;
import com.tianji.course.domain.po.CourseTeacher;
import com.tianji.course.domain.po.CourseTeacherDraft;
import com.tianji.course.domain.vo.CourseTeacherVO;
import com.tianji.course.mapper.CourseTeacherDraftMapper;
import com.tianji.course.service.ICourseDraftService;
import com.tianji.course.service.ICourseTeacherDraftService;
import com.tianji.course.service.ICourseTeacherService;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 课程老师关系表草稿 服务实现类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-20
 */
@Service
public class CourseTeacherDraftServiceImpl extends ServiceImpl<CourseTeacherDraftMapper, CourseTeacherDraft> implements ICourseTeacherDraftService {

    @Autowired
    private ICourseDraftService courseDraftService;

    @Autowired
    private ICourseTeacherService courseTeacherService;

    @Autowired
    private UserClient userClient;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void save(CourseTeacherSaveDTO courseTeacherSaveDTO) {
        LambdaUpdateWrapper<CourseTeacherDraft> updateWrappe = new LambdaUpdateWrapper<>();
        updateWrappe.eq(CourseTeacherDraft::getCourseId, courseTeacherSaveDTO.getId());
        baseMapper.delete(updateWrappe);

        List<CourseTeacherDraft> courseTeacherDrafts = BeanUtils.copyList(courseTeacherSaveDTO.getTeachers(),
                CourseTeacherDraft.class, (teacherInfo, teacherDraft) -> {
                    teacherDraft.setCourseId(courseTeacherSaveDTO.getId());
                    teacherDraft.setTeacherId(teacherInfo.getId());
                    teacherDraft.setCIndex(courseTeacherSaveDTO.getTeachers().indexOf(teacherInfo));
                });
        saveBatch(courseTeacherDrafts);
        courseDraftService.updateStep(courseTeacherSaveDTO.getId(), CourseConstants.CourseStep.TEACHER);
    }

    @Override
    public List<CourseTeacherVO> queryTeacherOfCourse(Long courseId, Boolean see) {
        if (see) { //用于查看，先查架上数据，再查草稿
            List<CourseTeacherVO> courseTeacherVOS = courseTeacherService.queryTeachers(courseId);
            if(CollUtils.isNotEmpty(courseTeacherVOS)){
                return courseTeacherVOS;
            }
            courseTeacherVOS = queryTeachers(courseId);
            return CollUtils.isEmpty(courseTeacherVOS) ? new ArrayList<>() : courseTeacherVOS;
        } else { //编辑数据先查草稿
            return queryTeachers(courseId);
        }
    }

    @Override
    @Transactional(rollbackFor = {DbException.class,Exception.class})
    public void copyToShelf(Long courseId, Boolean isFirstShelf) {
        //1.先将草稿中的数据查出来
        LambdaQueryWrapper<CourseTeacherDraft> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacherDraft::getCourseId, courseId);
        List<CourseTeacherDraft> courseTeacherDrafts = baseMapper.selectList(queryWrapper);
        //2.删除架上的老师数据，
        if(!isFirstShelf) {
            courseTeacherService.deleteByCourseId(courseId);
        }
        //3.将草稿上架
        List<CourseTeacher> courseTeachers = BeanUtils.copyList(courseTeacherDrafts, CourseTeacher.class);
        courseTeacherService.saveOrUpdateBatch(courseTeachers);
        //4.删除草稿
        if(baseMapper.deleteByCourseId(courseId) <= 0){
            throw new DbException(ErrorInfo.Msg.DB_DELETE_EXCEPTION);
        }
    }

    @Override
    public void copyToDraft(Long courseId) {

    }

    private List<CourseTeacherVO> queryTeachers(Long couserId) {
        LambdaQueryWrapper<CourseTeacherDraft> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacherDraft::getCourseId, couserId);
        List<CourseTeacherDraft> courseTeacherDrafts = baseMapper.selectList(queryWrapper);
        if(CollUtils.isEmpty(courseTeacherDrafts)){
            return new ArrayList<>();
        }

        List<UserDTO> UserDTOS = userClient.queryUserByIds(
                courseTeacherDrafts.stream().map(CourseTeacherDraft::getTeacherId).collect(Collectors.toList()));
        Map<Long, UserDTO> UserDTOMap = UserDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO -> UserDTO));


        return BeanUtils.copyList(courseTeacherDrafts,CourseTeacherVO.class, (courseTeacher, courseTeacherVO) -> {
            UserDTO UserDTO = UserDTOMap.get(courseTeacher.getTeacherId());
            if (UserDTO != null) {
                courseTeacherVO.setIcon(UserDTO.getPhoto());
                courseTeacherVO.setName(UserDTO.getName());
                courseTeacherVO.setIntroduce(UserDTO.getIntro());
                courseTeacherVO.setJob(UserDTO.getJob());
            }
            //老师id
            courseTeacherVO.setId(courseTeacher.getTeacherId());
        });
    }

}
