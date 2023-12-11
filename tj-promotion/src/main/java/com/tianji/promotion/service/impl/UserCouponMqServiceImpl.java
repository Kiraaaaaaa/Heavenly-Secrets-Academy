package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.MyLockType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * <p>
 *     通过Mq+redis实现异步领取优惠券
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-12-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    private final CouponMapper couponMapper;
    private final StringRedisTemplate redisTemplate;
    private final IExchangeCodeService exchangeCodeService;
    private final RedissonClient redissonClient;
    private final RabbitMqHelper mqHelper;
    @Override
    // 由于不从db去查询优惠券信息和用户券记录了，所以参数user改为优惠券id
    @MyLock(name = "lock:coupon:uid:#{couponId}", myLockType = MyLockType.RE_ENTRANT_LOCK, myLockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
    public void receiveCoupon(Long couponId) {
        //1.参数校验
        if(couponId == null){
            throw new BadRequestException("参数错误");
        }
        //2.优惠券是否存在，这里就从查表改为查redis缓存
        // Coupon coupon = couponMapper.selectById(couponId);
        // if(coupon == null){
        //     throw new BadRequestException("优惠券不存在");
        // }
        Coupon coupon = queryCouponByCache(couponId);
        if(coupon == null){
            throw new BizIllegalException("该优惠券不存在");
        }

        //3.优惠券是否正在发放，coupon信息是从redis查询的，而redis不包含他的状态信息，但是既然能到这里，说明肯定是发放状态的
        /*if(coupon.getStatus() != CouponStatus.ISSUING.getValue()){
            throw new BadRequestException("只有发放中的优惠券才能领取");
        }*/
        //4.是否符合优惠券领取时间
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())){
            throw new BadRequestException("非该优惠券领取时间，领取失败");
        }
        //5.优惠券是否有库存
        Integer totalNum = coupon.getTotalNum();
        Integer issueNum = coupon.getIssueNum();
        //由于查询是否有库存是从redis查询，而redis保存的totalNum是剩余的可领取优惠券数量，领一张就-1，最小为0
        if(totalNum <= 0){
            throw new BadRequestException("优惠券已经抢完");
        }
        //6.校验限领数量
        Long user = UserContext.getUser();
        //6.1尝试扣减Redis中用户已领优惠券的数量
        String ukey = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        //使用increment对当前用户的已领数量+1，如果是第一次领取就是初始化为1，如果不是第一次领取就是+1
        Long count = redisTemplate.boundHashOps(ukey).increment(user.toString(), 1);
        //6.2校验限领数量
        //increment返回的数量就是领取后的数量，大于限领数量则不允许领取
        if(count > coupon.getUserLimit()){
            throw new BadRequestException("不能超过最大领取数量");
        }

        //7.扣减该优惠券库存
        String ckey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        redisTemplate.boundHashOps(ckey).increment("totalNum", -1);
        /*Integer userLimit = coupon.getUserLimit();
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
        couponMapper.incrIssueNumById(couponId);


          查询优惠券是否达到兑换上限
            保存兑换记录
            修改优惠券已兑换数量
        // 先获取锁，再开启事务，避免重复事务提交
        synchronized (user.toString().intern()){ //由于Long类型有享元模式，所有先转换为String类型，然后intern()从常量池中取，相同id就为同一地址
            // 这样调用方法，它的事务不会生效，因为不能在该类的非事务方法调用事务方法，这里相当于调用的原来方法
            // checkAndCreateUserCoupon(user, coupon, null);

            //所以，我们从AOP上下文对象中获取当前类的代理对象，然后强转为UserCouponServiceImpl类型
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            //此时代理对象就能执行被AOP代理过的方法，执行事务了
            userCouponService.checkAndCreateUserCoupon(user, coupon, null);
        }

        //由于以上synchronized加入的是jvm的锁，这里采用redis分布式锁，但是注意此处的锁不是redisson的锁
        String key = "lock:coupon:uid:" + user;
        //1.创建锁对象
        RedisLock lock = new RedisLock(key, redisTemplate);
        //2.尝试获取锁
        boolean isLocked = lock.tryLock(5, TimeUnit.SECONDS);
        //3.判断是否获取到锁
        if(!isLocked){
            throw new BizIllegalException("请求太频繁");
        }
        //4.执行业务逻辑
        try{
            //从AOP上下文对象中获取当前类的代理对象，然后强转为UserCouponServiceImpl类型
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            //此时代理对象就能执行被AOP代理过的方法，执行事务
            userCouponService.checkAndCreateUserCoupon(user, coupon, null);
        }finally {
            //5.释放锁
            lock.unlock();
        }

        //如果使用第三方的分布式锁，则可以直接使用redisson的分布式锁
        //1.创建锁对象
        可以看到由于redisson有watchdog机制，所以这里不能设置过期时间，看门狗默认是30秒
        如果设置了过期时间，那么watchdog机制就会失效，watchdog机制会在30秒内自动释放锁
        如果该业务方法执行时间超过30秒，那么业务还没有执行完，但是锁已经没有了，所以会造成锁失效
        RLock lock = redissonClient.getLock(key);
        //2.尝试获取锁
        boolean isLocked = lock.tryLock();
        //3.判断是否获取到锁
        if(!isLocked){
            throw new BizIllegalException("请求太频繁");
        }
        //4.执行业务逻辑
        try{
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(user, coupon, null);
        }finally {
            //5.释放锁
            lock.unlock();
        }
        */

        /*以上这种创建锁对象、获取锁、释放锁都是在一个方法中，可以封装成AOP，方便使用，
        常规如果要使用AOP是基于路径的，这里采用注解的方式，更加方便简洁，
        所以直接在checkAndCreateUserCoupon方法上添加自己的自定义注解即可，*/

        //由于修改优惠券领取数量和保存用户优惠券领取记录变成了发送MQ消息，进行异步执行，所以不需要在这里进行DB操作了
        /*//从AOP上下文对象中获取当前类的代理对象，然后强转为UserCouponServiceImpl类型
        IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
        //此时代理对象就能执行被AOP代理过的方法，执行事务
        userCouponService.checkAndCreateUserCoupon(user, coupon, null);
*/

        //8.发送MQ消息，用于修改优惠券领取数量和保存用户优惠券领取记录
        UserCouponDTO msg = new UserCouponDTO();
        msg.setUserId(user);
        msg.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, msg);
        log.debug("发送领券消息：{}", msg);
    }

    private Coupon queryCouponByCache(Long couponId) {
        //1.获取该优惠券的key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        //2.由于该优惠券的信息是map，所以直接使用entries()获取所有key和value
        Map<Object, Object> map = redisTemplate.boundHashOps(key).entries();
        if(CollUtils.isEmpty(map)){
            return null;
        }
        //3.调用工具类将map类型对象反序列化为优惠券对象，参数1为map对象，参数2为优惠券对象，参数3为是否以驼峰命名，参数4为拷贝选项
        return BeanUtils.mapToBean(map, Coupon.class, false, CopyOptions.create());
    }

    @Override
    // @Transactional (取消掉事务，将事务写在锁住的地方，优先获取锁再开启事务)
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

            /*  6.查询优惠券是否达到兑换上限
                7.保存兑换记录
                8.修改优惠券已兑换数量
                9.更新兑换码数据   */
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
    @Override
    @Transactional // 先获取锁，再开启事务，避免重复事务提交
    //此处name是固定的，如果要动态获取到user，则需要使用#{user}占位符，而#{user}是一个SPEL表达式，如果需要实现需要在MyLockAspect中添加解析
    @MyLock(name = "lock:coupon:uid:#{user}", myLockType = MyLockType.RE_ENTRANT_LOCK, myLockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
    public void checkAndCreateUserCoupon(Long user, Coupon coupon, Long codeId) {

        /**
         * 经过测试，同一个user在高并发下，会出现领取优惠券数量超过限制数量的情况
         * 解决方法：把以下增查改操作添加synchronized悲观锁代码块中，保证请求串行化，解决并发问题
         */

        /**
         * 如果要让方法synchronized话也可以直接在方法上加上synchronized关键字，
         * 但是这里不管是多位用户还是单个用户的并发都会调用这个方法，如果锁住所有调用，
         * 那么会导致所有用户都要等待，所以，我们需要给synchronized一个条件，锁住的条件就是user相同的情况，
         */
        /*synchronized (user.toString().intern()){ //由于Long类型有享元模式，所有先转换为String类型，然后intern()从常量池中取同一地址
            // 所有db操作
        }*/

        /*但是以上同样有问题，因为当前synchronized块是在事务里的，其实多个线程在开启事务的时候不会冲突，
        假设此时同一用户有两个并发请求，同时开启了两个线程，这两个线程都开启了事务，
        此时线程1在事务内获取到了锁，那么他会执行锁住的方法，线程2在事务内获取不到锁，
        当线程1的锁释放后，但此时还未提交事务，此时线程2拿到锁，也执行了一次锁住的方法，
        那么当线程1此时提交事务后，线程2又提交了一次，就重复提交了两次事务，这就冲突了，
        所以，需要在事务提交之前，获取锁，当事务提交之后，释放锁，
        代码就需要改造，将事务写在锁住的地方，优先获取锁再开启事务*/

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
        //MyBatis写法
        int result = couponMapper.incrIssueNumById(coupon.getId());
        if(result == 0){
            throw new BizIllegalException("优惠券库存不足");
        }
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
        //设置用户券状态为未使用，设置用户id，设置优惠券id
        userCoupon.setStatus(UserCouponStatus.UNUSED).setUserId(user).setCouponId(coupon.getId());
        //如果领取的优惠券是设置的天数，则需要设置有效期，否则为null，而null会导致数据库报错，从而又导致MQ消息消费失败进行重试
        if(userCoupon.getTermBeginTime() == null || userCoupon.getTermEndTime() == null){
            //如果设置的天数，有效开始时间为立即有效
            userCoupon.setTermBeginTime(LocalDateTime.now());
            //结束时间就是当前时间+天数
            userCoupon.setTermEndTime(userCoupon.getTermBeginTime().plusDays(coupon.getTermDays()));
        }
        save(userCoupon);
    }

    /**
     * 保存用户优惠券领取记录、更新优惠券领取数量
     */
    @Override
    @Transactional // 先获取锁，再开启事务，避免重复事务提交
    // 这里就不用加锁了，因为这是由用户领券发送的MQ消息到监听器执行的，锁应该加到在这之前，即在开始监测领券的时候，receiveCoupon方法
    //此处name是固定的，如果要动态获取到user，则需要使用#{user}占位符，而#{user}是一个SPEL表达式，如果需要实现需要在MyLockAspect中添加解析
    // @MyLock(name = "lock:coupon:uid:#{user}", myLockType = MyLockType.RE_ENTRANT_LOCK, myLockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
        public void checkAndCreateUserCouponNew(UserCouponDTO dto) {

        /**
         * 经过测试，同一个user在高并发下，会出现领取优惠券数量超过限制数量的情况
         * 解决方法：把以下增查改操作添加synchronized悲观锁代码块中，保证请求串行化，解决并发问题
         */

        /**
         * 如果要让方法synchronized话也可以直接在方法上加上synchronized关键字，
         * 但是这里不管是多位用户还是单个用户的并发都会调用这个方法，如果锁住所有调用，
         * 那么会导致所有用户都要等待，所以，我们需要给synchronized一个条件，锁住的条件就是user相同的情况，
         */
        /*synchronized (user.toString().intern()){ //由于Long类型有享元模式，所有先转换为String类型，然后intern()从常量池中取同一地址
            // 所有db操作
        }*/

        /*但是以上同样有问题，因为当前synchronized块是在事务里的，其实多个线程在开启事务的时候不会冲突，
        假设此时同一用户有两个并发请求，同时开启了两个线程，这两个线程都开启了事务，
        此时线程1在事务内获取到了锁，那么他会执行锁住的方法，线程2在事务内获取不到锁，
        当线程1的锁释放后，但此时还未提交事务，此时线程2拿到锁，也执行了一次锁住的方法，
        那么当线程1此时提交事务后，线程2又提交了一次，就重复提交了两次事务，这就冲突了，
        所以，需要在事务提交之前，获取锁，当事务提交之后，释放锁，
        代码就需要改造，将事务写在锁住的地方，优先获取锁再开启事务*/

        //优惠券是否达到领取上限，这里不用查了，在之前的redis中就查了
        /* Integer userLimit = coupon.getUserLimit();
        Integer count = lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, user)
                .count();
        if(count != null && count >= userLimit){
            throw new BadRequestException("超过领取上限");
        }*/

        //1.由于发送的MQ消息是{优惠券id，用户id}，所有需要查询一次优惠券信息
        //因为之前是在Redis中检查优惠券是否存在、是否超过限领数量等一系列校验，所以到达这里的一定是可以领取的请求，还能领多少这里就只能查多少次，对性能的影响不是特别大
        Coupon coupon = couponMapper.selectById(dto.getCouponId());
        if(coupon == null){
            //注意不能抛异常，因为该方法是MQ消息的消费者，如果是消费者抛异常那么监听器将会进行重试，
            //既然需要修改的优惠券都不存在了，那也没必要进行重试了，所以直接返回空即可
            return;
        }
        //保存用户优惠券领取记录
        saveUserCoupon(coupon, dto.getUserId());
        //更新优惠券领取数量+1
        //MyBatis写法
        int r = couponMapper.incrIssueNumById(coupon.getId());
        if(r == 0){
            return;
        }

        //如果是兑换码，则将DB中的兑换码状态置为已兑换
        /*if(codeId != null){
            exchangeCodeService.lambdaUpdate()
                    .eq(ExchangeCode::getId, codeId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, user)
                    .update();
        }*/
    }
}
