package com.tianji.trade.mapper;

import com.tianji.trade.domain.po.OrderDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 订单明细 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {

    @Select("SELECT course_id FROM order_detail WHERE order_id = #{orderId}")
    List<Long> queryCourseIdsByOrderId(Long orderId);
}
