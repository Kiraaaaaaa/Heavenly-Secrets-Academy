package com.tianji.course.domain.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * @ClassName SubjectSimpleVO
 * @Author wusongsong
 * @Date 2022/8/18 11:40
 * @Version
 **/
@Data
public class SubjectSimpleVO {
    @ApiModelProperty("题目id")
    private Long id;

    @ApiModelProperty("题干")
    private String name;

    @ApiModelProperty("选择题的选项")
    private List<String> options;

    @ApiModelProperty("分值")
    private Integer score;

    @ApiModelProperty("问题类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题")
    private Integer subjectType;

    @ApiModelProperty("难易度，1：简单，2：中等，3：困难")
    private Integer difficulty;

    @ApiModelProperty("解析")
    private String analysis;
}
