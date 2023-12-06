package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author fenny
 * @since 2023-12-03
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    //更新优惠券已经被领取的数量
    @Update("update coupon set issue_num = issue_num + 1 where id = #{id}")
    void incrIssueNumById(Long couponId);
}
