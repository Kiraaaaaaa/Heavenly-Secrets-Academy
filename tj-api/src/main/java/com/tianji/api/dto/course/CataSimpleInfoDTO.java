package com.tianji.api.dto.course;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @ClassName CataSimpleInfoDTO
 * @Author wusongsong
 * @Date 2022/7/27 14:22
 * @Version
 **/
@Data
public class CataSimpleInfoDTO {
    @ApiModelProperty("目录id")
    private Long id;
    @ApiModelProperty("目录名称")
    private String name;
    @ApiModelProperty("数字序号，不包含章序号")
    private Integer cIndex;
}
