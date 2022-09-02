package com.tianji.api.dto;

import lombok.Data;

/**
 * id和nun模型，一个id对应的数量可以用与查询id和num的关系
 * @author wusongsong
 * @since 2022/8/3 9:27
 * @version 1.0.0
 **/
@Data
public class IdAndNumDTO {
    private Long id;
    private Long num;
}
