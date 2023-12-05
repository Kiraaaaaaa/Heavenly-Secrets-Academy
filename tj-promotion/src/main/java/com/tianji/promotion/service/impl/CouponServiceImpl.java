package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-12-03
 */
@Service
@Slf4j
@AllArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final ICouponScopeService scopeService;

    private final CategoryClient categoryClient;

    private final IExchangeCodeService exchangeCodeService;

    private final IUserCouponService userCouponService;
    @Override
    public void saveCoupon(CouponFormDTO dto) {
        //1.保存优惠券
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);
        //2.检查是否为分类限定优惠券
        if(!dto.getSpecific()){
            return;
        }
        //3.检查是否有优惠券范围
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)){
            throw new BadRequestException("限定范围不能为空");
        }
        Long couponId = coupon.getId();
        //4.将此优惠券的可用范围对象插入优惠券范围表
        List<CouponScope> collect = scopes.stream()
                .map(bizId -> new CouponScope()
                        .setCouponId(couponId)
                        .setBizId(bizId)
                        .setType(1)) // 由于我们只有分类限定优惠券，所以这里的类型写死为1
                .collect(Collectors.toList());
        //5.批量保存优惠券范围表
        scopeService.saveBatch(collect);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        String name = query.getName();
        Integer status = query.getStatus();
        Integer type = query.getType();
        //1.分页查询
        Page<Coupon> page = lambdaQuery()
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .eq(status != null, Coupon::getStatus, status)
                //注意getDiscountType才是优惠券的折扣类型
                .eq(type != null, Coupon::getDiscountType, type)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //2.数据一致，直接封装返回
            List<CouponPageVO> vos = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, vos);
    }

    @Override
    public void updateById(CouponFormDTO dto, Long id) {
        //1.校验参数
        Long dtoId = dto.getId();
        //如果dto的id和路径id都存在但id不一致，或者都不存在，则抛出异常
        if((dtoId!=null && id!=null && !dtoId.equals(id)) || (dtoId==null&&id==null)){
            throw new BadRequestException("参数错误");
        }
        //2.更新优惠券基本信息
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        //只更新状态为1的优惠券基本信息，如果失败则是状态已修改
        boolean update = lambdaUpdate().eq(Coupon::getStatus, 1).update(coupon);
        //基本信息更新失败则无需更新优惠券范围信息
        if(!update){
            return;
        }
        //3.更新优惠券范围信息
        List<Long> scopeIds = dto.getScopes();
        //3.1只要是优惠券状态不为1，或者优惠券范围为空，则不更新优惠券范围信息
        //3.2个人写法是先删除优惠券范围信息，再重新插入
        List<Long> ids = scopeService.lambdaQuery().select(CouponScope::getId).eq(CouponScope::getCouponId, dto.getId()).list()
                .stream().map(CouponScope::getId).collect(Collectors.toList());
        scopeService.removeByIds(ids);
        //3.3删除成功后，并且有范围再插入
        if(CollUtils.isNotEmpty(scopeIds)){
            List<CouponScope> lis = scopeIds.stream().map(i -> new CouponScope().setCouponId(dto.getId()).setType(1).setBizId(i)).collect(Collectors.toList());
            scopeService.saveBatch(lis);
        }
    }

    @Override
    public void deleteById(Long id) {
        //1.查询优惠券是否存在并删除
        boolean remove = lambdaUpdate()
                .eq(Coupon::getId, id)
                .eq(Coupon::getStatus, 1)
                .remove();
        if(!remove){
            throw new BadRequestException("删除失败，当前优惠券状态非待发放状态");
        }
        //2.查询优惠券范围信息并删除
        scopeService.lambdaUpdate()
                .eq(CouponScope::getCouponId, id)
                .remove();
    }

    @Override
    public CouponDetailVO queryById(Long id) {
        //1.查询优惠券基本信息
        Coupon coupon = lambdaQuery()
                .eq(Coupon::getId, id)
                .one();
        //2.查询优惠券范围列表
        List<CouponScope> couponScopes = scopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
        //3.查询范围信息<分类id，分类名称>
        Map<Long, String> cateMap = categoryClient.getAllOfOneLevel().stream().collect(Collectors.toMap(CategoryBasicDTO::getId, CategoryBasicDTO::getName));
        //4.封装范围信息到范围列表
        List<CouponScopeVO> vos = couponScopes.stream().map(i -> new CouponScopeVO().setName(cateMap.get(i.getBizId())).setId(i.getBizId())).collect(Collectors.toList());
        //5.封装优惠券详细信息
        CouponDetailVO couponDetailVO = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        couponDetailVO.setScopes(vos);
        return couponDetailVO;
    }

    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {
        log.debug("发放优惠券，线程id{}，线程名{}",  Thread.currentThread().getId(),  Thread.currentThread().getName());
        Integer termDays = dto.getTermDays();
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        //1.获取该优惠券DB信息
        Coupon one = getById(dto.getId());
        //记录下未修改前状态是否为待发放
        boolean isDraft = one.getStatus() == CouponStatus.DRAFT.getValue() ? true : false;
        if(one == null){
            throw new BadRequestException("该优惠券不存在");
        }
        //如果状态不是待发放或者暂停，则抛出异常
        if(one.getStatus()!=CouponStatus.DRAFT.getValue() && one.getStatus()!=CouponStatus.PAUSE.getValue()){
            throw new BizIllegalException("只有待发放和暂停中的优惠券才能进行发放");
        }
        //2.判断优惠券是定时发放还是立即发放
        LocalDateTime now = LocalDateTime.now();
        //如果立即发放时间为空，或者立即发放时间小于等于当前时间，则设置立即发放时间为当前时间，注意isBefore如果比较的时间都一样，那么比较结果为false
        boolean isNow = issueBeginTime == null || !issueBeginTime.isAfter(now) ? true : false;
        /**
         * 方式1，通过修改查出DB的对象属性来更新
         */
        if(isNow){
            //立即发放
            one.setIssueBeginTime(now);
            one.setStatus(CouponStatus.ISSUING.getValue());
        }else{
            //定时发放
            one.setIssueBeginTime(dto.getIssueBeginTime());
            one.setStatus(CouponStatus.UN_ISSUE.getValue());
        }
        one.setIssueEndTime(dto.getIssueEndTime());
        //3.判断优惠券是有效天数还是有效期
        if(termDays != null){
            one.setTermDays(termDays);
        }else{
            one.setTermBeginTime(dto.getTermBeginTime());
            one.setTermEndTime(dto.getTermEndTime());
        }
        /**
         * 方式2，只更新DB中需要更新的字段
         */
        // Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        // if(isNow){
        //     coupon.setStatus(CouponStatus.ISSUING.getValue());
        //     coupon.setIssueBeginTime(now);
        // }else{
        //     coupon.setStatus(CouponStatus.UN_ISSUE.getValue());
        // }
        //4.更新该优惠券
        updateById(one);
        //4.如果优惠券状态为指定发放，且优惠券之前的状态为待发放，则生成兑换码
        if(one.getObtainWay()== ObtainType.ISSUE && isDraft){
            exchangeCodeService.asyncGenerateCode(one);
        }
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        //1.查询所有发放中，且领取方式是手动领取的优惠券
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING.getValue())
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if(CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        //2.查询用户已经领取的优惠券
        Set<Long> ids = coupons.stream().map(Coupon::getId).collect(Collectors.toSet());
        //获取当前用户所有发放中的优惠券
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, ids) //由于用户的优惠券表只有优惠券使用状态，但是这里需要查询发放状态的优惠券，所有用in
                .list();

        //2.1统计当前用户发放中【已经领取】的优惠券数量<优惠券id，已领取数量>

        // 传统写法1
        // Map<Long, Long> map = new HashMap<>();
        // for (UserCoupon userCoupon : userCoupons) {
        //     Long couponId = userCoupon.getCouponId();
        //     if(map.containsKey(couponId)){
        //         map.put(couponId, map.get(couponId)+1);
        //     }else{
        //         map.put(couponId, 1L);
        //     }
        // }
        // 传统写法2
        // Map<Long, Long> map = new HashMap<>();
        // for (UserCoupon userCoupon : userCoupons) {
        //     Long num = map.get(userCoupon.getCouponId());
        //     if(num == null){
        //         //没有则新增该优惠券统计数量
        //         map.put(userCoupon.getCouponId(), 1L);
        //     }else{
        //         //已经有则累加该优惠券统计数量
        //         map.put(userCoupon.getCouponId(), num+1);
        //     }
        // }

        // stream流写法
        Map<Long, Long> getCouponsMap = userCoupons.stream().collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        //2.2统计当前用户【已经领取且未使用】的优惠券数量<优惠券id，未使用数量>
        Map<Long, Long> unUsedCouponsMap = userCoupons.stream()
                .filter(c->c.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        //3.封装vos
        List<CouponVO> vos = coupons.stream().map(i -> {
            CouponVO couponVO = BeanUtils.copyBean(i, CouponVO.class);
            //3.1设置该优惠券是否可以领取，优惠券已领取数量IssueNum < 优惠券总数量TotalNum，且用户领取数量 < 优惠券每个人限领数量
            // 已领取数量，没有则为0
            Long getNum = getCouponsMap.getOrDefault(i.getId(), 0L);
            boolean avaliable = (i.getIssueNum() < i.getTotalNum() && getNum.intValue() < i.getUserLimit());
            couponVO.setAvailable(avaliable);
            //3.2该优惠券是否可以使用
            // 已使用数量
            boolean received = unUsedCouponsMap.getOrDefault(i.getId(), 0L) > 0;
            couponVO.setReceived(received);
            return couponVO;
        }).collect(Collectors.toList());

        return vos;
    }
}
