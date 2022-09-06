package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.course.domain.po.CourseTeacher;
import com.tianji.course.domain.vo.CourseTeacherVO;
import com.tianji.course.mapper.CourseTeacherMapper;
import com.tianji.course.service.ICourseTeacherService;
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
public class CourseTeacherServiceImpl extends ServiceImpl<CourseTeacherMapper, CourseTeacher> implements ICourseTeacherService {

    @Autowired
    private UserClient userClient;

    @Override
    public List<CourseTeacherVO> queryTeachers(Long couserId) {
        LambdaQueryWrapper<CourseTeacher> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacher::getCourseId, couserId);
        List<CourseTeacher> courseTeachers = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courseTeachers)) {
            return null;
        }
        List<UserDTO> UserDTOS = userClient.queryUserByIds(courseTeachers.stream().map(CourseTeacher::getTeacherId).collect(Collectors.toList()));
        Map<Long, UserDTO> UserDTOMap = UserDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO -> UserDTO));
        return BeanUtils.copyList(courseTeachers, CourseTeacherVO.class, (courseTeacher, courseTeacherVO) -> {
            UserDTO UserDTO = UserDTOMap.get(courseTeacher.getTeacherId());
            if (UserDTO != null) {
                courseTeacherVO.setIcon(UserDTO.getPhoto());
                courseTeacherVO.setName(UserDTO.getName());
                courseTeacherVO.setIntro(UserDTO.getIntro());
                courseTeacherVO.setJob(UserDTO.getJob());
            }
            //老师id
            courseTeacherVO.setId(courseTeacher.getTeacherId());
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {DbException.class, Exception.class})
    public void deleteByCourseId(Long courserId) {
        if (baseMapper.deleteByCourseId(courserId) <= 0) {
            throw new DbException(ErrorInfo.Msg.DB_DELETE_EXCEPTION);
        }
    }

    @Override
    public List<Long> getTeacherIdOfCourse(Long courseId) {
        LambdaQueryWrapper<CourseTeacher> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseTeacher::getCourseId, courseId);
        queryWrapper.orderByAsc(CourseTeacher::getCIndex);
        List<CourseTeacher> courseTeachers = baseMapper.selectList(queryWrapper);
        return CollUtils.isEmpty(courseTeachers) ? new ArrayList<>() :
                courseTeachers.stream().map(CourseTeacher::getTeacherId).collect(Collectors.toList());
    }

}
