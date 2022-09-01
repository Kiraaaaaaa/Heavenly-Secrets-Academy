package com.tianji.authsdk.resource.interceptors;

import com.tianji.auth.common.constants.AuthErrorInfo;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.R;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.common.utils.WebUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.尝试获取用户信息
        Long userId = UserContext.getUser();
        // 2.判断是否登录
        if (userId == null) {
            if(WebUtils.isGatewayRequest()){
                response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
                WebUtils.setResponseHeader(Constant.BODY_PROCESSED_MARK_HEADER, "true");
                response.getWriter().write(JsonUtils.toJsonStr(R.error(401, AuthErrorInfo.Msg.UNAUTHORIZED)));
            }else {
                response.setStatus(401);
                response.sendError(401, "未登录用户无法访问！");
            }
            return false;
        }
        // 3.登录则放行
        return true;
    }
}
