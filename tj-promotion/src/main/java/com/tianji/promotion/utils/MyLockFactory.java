package com.tianji.promotion.utils;

import com.tianji.promotion.enums.MyLockType;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.enums.MyLockType.*;


@Component
public class MyLockFactory {

    private final Map<MyLockType, Function<String, RLock>> lockHandlers;

    public MyLockFactory(RedissonClient redissonClient) {
        this.lockHandlers = new EnumMap<>(MyLockType.class);
        //由于是函数式接口，所以可以直接使用lambda表达式，或者称之为方法引用
        this.lockHandlers.put(RE_ENTRANT_LOCK, redissonClient::getLock);
        //或者使用匿名内部类，效果是一样的
       /* this.lockHandlers.put(RE_ENTRANT_LOCK, new Function<String, RLock>() {
            @Override
            public RLock apply(String name) {
                return redissonClient.getLock(name);
            }
        });*/
        this.lockHandlers.put(FAIR_LOCK, redissonClient::getFairLock);
        this.lockHandlers.put(READ_LOCK, name -> redissonClient.getReadWriteLock(name).readLock());
        this.lockHandlers.put(WRITE_LOCK, name -> redissonClient.getReadWriteLock(name).writeLock());
    }

    public RLock getLock(MyLockType lockType, String name){
        return lockHandlers.get(lockType).apply(name);
    }
}