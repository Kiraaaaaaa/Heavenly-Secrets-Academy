package com.tianji.course.domain.dto;

import lombok.Data;

/**
 * id和nun模型，一个id对应的数量可以用与查询id和num的关系
 * @ClassName IdAndNumDTO
 * @Author wusongsong
 * @Date 2022/8/3 9:27
 * @Version
 **/
@Data
public class IdAndNumDTO {
    private Long id;
    private Long num;
}
