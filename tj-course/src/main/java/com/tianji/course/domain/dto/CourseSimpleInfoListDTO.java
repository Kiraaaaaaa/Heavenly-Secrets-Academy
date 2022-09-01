package com.tianji.course.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * @ClassName CourseSimpleInfoListDTO
 * @Author wusongsong
 * @Date 2022/7/26 9:26
 * @Version
 **/
@Data
public class CourseSimpleInfoListDTO {

    @ApiModelProperty("三级分类id列表")
    private List<Long> thirdCataIds;

    @ApiModelProperty("课程id列表")
    private List<Long> ids;
}
