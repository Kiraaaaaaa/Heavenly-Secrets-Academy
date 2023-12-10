package com.tianji.promotion.utils;

import com.tianji.promotion.enums.MyLockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyLock {
    //设置锁的名字
    String name();

    //设置锁的等待时间
    long waitTime() default 1;

    //设置锁的持有时间
    long leaseTime() default -1;

    //设置锁的时间单位
    TimeUnit unit() default TimeUnit.SECONDS;

    //设置锁的类型，默认是可重入锁
    MyLockType myLockType() default MyLockType.RE_ENTRANT_LOCK;

    //设置锁的策略，默认是失败重试
    MyLockStrategy myLockStrategy() default MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT;
}