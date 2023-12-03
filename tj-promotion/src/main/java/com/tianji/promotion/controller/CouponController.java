package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author fenny
 * @since 2023-12-03
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/coupons")
@Api(tags = "优惠券相关接口")
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("新增优惠券接口")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO dto){
        couponService.saveCoupon(dto);
    }
    @ApiOperation("修改优惠券接口")
    @PutMapping("{id}")
    public void updateById(@RequestBody @Valid CouponFormDTO dto, @PathVariable("id") Long id){
        couponService.updateById(dto, id);
    }
    @ApiOperation("删除优惠券接口")
    @DeleteMapping("{id}")
    public void deleteById(@ApiParam("优惠券id") @PathVariable("id") Long id){
        couponService.deleteById(id);
    }
    @ApiOperation("查询优惠券接口")
    @GetMapping("{id}")
    public CouponDetailVO queryById(@ApiParam("优惠券id") @PathVariable("id") Long id){
        return couponService.queryById(id);
    }
    @ApiOperation("分页查询优惠券接口-管理端")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
        return couponService.queryCouponByPage(query);
    }
}