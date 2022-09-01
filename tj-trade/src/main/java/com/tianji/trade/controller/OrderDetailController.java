package com.tianji.trade.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.trade.domain.query.OrderDetailPageQuery;
import com.tianji.trade.domain.vo.OrderDetailAdminVO;
import com.tianji.trade.domain.vo.OrderDetailPageVO;
import com.tianji.trade.service.IOrderDetailService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 订单明细 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */

@Api(tags = "订单明细相关接口")
@RestController
@RequestMapping("/order-details")
@RequiredArgsConstructor
public class OrderDetailController {

    private final IOrderDetailService detailService;

    @ApiOperation("分页查询订单明细")
    @GetMapping("/page")
    public PageDTO<OrderDetailPageVO> queryDetailForPage(OrderDetailPageQuery pageQuery) {
        return detailService.queryDetailForPage(pageQuery);
    }

    @ApiOperation("根据订单明细id获取详细信息")
    @GetMapping("/{id}")
    public OrderDetailAdminVO queryOrdersDetailProgress( @ApiParam(value = "订单明细id")@PathVariable("id") Long id) {
        return detailService.queryOrdersDetailProgress(id);
    }

}
