package com.tianji.exam.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 题目
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("question_detail")
public class QuestionDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 题目id
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 选择题选项，json数组格式
     */
    private String options;

    /**
     * 选择题正确答案1到10，如果有多个答案，中间使用逗号隔开，如果是判断题，1：代表正确，其他代表错误
     */
    private String answer;

    /**
     * 答案解析
     */
    private String analysis;

    /**
     * 部门id
     */
    private Long depId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private Long creater;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;


}
