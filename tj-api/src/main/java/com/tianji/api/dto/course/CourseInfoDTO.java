package com.tianji.api.dto.course;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程信息
 *
 * @ClassName CourseInfoDTO
 * @Author wusongsong
 * @Date 2022/8/5 16:54
 * @Version
 **/
@Data
public class CourseInfoDTO {
    /**
     * 课程草稿id，对应正式草稿id
     */
    private Long id;

    /**
     * 课程名称
     */
    private String name;

    /**
     * 课程类型，1：直播课，2：录播课
     */
    private Integer courseType;

    /**
     * 封面链接
     */
    private String coverUrl;

    /**
     * 一级课程分类id
     */
    private Long firstCateId;

    /**
     * 二级课程分类id
     */
    private Long secondCateId;

    /**
     * 三级课程分类id
     */
    private Long thirdCateId;

    /**
     * 售卖方式0付费，1：免费
     */
    private Integer free;

    /**
     * 课程价格，单位为分
     */
    private Integer price;

    /**
     * 模板类型，1：固定模板，2：自定义模板
     */
    private Integer templateType;

    /**
     * 自定义模板的连接
     */
    private String templateUrl;

    /**
     * 课程状态，1：待上架，1：已上架，2：下架，3：已完结
     */
    private Integer status;

    /**
     * 课程购买有效期开始时间
     */
    private LocalDateTime purchaseStartTime;

    /**
     * 课程购买有效期结束时间
     */
    private LocalDateTime purchaseEndTime;

    /**
     * 信息填写进度
     */
    private Integer step;

    /**
     * 课程总时长
     */
    private Integer mediaDuration;

    /**
     * 课程有效期，单位月
     */
    private Integer validDuration;

    /**
     * 课程总节数，包括练习
     */
    private Integer sectionNum;

    //课程分类名称
    private List<String> cates;
    //课程目录结构 批量查询不支持
    private List<CataDTO> cataDTOS;
    //老师列表
    private List<Long> teacherIds;
}
