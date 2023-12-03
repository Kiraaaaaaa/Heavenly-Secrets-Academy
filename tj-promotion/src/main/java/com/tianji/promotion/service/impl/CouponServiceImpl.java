package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
@AllArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final ICouponScopeService scopeService;

    private final CategoryClient categoryClient;

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
}
