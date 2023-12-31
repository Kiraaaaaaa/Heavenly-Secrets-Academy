package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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
    @Update("update coupon set issue_num = issue_num + 1 where id = #{id} and issue_num < total_num")
    int incrIssueNumById(Long couponId);

    //批量更新优惠券为已使用
    @Update("UPDATE coupon SET used_num = used_num + #{num} WHERE id IN (:couponIds)")
    int incrUsedNumByIds(List<Long> couponIds, int num);
}
