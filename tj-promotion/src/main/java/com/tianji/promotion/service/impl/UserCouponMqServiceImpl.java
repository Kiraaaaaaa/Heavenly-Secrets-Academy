package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.hash.Hash;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.course.CourseDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.config.PromotionConfig;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCouponDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.MyLockType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.ldap.HasControls;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;

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
    private final ICouponScopeService scopeService;
    private final Executor discountSolutionExecutor;
    private final UserCouponMapper userCouponMapper;
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

        //todo 兑换兑换码还需要更新兑换码
        //如果是兑换码，则将DB中的兑换码状态置为已兑换
        /*if(codeId != null){
            exchangeCodeService.lambdaUpdate()
                    .eq(ExchangeCode::getId, codeId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, user)
                    .update();
        }*/
    }

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        //1.查询用户可用的优惠券，条件：1.该用户2.用户券状态为未使用。查询表coupon和user_coupon，关联条件coupon_id。
        //得到用户可使用的优惠券集合，所需字段：1.优惠券id，2.优惠券规则，3.优惠券折扣值，4.优惠券门槛，5.优惠券最大折金额，6.优惠券最大优惠金额，7.【用户券id】(返回给前端核销时使用的)
        List<Coupon> coupons = baseMapper.queryMyCoupons(UserContext.getUser());

        //2.初筛，过滤掉优惠券规则不满足的优惠券，条件：优惠券门槛不超过订单总价
        //2.1计算订单总价
        int totalNum = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        //2.1筛选出该订单可用券
        List<Coupon> availableCoupons  = coupons.stream()
                // 利用策略模式，根据优惠券类型获取对应的优惠券，并使用自身的canUse方法判断优惠券是否可用(比较订单总价与优惠券门槛)
                .filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalNum, c))
                .collect(Collectors.toList());
        if(CollUtils.isEmpty(availableCoupons)){
            return CollUtils.emptyList();
        }
        //3.细筛，筛选出优惠券的全排列组合，组合包含单券和多券两种组合，多券组合为某张优惠券下可用的课程id列表，单券组合为所有优惠券集合
        //3.1获取到每一张可用的优惠券可用课程集合的集合
        Map<Coupon, List<OrderCourseDTO>> availableCouponsMap = findAvailableCoupons(availableCoupons, orderCourses);
        //通过优惠券可用分类细筛后如果没有可用的优惠券则直接返回
        if(CollUtils.isEmpty(availableCouponsMap)){
            return CollUtils.emptyList();
        }
        //计算优惠券的排列组合
        //3.2从map取出keys即优惠券Set，转换为优惠券List，并覆盖初筛的可用券集合
        availableCoupons = new ArrayList<>(availableCouponsMap.keySet());
        //3.3获得方案集合，即对细筛优惠券集合进行全排列组合
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        //3.4将所有单券也加入到全排列中，因为页面除了展示优惠券组合，还要展示单券
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(CollUtils.toList(availableCoupon));
        }

        //4.计算每一种方案的优惠情况
        /*List<CouponDiscountDTO> list = new ArrayList<>();
        for (List<Coupon> couponList : solutions) {
            //参数：1.优惠券可优惠课程2.购买的课程集合3.优惠券排列方案
            //返回值：该优惠券组合或者单券的优惠情况
            list.add(calculateSolutionDiscount(availableCouponsMap, orderCourses, couponList));
        }*/
        //5.改造第4步为【多线程并行】查询每种方案
        List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size())); //线程安全的集合
        CountDownLatch countDownLatch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
                @Override
                public CouponDiscountDTO get() {
                    //参数：1.优惠券可优惠课程2.购买的课程集合3.优惠券排列方案
                    //返回值：该优惠券组合或者单券的优惠情况
                    CouponDiscountDTO dto = calculateSolutionDiscount(availableCouponsMap, orderCourses, solution);
                    return dto;
                }
            }, discountSolutionExecutor).thenAccept(new Consumer<CouponDiscountDTO>() { //接收优惠情况，但无返回值
                @Override
                public void accept(CouponDiscountDTO couponDiscountDTO) {
                    list.add(couponDiscountDTO); //将该优惠情况加入到优惠方案集合中
                    // countDownLatch.countDown(); // 可加可不加，因为规定了超时两秒后取消阻塞
                }
            });
        }
        try{
            //两秒都还没有结果就取消计数器阻塞状态继续执行
            countDownLatch.await(2, TimeUnit.SECONDS);
        }catch (InterruptedException e){
            log.error("优惠券方案计算出现错误", e.getMessage());
        }

        //6.筛选出方案中最优解并返回
        return findBestSolution(list);
    }

    /**
     * 下单时，查询用户使用的优惠券优惠情况(主要是优惠券明细，查询出来保存到订单项中)
     * @param orderCouponDTO 订单服务传递来的：用户使用的优惠券id集合 + 订单中的课程列表
     * @return CouponDiscountDTO 主要使用了：优惠券ids + 优惠券优惠明细(课程和优惠金额哈希表) + 总优惠金额
     */
    @Override
    public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
        //1.查询用户可用优惠券
        List<Long> userCouponIds = orderCouponDTO.getUserCouponIds();
        List<Coupon> availableCoupons = userCouponMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.UNUSED);
        if(CollUtils.isEmpty(availableCoupons)){
            return null;
        }
        //2.查询优惠券将可用于哪些课程
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupons(availableCoupons, orderCouponDTO.getCourseList());
        if(CollUtils.isEmpty(availableCouponMap)){
            return null;
        }
        //3.获取该方案优惠情况(优惠券使用顺序是前端传过来，所以是一个固定的方案)
        CouponDiscountDTO dto = calculateSolutionDiscount(availableCouponMap, orderCouponDTO.getCourseList(), availableCoupons);
        return dto;
    }

    /**
     * 在下单完成后，完成优惠券核销
     * @param userCouponIds 用户使用的优惠券id集合
     */
    @Override
    public void writeOffCoupon(List<Long> userCouponIds) {
        //1.校验将要核销的用户券集合是否存在
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if(CollUtils.isEmpty(userCoupons)){
            return;
        }
        //2.处理用户券集合
        List<UserCoupon> coupons = userCoupons.stream()
                // 防止错误调用方法，二次校验
                .filter(coupon -> {
                    //用户券必须存在
                    if (coupon == null) return false;
                    //用户券的状态必须为未使用
                    if (coupon.getStatus() != UserCouponStatus.UNUSED) return false;
                    //用户券的使用时间必须在当前时间可用
                    LocalDateTime now = LocalDateTime.now();
                    return !now.isBefore(coupon.getTermBeginTime()) && !now.isAfter(coupon.getTermEndTime());
                })
                //排除掉不可用的优惠券后，设置优惠券的状态为已使用
                .map(coupon ->
                        coupon.setStatus(UserCouponStatus.USED)
                ).collect(Collectors.toList());
        //3.修改用户券状态
        boolean success = updateBatchById(coupons);
        if(!success) return;
        //4.修改优惠券已使用数量
        List<Long> ids = coupons.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        int res = couponMapper.incrUsedNumByIds(ids, 1);
        if(res < 1){
            throw new DbException("更新优惠券使用数量失败");
        }
    }


    /**
     * 计算最优优惠券方案
     * - 用券相同时，优惠金额最高的方案
     * - 优惠金额相同时，用券最少的方案
     * @param solutions 未经过最优筛选的优惠方案集合
     * @return 返回最佳优惠方案集合
     */
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        //1.创建两个优惠券记录哈希表
        //分别记录：
        // + 拥有相同优惠券id的方案中，记录下优惠最高的方案
        // + 拥有相同优惠金额的方案中，记录下使用优惠券最少的方案
        //<优惠券ids，最高优惠的方案>
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        //<优惠金额，最少优惠券的方案>
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();

        //2.遍历优惠方案
        for (CouponDiscountDTO solution : solutions) {
            //2.1当前方案中的ids转string
            String ids = solution.getIds().stream()
                    //先升序排序ids
                    .sorted(Comparator.comparing(Long::valueOf))
                    //将每个id转为字符串
                    .map(String::valueOf)
                    //将每个id使用，号拼接
                    .collect(Collectors.joining(","));
            //2.2比较旧方案和当前方案的优惠金额
            CouponDiscountDTO old = moreDiscountMap.get(ids);
            //如果旧方案优惠金额大于等于当前方案优惠金额，则跳过当前方案(不更新)
            if(old != null && old.getDiscountAmount() >= solution.getDiscountAmount()){
                continue;
            }
            //2.3比较当前方案优惠金额的优惠券数量和map中记录的该金额的优惠券数量
            old = lessCouponMap.get(solution.getDiscountAmount()); //取出该优惠券的金额，查询该金额旧的记录
            if(old!= null && old.getIds().size()>1 && old.getIds().size() <= solution.getIds().size()){ //old.getIds().size()>1 为只比较组合，不比较单券
                continue;
            }
            //2.4说明当前方案更优。更新两个MAP的记录
            moreDiscountMap.put(ids, solution); //更新该ids的优惠方案
            lessCouponMap.put(solution.getDiscountAmount(), solution); //更新该金额的优惠方案
        }

        //3.求moreDiscountMap和lessCouponMap中交集，得出最佳方案集合(去掉了solutions中ids重复的优惠方案，只取出优惠力度最大的其中一个方案)
        Collection<CouponDiscountDTO> bestSolutions = CollUtil.intersection(moreDiscountMap.values(), lessCouponMap.values());
        List<CouponDiscountDTO> latestBestSolutions = bestSolutions.stream()
                //根据优惠金额倒序
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
        return latestBestSolutions;
    }

    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> availableCouponsMap, List<OrderCourseDTO> orderCourses, List<Coupon> couponList) {
        CouponDiscountDTO discountDTO = new CouponDiscountDTO();
        //1.建立优惠明细映射<商品id，优惠金额>
        Map<Long, Integer> courseDiscountMap = orderCourses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, orderCourseDTO -> 0)); //初始化每个商品的已优惠金额为0
        discountDTO.setDiscountDetail(courseDiscountMap); //结果设置优惠明细
        //2.计算该方案明细
        //2.1.循环方案中的优惠券
        for (Coupon coupon : couponList) {
            //2.2.得到该优惠券可优惠的课程集合
            List<OrderCourseDTO> courses = availableCouponsMap.get(coupon);
            //2.3.计算目前【剩余】的可优惠课程的总价(课程原价-对应优惠明细)
            int price = courses.stream().mapToInt(course -> course.getPrice() - courseDiscountMap.get(course.getId())).sum();
            //2.4.判断总价是否符合该优惠券门槛
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType()); //得到该优惠券的计算策略
            if(!discount.canUse(price, coupon)) continue;
            //2.5.计算总优惠金额
            int discountPrice = discount.calculateDiscount(price, coupon);
            //2.6.将总优惠金额分摊到每个可优惠课程的优惠明细中
            //无返回值，只是更新了优惠明细映射，方便下张优惠券来计算优惠金额
            calculateDetailDiscount(courseDiscountMap, orderCourses, discountPrice, price);
            //2.8.保存该优惠券id到本方案中
            discountDTO.getIds().add(coupon.getId());
            //2.9.保存该优惠券规则到本方案中(从策略中取出该优惠券的规则)
            discountDTO.getRules().add(discount.getRule(coupon));
            //保存该方案的优惠金额(累加每张优惠券的优惠总金额)
            discountDTO.setDiscountAmount(discountDTO.getDiscountAmount() + discountPrice); //不能写discountPrice，因为这是一张优惠券的优惠金额
        }
        return discountDTO;
    }

    /**
     * 目的：优惠券使用后，得到每个可优惠课程的优惠明细
     * 规则：除了最后一个商品的优惠明细为剩余优惠金额(可优惠总金额 - 前面商品已优惠金额)，其他商品的优惠明细为按比例计算
     * @param courseDiscountMap 优惠明细映射
     * @param orderCourses 可优惠课程集合
     * @param discountPrice 可优惠金额
     * @param price 可优惠课程的总价
     */
    private void calculateDetailDiscount(Map<Long, Integer> courseDiscountMap, List<OrderCourseDTO> orderCourses, int discountPrice, int price) {
        //剩余待分配的优惠金额
        int leftDiscountPrice = discountPrice;
        //该课程的优惠明细
        int detailDiscount = 0;
        //循环每个可优惠课程，分配优惠明细
        for (int i = 0; i < orderCourses.size(); i++) {
            OrderCourseDTO course = orderCourses.get(i);
            if(i == orderCourses.size() - 1){
                //如果是最后一门可用课程，那就剩余的待分配优惠金额都给该课程。(为了保证优惠明细和优惠总金额相等)
                detailDiscount = leftDiscountPrice;
            }else{
                //更新优惠明细映射
                // 该课程优惠明细 = 该课程价格 / 可用课程总价 * 优惠金额 (有bug，前者都为int类型，相除为0)
                // int detailDiscount = orderCourses.get(i).getPrice() / price * discountPrice;
                // 该课程优惠明细 = 该课程价格 * 优惠金额 / 可用课程总价 (修改计算顺序)
                detailDiscount = course.getPrice() * discountPrice * price;
                //更新待分配的优惠金额
                leftDiscountPrice -= detailDiscount;
            }
            //更新该课程的优惠明细(之前该课程的总优惠明细+当前优惠明细)
            courseDiscountMap.put(course.getId(), courseDiscountMap.get(course.getId()) + detailDiscount);
        }
    }


    /**
     * 根据参数的优惠券集合，查询每个优惠券对于参数中的课程的可用情况
     * 注意：如果优惠券没有指定类别，就设置为参数中的所有课程都可用
     * @param coupons 优惠券集合
     * @param orderCourses 课程集合
     * @return 优惠券于可用课程的映射
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupons(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        HashMap<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        //1.循环可用优惠券，查询每张优惠券的课程集合
        for (Coupon coupon : coupons) {
            //提前初始化该券为所有课程可用，如果查出了可用的课程就设置为可用课程
            List<OrderCourseDTO> availableCourses = orderCourses;
            //2.查询每张优惠券的可用课程集合
            //2.1判断该优惠券是否指定了类别
            if(coupon.getSpecific()){
                //2.2查询优惠券的可用类别集合
                List<CouponScope> list = scopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                List<Long> cateIds = list.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                //2.3查询优惠券的可用课程集合
                availableCourses = orderCourses.stream().filter(orderCourseDTO -> cateIds.contains(orderCourseDTO.getCateId())).collect(Collectors.toList());
            }
            //如果该优惠券不存在可用课程，则直接跳过
            if(CollUtils.isEmpty(availableCourses)) continue;
            //3.要使用该优惠券，所以要计算该优惠券对于自身可用的课程可优惠多少钱
            int price = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            //4.判断该优惠券是否满足使用条件
            boolean canUse = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(price, coupon);
            if(canUse){
                //5.如果优惠券是可用使用的，则将该券添加到结果集中
                map.put(coupon, availableCourses);
            }
        }
        return map;
    }
}
