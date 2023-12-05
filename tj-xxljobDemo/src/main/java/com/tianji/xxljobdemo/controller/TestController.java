package com.tianji.xxljobdemo.controller;

import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    /**
     * 测试同步线程
     */
    @GetMapping("test")
    // 使用异步注解，底层其实也是使用Springboot的ThreadPoolTaskExecutor线程池
    // 如果是springboot2.0.9以前需要加上("线程池id")
    // 但是config配置了自定义线程池配置，所以加上配置的线程池id
    @Async("generateExchangeCodeExecutor")
    public void test(){
        //(使用JDK线程池:ThreadPoolExecutor)未启用异步前的名称：http-nio-8099-exec-6
        //(使用SpringBoot线程池:ThreadPoolTaskExecutor)启用Async异步后的名称：task-1
        System.out.println("同步线程启动："+Thread.currentThread().getName());
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("同步线程结束："+Thread.currentThread().getName());
    }


}
