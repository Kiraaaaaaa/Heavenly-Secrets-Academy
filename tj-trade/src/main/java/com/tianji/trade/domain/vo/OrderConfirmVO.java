package com.tianji.trade.domain.vo;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "订单确认页信息")
public class OrderConfirmVO {
    @ApiModelProperty("订单id")
    private Long orderId;
    @ApiModelProperty("订单总金额")
    private Integer totalAmount;
    // @ApiModelProperty("优惠折扣金额")
    // private Integer discountAmount;
    //经过修改后，这里给前端返回的应是优惠券方案
    @ApiModelProperty("优惠券方案集合")
    private List<CouponDiscountDTO> discounts;
    @ApiModelProperty("订单中包含的课程")
    private List<OrderCourseVO> courses;
}
