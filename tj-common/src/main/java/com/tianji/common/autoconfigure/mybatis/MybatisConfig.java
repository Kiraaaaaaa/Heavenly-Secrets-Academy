package com.tianji.common.autoconfigure.mybatis;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class})
public class MybatisConfig {

    /**
     * @deprecated 存在任务更新数据导致updater写入0或null的问题，暂时废弃
     * @see MyBatisAutoFillInterceptor 通过自定义拦截器来实现自动注入creater和updater
     */
    // @Bean
    // @ConditionalOnMissingBean
    public BaseMetaObjectHandler baseMetaObjectHandler(){
        return new BaseMetaObjectHandler();
    }

    //DynamicTableNameInnerInterceptor不是必须的，只有tj-learning项目中使用了动态表
    //required = false，如果不配置，则默认不生成动态表名
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Autowired(required = false)
            DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor
    ) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (dynamicTableNameInnerInterceptor != null) {
            //存在则添加动态表名拦截器
            interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        }
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        interceptor.addInnerInterceptor(new MyBatisAutoFillInterceptor());
        return interceptor;
    }
}
