package com.tianji.learning.domain.vo;

import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "查询拥有课程时需要的信息")
public class LessonStatusVO {

    @ApiModelProperty("主键lessonId")
    private Long id;

    @ApiModelProperty("课程id")
    private Long courseId;

    @ApiModelProperty("课程状态，0-未学习，1-学习中，2-已学完，3-已失效")
    private LessonStatus status;

    @ApiModelProperty("总已学习章节数")
    private Integer learnedSections;

    @ApiModelProperty("课程购买时间")
    private LocalDateTime createTime;

    @ApiModelProperty("课程过期时间，如果为null代表课程永久有效")
    private LocalDateTime expireTime;

    @ApiModelProperty("习计划状态，0-没有计划，1-计划进行中")
    private PlanStatus planStatus;
}
