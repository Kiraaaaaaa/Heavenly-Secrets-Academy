package com.tianji.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.trade.domain.po.Order;
import com.tianji.trade.domain.po.OrderDetail;
import com.tianji.trade.domain.po.RefundApply;
import com.tianji.trade.domain.query.OrderDetailPageQuery;
import com.tianji.trade.domain.vo.OrderDetailAdminVO;
import com.tianji.trade.domain.vo.OrderDetailPageVO;
import com.tianji.trade.domain.vo.OrderProgressNodeVO;

import java.util.List;

/**
 * <p>
 * 订单明细 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface IOrderDetailService extends IService<OrderDetail> {

    void updateStatusByOrderId(Long orderId, Integer status);

    List<OrderDetail> queryByOrderIds(List<Long> orderIds);

    List<OrderDetail> queryByOrderId(Long orderId);

    PageDTO<OrderDetailPageVO> queryDetailForPage(OrderDetailPageQuery pageQuery);

    OrderDetailAdminVO queryOrdersDetailProgress(Long id);

    List<OrderProgressNodeVO> packageProgressNodes(Order order, RefundApply refundApply);

    void markDetailSuccessByOrderId(Long id, String payChannel);

    void updateRefundStatusById(Long orderDetailId, int status);

    List<Long> queryCourseIdsByOrderId(Long orderId);
}
