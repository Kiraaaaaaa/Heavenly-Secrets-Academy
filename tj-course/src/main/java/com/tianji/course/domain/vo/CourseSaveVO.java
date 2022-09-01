package com.tianji.course.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @ClassName CourseSaveVO
 * @Author wusongsong
 * @Date 2022/7/13 11:26
 * @Version
 **/
@Data
@ApiModel("课程保存结果")
@AllArgsConstructor
@NotNull
@Builder
public class CourseSaveVO {
    @ApiModelProperty("课程id")
    private Long id;
}
