package com.tianji.auth.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.common.domain.dto.LoginUserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.tianji.auth.common.constants.JwtConstants.JWT_TOKEN_TTL;

@Component
@RequiredArgsConstructor
public class JwtTool {
    private final StringRedisTemplate stringRedisTemplate;
    private final JWTSigner jwtSigner;

    /**
     * 创建 access-token
     * @param userDTO 用户信息
     * @return access-token
     */
    public String createToken(LoginUserDTO userDTO){
        // 1.生成jws
        return JWT.create()
                .setPayload(JwtConstants.PAYLOAD_USER_KEY, userDTO)
                .setExpiresAt(new Date(System.currentTimeMillis() + JWT_TOKEN_TTL.toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    /**
     * 创建刷新token
     * @param userDetail 用户信息
     * @return 刷新token
     */
    public String createRefreshToken(LoginUserDTO userDetail){
        // TODO
       return "";
    }

    /**
     * 解析刷新token
     * @param refreshToken 刷新token
     * @return 解析刷新token得到的用户信息
     */
    public LoginUserDTO parseRefreshToken(String refreshToken) {
        // TODO
        return null;
    }

    /**
     * 清理刷新refresh-token的jti，本质是refresh-token作废
     */
    public void cleanJtiCache() {
        // TODO
    }
}
