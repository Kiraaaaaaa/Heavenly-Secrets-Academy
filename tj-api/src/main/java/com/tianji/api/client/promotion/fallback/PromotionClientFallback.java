package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;

@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    //降级后返回的对象
    @Override
    public PromotionClient create(Throwable cause) {
        log.error("远程调用Promotion服务出错", cause);
        return orderCourses -> null;
    }
}
