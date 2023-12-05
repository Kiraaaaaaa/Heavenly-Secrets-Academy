package com.tianji.promotion.service.impl;

import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-12-03
 */
@Service
@Slf4j
@AllArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateCode(Coupon one) {
        log.debug("异步执行生成兑换码，线程id{}; 线程名称{}", Thread.currentThread().getId(), Thread.currentThread().getName());
        /**
         * 方式1：使用redis的incr来生成兑换码的id
         * 缺点：如果在优惠券发放总数的for循环里每次进行redis连接，会造成性能问题
         */
        /**
         * 这里采用方式2：向redis增加该优惠券兑换码的总数
         * 优点：每次只需要向redis增加一次，性能更好
         */
        //1.使用redis自增的value来作为兑换码的id，保证不重复，把当前优惠券的总数作为自增的步长，记录在redis中，方便下一次生成兑换码id
        Integer totalNum = one.getTotalNum();
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if(increment == null){
            return;
        }
        //2.使用工具类，批量生成兑换码
        int startId = increment.intValue() - totalNum + 1;
        List<ExchangeCode> codes = new ArrayList<>();
        for (int serialId = startId; serialId <= increment.intValue(); serialId++) {
            //用兑换码的id+新鲜值，基于类base32的方式来生成一个唯一的兑换码
            //参数1：兑换码的id，参数2：新鲜值生成所需参数()
            String code = CodeUtil.generateCode(serialId, one.getId());
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setCode(code);
            exchangeCode.setId(serialId); //手动生成的兑换码的id, 该po类的注解应该为INPUT
            exchangeCode.setExchangeTargetId(one.getId());
            exchangeCode.setExpiredTime(one.getIssueEndTime());
            codes.add(exchangeCode);
        }
        //3.数据库批量保存兑换码，id就是兑换码的id（非自增）
        saveBatch(codes);
        //4.将该优惠券的兑换码总数存入redis中<优惠券id，兑换码最大id>
        redisTemplate.boundZSetOps(PromotionConstants.COUPON_RANGE_KEY).add(one.getId().toString(), increment);
    }
}
