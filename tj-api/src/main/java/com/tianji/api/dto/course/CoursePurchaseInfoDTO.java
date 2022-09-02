package com.tianji.api.dto.course;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程支付相关信息 课程状态
 * @author wusongsong
 * @since 2022/7/26 20:41
 * @version 1.0.0
 **/
@Data
@ApiModel("课程购买信息")
public class CoursePurchaseInfoDTO {
    @ApiModelProperty("课程id")
    private Long id;
    @ApiModelProperty("课程购买开始时间")
    private LocalDateTime purchaseStartTime;
    @ApiModelProperty("课程购买结束时间")
    private LocalDateTime purchaseEndTime;
    @ApiModelProperty("付费方式，是否支持免费")
    private Boolean free;
    @ApiModelProperty("价格")
    private Integer price;
    @ApiModelProperty("课程名称")
    private String name;
    @ApiModelProperty("课程封面")
    private String coverUrl;
    @ApiModelProperty("课程学习有效期")
    private Integer validDuration;
}
