package com.tianji.message.properties;

import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 线程池配置
 * @ClassName ThreadPoolProperties
 * @Author wusongsong
 * @Date 2022/6/23 15:36
 * @Version
 **/
@Configuration
@Data
public class ThreadPoolProperties {

    @NestedConfigurationProperty
    private Map<String, Object> threadPools;


}
