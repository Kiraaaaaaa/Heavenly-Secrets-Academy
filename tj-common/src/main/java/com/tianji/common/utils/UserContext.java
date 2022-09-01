package com.tianji.common.utils;

public class UserContext {
    private static final ThreadLocal<Long> TL = new ThreadLocal<>();

    public static void setUser(Long userId){
        TL.set(userId);
    }

    public static Long getUser(){
        return TL.get();
    }

    public static void removeUser(){
        TL.remove();
    }
}
