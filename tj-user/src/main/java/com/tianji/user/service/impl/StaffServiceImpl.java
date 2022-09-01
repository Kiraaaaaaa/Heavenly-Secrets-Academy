package com.tianji.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.user.domain.po.UserDetail;
import com.tianji.user.domain.query.UserPageQuery;
import com.tianji.user.domain.vo.StaffVO;
import com.tianji.common.enums.UserType;
import com.tianji.user.service.IStaffService;
import com.tianji.user.service.IUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 员工详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@Service
public class StaffServiceImpl implements IStaffService {

    @Autowired
    private IUserDetailService detailService;

    @Override
    public PageDTO<StaffVO> queryStaffPage(UserPageQuery query) {
        // 1.搜索
        Page<UserDetail> p = detailService.queryUserDetailByPage(query, UserType.STAFF);
        // 2.处理vo
        return PageDTO.of(p, StaffVO.class);
    }
}
