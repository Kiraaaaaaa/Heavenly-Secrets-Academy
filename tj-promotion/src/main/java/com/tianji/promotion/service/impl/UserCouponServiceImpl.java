package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-12-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    private final CouponMapper couponMapper;
    private final StringRedisTemplate redisTemplate;
    private final IExchangeCodeService exchangeCodeService;
    @Override
    public void receiveCoupon(Long couponId) {
        //参数是否正确
        if(couponId == null){
            throw new BadRequestException("参数错误");
        }
        //优惠券是否存在
        Coupon coupon = couponMapper.selectById(couponId);
        if(coupon == null){
            throw new BadRequestException("优惠券不存在");
        }
        //优惠券是否正在发放
        if(coupon.getStatus() != CouponStatus.ISSUING.getValue()){
            throw new BadRequestException("只有发放中的优惠券才能领取");
        }
        //是否符合优惠券领取时间
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())){
            throw new BadRequestException("非该优惠券领取时间，领取失败");
        }
        //优惠券是否有库存
        Integer totalNum = coupon.getTotalNum();
        Integer issueNum = coupon.getIssueNum();
        if(issueNum >= totalNum || totalNum <= 0){
            throw new BadRequestException("优惠券已经抢完");
        }
        Long user = UserContext.getUser();
        /*//优惠券是否达到领取上限
        Integer userLimit = coupon.getUserLimit();
        Integer count = lambdaQuery()
                .eq(UserCoupon::getCouponId, couponId)
                .eq(UserCoupon::getUserId, user)
                .count();
        if(count != null && count >= userLimit){
            throw new BadRequestException("超过领取上限");
        }
        //保存用户优惠券领取记录
        saveUserCoupon(coupon, user);
        //更新优惠券领取数量+1
        //写法1
        // coupon.setIssueNum(coupon.getIssueNum() + 1);
        // couponMapper.updateById(coupon);

        //MyBatis写法
        couponMapper.incrIssueNumById(couponId);*/
        // 查询优惠券是否达到兑换上限
        // 保存兑换记录
        // 修改优惠券已兑换数量
        checkAndCreateUserCoupon(user, coupon, null);
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        //1.检验参数
        if(code == null){
            throw new BadRequestException("参数错误");
        }
        //2.解密获取兑换码的自增id
        long id = CodeUtil.parseCode(code);
        log.debug("兑换码的自增id为：{}", id);
        //3.判断兑换码是否已经兑换，在redis的bitmap中setbit key offset id 1(返回1代表已经兑换)
        boolean received = exchangeCodeService.updateExchangeCodeMark(id, true);
        if(received){
            //如果是true，则说明已经兑换，直接返回
            throw new BizIllegalException("兑换码已经被兑换");
        }
        try{
            //4.查询兑换码是否存在
            ExchangeCode codeDB = exchangeCodeService.getById(id);
            if(codeDB == null){
                throw new BizIllegalException("兑换码不存在");
            }
            //5.查询兑换码是否已经过期
            LocalDateTime now = LocalDateTime.now();
            if(now.isAfter(codeDB.getExpiredTime())){
                throw new BizIllegalException("兑换码已经过期");
            }
            //校验兑换码并生成用户券（）
            //查询优惠券信息
            Long user = UserContext.getUser();
            Long couponId = codeDB.getExchangeTargetId();
            Coupon coupon = couponMapper.selectById(couponId);
            //查询优惠券是否存在
            if(coupon == null){
                throw new BizIllegalException("优惠券不存在");
            }
            // 6.查询优惠券是否达到兑换上限
            // 7.保存兑换记录
            // 8.修改优惠券已兑换数量
            // 9.更新兑换码数据
            checkAndCreateUserCoupon(user, coupon, id);

        }catch (Exception e){
            //10.如果以上步骤失败，将bitmap中的该兑换码状态置为0
            exchangeCodeService.updateExchangeCodeMark(id, false);
            throw e;
        }
    }

    /**
     * 检验优惠券是否达到领取上限，并保存用户优惠券领取记录、更新优惠券领取数量
     * @param user
     * @param coupon
     */
    private void checkAndCreateUserCoupon(Long user, Coupon coupon, Long codeId) {
        //优惠券是否达到领取上限
        Integer userLimit = coupon.getUserLimit();
        Integer count = lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, user)
                .count();
        if(count != null && count >= userLimit){
            throw new BadRequestException("超过领取上限");
        }
        //保存用户优惠券领取记录
        saveUserCoupon(coupon, user);
        //更新优惠券领取数量+1
        //MyBatis写法 //目前采用这种方式，考虑到并发。后期使用乐观锁修改 todo 2023年12月6日22:52:05
        couponMapper.incrIssueNumById(coupon.getId());
        //如果是兑换码，则将DB中的兑换码状态置为已兑换
        if(codeId != null){
            exchangeCodeService.lambdaUpdate()
                    .eq(ExchangeCode::getId, codeId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, user)
                    .update();
        }
    }


    private void saveUserCoupon(Coupon coupon, Long user) {
        UserCoupon userCoupon = BeanUtils.copyBean(coupon, UserCoupon.class);
        userCoupon.setStatus(UserCouponStatus.UNUSED).setUserId(user).setCouponId(coupon.getId());
    }
}
