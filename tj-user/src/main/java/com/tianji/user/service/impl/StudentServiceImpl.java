package com.tianji.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.RandomUtils;
import com.tianji.user.constants.UserConstants;
import com.tianji.user.domain.dto.StudentFormDTO;
import com.tianji.user.domain.po.User;
import com.tianji.user.domain.po.UserDetail;
import com.tianji.user.domain.query.UserPageQuery;
import com.tianji.user.domain.vo.StudentPageVo;
import com.tianji.common.enums.UserType;
import com.tianji.user.service.IStudentService;
import com.tianji.user.service.IUserDetailService;
import com.tianji.user.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学员详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@Service
public class StudentServiceImpl implements IStudentService {

    @Autowired
    private IUserService userService;
    @Autowired
    private IUserDetailService detailService;

    @Override
    @Transactional
    public void saveStudent(StudentFormDTO studentFormDTO) {
        // 1.新增用户账号
        User user = new User();
        user.setCellPhone(studentFormDTO.getCellPhone());
        user.setPassword(studentFormDTO.getPassword());
        user.setType(UserType.STUDENT);
        userService.addUserByPhone(user, studentFormDTO.getCode());

        // 2.新增学员详情
        UserDetail student = new UserDetail();
        student.setId(user.getId());
        student.setName(RandomUtils.randomString(8));
        student.setRoleId(UserConstants.STUDENT_ROLE_ID);
        detailService.save(student);
    }

    @Override
    public void updateMyPassword(StudentFormDTO studentFormDTO) {
        userService.updatePasswordByPhone(
                studentFormDTO.getCellPhone(), studentFormDTO.getCode(), studentFormDTO.getPassword()
        );
    }

    @Override
    public PageDTO<StudentPageVo> queryStudentPage(UserPageQuery query) {
        // 1.分页条件
        Page<UserDetail> page  =  detailService.queryUserDetailByPage(query, UserType.STUDENT);
        List<UserDetail> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 2.处理vo
        List<StudentPageVo> list = new ArrayList<>(records.size());
        for (UserDetail r : records) {
            StudentPageVo v = BeanUtils.toBean(r, StudentPageVo.class);
            list.add(v);
        }
        // TODO 3.查询购买的课程数量
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }
}
