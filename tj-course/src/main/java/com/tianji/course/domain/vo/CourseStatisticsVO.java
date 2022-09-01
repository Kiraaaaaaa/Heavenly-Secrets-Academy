package com.tianji.course.domain.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 课程数据统计
 * @ClassName CourseStatisticsVO
 * @Author wusongsong
 * @Date 2022/7/10 15:36
 * @Version
 **/
@Data
public class CourseStatisticsVO {
    @ApiModelProperty("课程总数量")
    private Integer totalNum;
    @ApiModelProperty("上架课程数量")
    private Integer onSaleNum;
    @ApiModelProperty("下架课程数量")
    private Integer offShelfNum;
    @ApiModelProperty("待上架课程数量")
    private Integer noSaleNum;
    @ApiModelProperty("完结课程数量")
    private Integer finishedNum;
    @ApiModelProperty("录播课程数量")
    private Integer recordNum;
    @ApiModelProperty("直播课程数")
    private Integer liveNum;


}
