package com.tianji.message.thirdparty;

import com.tianji.api.dto.sms.SmsInfoDTO;

/**
 * 第三方接口对接平台
 */
public interface ISmsHandler {

    /**
     * 发送短信
     */
    void send(SmsInfoDTO platformSmsInfoDTO);


}
