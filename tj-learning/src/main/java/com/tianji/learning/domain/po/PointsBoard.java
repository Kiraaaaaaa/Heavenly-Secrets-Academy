package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 学霸天梯榜
 * </p>
 *
 * @author fenny
 * @since 2023-11-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("points_board")
@ApiModel(value="PointsBoard对象", description="学霸天梯榜")
public class PointsBoard implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "榜单id")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty(value = "学生id")
    private Long userId;

    @ApiModelProperty(value = "积分值")
    private Integer points;

    @ApiModelProperty(value = "名次，只记录赛季前100")
    private Integer rank;

    @ApiModelProperty(value = "赛季，例如 1,就是第一赛季，2-就是第二赛季")
    private Integer season;


}
