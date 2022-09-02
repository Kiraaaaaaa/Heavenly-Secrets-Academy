package com.tianji.common.autoconfigure.redisson.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁
 *
 * @ClassName Lock
 * @author wusongsong
 * <p>
 * 加锁操作,
 * 抢夺执行权。
 * unlock默认为true 方法执行结束会主动释放锁。
 * 未检测到主动释放锁，则会在1分钟(默认)后自动释放锁.
 * key: 锁的KEY。必须填写。不可于其他锁的KEY重复。
 * time: 锁时间。默认为1
 * unit: 锁时间单位。默认为分钟.
 * unlock: 是否主动解锁。默认为是
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lock {


    //加锁key的表达式，支持表达式
    String formatter();

    //加锁时长
    long time() default 5;

    //阻塞超时时间，默认2分钟,当block为true的时候生效
    long waitTime() default 120;

    //阻塞超时时间单位，默认s
    TimeUnit wtUnit() default TimeUnit.SECONDS;

    //加锁时间单位
    TimeUnit unit() default TimeUnit.SECONDS;

    //方法访问完后要不要解锁，默认不解锁
    boolean unlock() default false;

    //如果设定了true,将等待处理后进行处理
    boolean block() default false;


}
