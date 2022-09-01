package com.tianji.course.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 所有课程分类数据
 * @ClassName SimpleCategoryVO
 * @Author wusongsong
 * @Date 2022/7/14 18:15
 * @Version
 **/
@Data
@AllArgsConstructor
@NotNull
public class SimpleCategoryVO {
    private Long id;
    private String name;
    private List<SimpleCategoryVO> children;
}
