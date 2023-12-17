package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.enums.UserCouponStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author fenny
 * @since 2023-12-05
 */

public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT\n" +
            "\tc.id,\n" +
            "\tc.discount_type,\n" +
            "\tc.`specific`,\n" +
            "\tc.discount_value,\n" +
            "\tc.threshold_amount,\n" +
            "\tc.max_discount_amount,\n" +
            "\tuc.id AS creater \n" +
            "FROM\n" +
            "\tcoupon c\n" +
            "\tINNER JOIN user_coupon uc ON c.id = uc.coupon_id \n" +
            "WHERE\n" +
            "\tuc.`status` = 1 \n" +
            "\tAND uc.user_id = #{userId};")
    List<Coupon> queryMyCoupons(@Param("userId") Long userId);


    @Select("SELECT\n" +
            "\tc.id,\n" +
            "\tc.discount_type,\n" +
            "\tc.`specific`,\n" +
            "\tc.discount_value,\n" +
            "\tc.threshold_amount,\n" +
            "\tc.max_discount_amount,\n" +
            "\tuc.id AS creater \n" +
            "FROM\n" +
            "\tuser_coupon uc\n" +
            "\tINNER JOIN coupon c ON uc.coupon_id = c.id \n" +
            "WHERE\n" +
            "\tuc.id IN (:userCouponIds) \n" +
            "\tAND uc.STATUS = #{status}")
    List<Coupon> queryCouponByUserCouponIds(
            @Param("userCouponIds") List<Long> userCouponIds,@Param("status") UserCouponStatus status);
}
