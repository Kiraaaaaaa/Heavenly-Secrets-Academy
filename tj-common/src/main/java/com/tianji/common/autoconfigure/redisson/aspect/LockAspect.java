package com.tianji.common.autoconfigure.redisson.aspect;

import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.RequestTimeoutException;
import com.tianji.common.utils.AspectUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@Aspect
public class LockAspect {

    private final RedissonClient redissonClient;

    public LockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    //通过环绕加锁，方法执行前加锁，方法执行后根据注解使用解锁
    @Around("@annotation(lock)")
    public Object handleLock(ProceedingJoinPoint pjp, Lock lock) throws Throwable {
        String redisKey = AspectUtils.parse(lock.formatter(), AspectUtils.getMethod(pjp), pjp.getArgs());
        //得到锁
        RLock rLock = redissonClient.getLock(redisKey);
        long waitTime = 0;
        if (lock.block()) { //阻塞等待资源,最多等2分钟
            //根据时间单位转换成ms
            waitTime = lock.wtUnit().toMillis(lock.waitTime());
        }
        boolean success = rLock.tryLock(waitTime, lock.time(), lock.unit());
        if (!success && !lock.block()) { //未阻塞要求的情况下未得到锁
            throw new BadRequestException(ErrorInfo.Msg.REQUEST_OPERATE_FREQUENTLY);
        }
        if (!success) { //阻塞情况下未得到锁，请求超时
            throw new RequestTimeoutException(ErrorInfo.Msg.REQUEST_TIME_OUT);
        }

        try {
            return pjp.proceed();
        } finally {
            if(lock.unlock() || lock.block()){ //如果是阻塞锁的话，不管任务执行成功还是失败，都要进行解锁
                rLock.unlock();
            }
        }

    }
}
