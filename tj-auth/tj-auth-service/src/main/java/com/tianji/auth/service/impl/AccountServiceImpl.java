package com.tianji.auth.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.service.IAccountService;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.util.JwtTool;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 账号表，平台内所有用户的账号、密码信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountService{
    private final JwtTool jwtTool;
    private final UserClient userClient;
    private final ILoginRecordService loginRecordService;

    @Override
    public String login(LoginFormDTO loginDTO, boolean isStaff) {
        if (UserContext.getUser() != null) {
            // 已经登录的用户
            throw new BadRequestException("请勿重复登录");
        }

        // 1.查询并校验用户信息
        LoginUserDTO detail = userClient.queryUserDetail(loginDTO, isStaff);
        if (detail == null) {
            throw new BadRequestException("登录信息有误");
        }

        // 2.基于JWT生成登录token
        // 2.1.设置记住我标记
        detail.setRememberMe(loginDTO.getRememberMe());
        // 2.2.生成token
        String token = jwtTool.createToken(detail);

        // 3.计入登录信息表
        loginRecordService.loginSuccess(loginDTO.getCellPhone(), detail.getUserId());
        // 4.返回结果
        return token;
    }

    @Override
    public void logout() {
        // 删除jti
        jwtTool.cleanJtiCache();
    }
}
