package com.tianji.course.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 所有课程分类数据
 * @author wusongsong
 * @since 2022/7/14 18:15
 * @version 1.0.0
 **/
@Data
@AllArgsConstructor
@NotNull
@ApiModel(description = "所有课程分类数据")
public class SimpleCategoryVO {
    @ApiModelProperty("id")
    private Long id;
    @ApiModelProperty("分类名称")
    private String name;
    @ApiModelProperty("子分类")
    private List<SimpleCategoryVO> children;
    @ApiModelProperty("分类层级")
    private Integer level;
    @ApiModelProperty("父分类id")
    private Long parentId;
}
