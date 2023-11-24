package com.tinaji;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorTest {
    public static void main(String[] args) {
        //创建线程池
        //
        // Executors.newFixedThreadPool() //创建固定大小线程池
        // Executors.newSingleThreadExecutor() //创建单线程线程池
        // Executors.newCachedThreadPool() //创建缓存线程池
        // Executors.newScheduledThreadPool() //创建可延迟执行的线程池

        //创建线程池
        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(12, 12, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>(10));
        //建议1. 如果任务为cpu运算型，核心线程适合设置为cpu核心数
        //建议2. 如果任务为io型，推荐设置为cpu最大线程数
    }
}
