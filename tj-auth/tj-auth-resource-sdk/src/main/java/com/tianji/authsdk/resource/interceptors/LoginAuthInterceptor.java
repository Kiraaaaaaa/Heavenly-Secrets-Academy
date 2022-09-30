package com.tianji.authsdk.resource.interceptors;

import com.tianji.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
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
            /*if(WebUtils.isGatewayRequest()){
                // 2.1.网关访问微服务，未登录需要返回R包裹的数据
                response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
                WebUtils.setResponseHeader(Constant.BODY_PROCESSED_MARK_HEADER, "true");
                response.getWriter().write(JsonUtils.toJsonStr(R.error(401, AuthErrorInfo.Msg.UNAUTHORIZED)));
            }else {
                // 2.2.微服务之间访问，直接返回401
                response.setStatus(401);
                response.sendError(401, "未登录用户无法访问！");
            }*/
            response.setStatus(401);
            response.sendError(401, "未登录用户无法访问！");
            // 2.3.未登录，直接拦截
            return false;
        }
        // 3.登录则放行
        return true;
    }
}
