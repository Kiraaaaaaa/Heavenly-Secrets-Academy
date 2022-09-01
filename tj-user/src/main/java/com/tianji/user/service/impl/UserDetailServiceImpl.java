package com.tianji.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.constants.Constant;
import com.tianji.common.utils.StringUtils;
import com.tianji.user.domain.po.UserDetail;
import com.tianji.user.domain.query.UserPageQuery;
import com.tianji.common.enums.UserType;
import com.tianji.user.mapper.UserDetailMapper;
import com.tianji.user.service.IUserDetailService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 教师详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-15
 */
@Service
public class UserDetailServiceImpl extends ServiceImpl<UserDetailMapper, UserDetail> implements IUserDetailService {

    @Override
    public UserDetail queryById(Long userId) {
        return getBaseMapper().queryById(userId);
    }

    @Override
    public List<UserDetail> queryByIds(List<Long> ids) {
        return getBaseMapper().queryByIds(ids);
    }

    @Override
    public Page<UserDetail> queryUserDetailByPage(UserPageQuery query, UserType type) {
        // 1.分页条件
        Page<UserDetail> p = new Page<>(query.getPageNo(), query.getPageSize());
        // 2.排序条件
        String sortBy = query.getSortBy();
        boolean isAsc = query.getIsAsc();
        if (StringUtils.isBlank(sortBy)) {
            sortBy = Constant.DATA_FIELD_NAME_CREATE_TIME;
            isAsc = false;
        }
        p.addOrder(new OrderItem(sortBy, isAsc));
        // 3.搜索条件
        Integer status = query.getStatus();
        String name = query.getName();
        String phone = query.getPhone();
        QueryWrapper<UserDetail> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(status != null, UserDetail::getStatus, status)
                .eq(StringUtils.isNotBlank(phone), UserDetail::getCellPhone, phone)
                .like(StringUtils.isNotBlank(name), UserDetail::getName, name);
        // 4.查询
        p = getBaseMapper().queryByPage(p, wrapper, type.getValue());
        // 5.返回
        return p;
    }
}
