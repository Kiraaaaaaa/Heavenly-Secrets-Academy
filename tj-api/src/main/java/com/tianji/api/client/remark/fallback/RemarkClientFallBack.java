package com.tianji.api.client.remark.fallback;

import com.tianji.api.client.remark.RemarkClient;
import com.tianji.common.domain.dto.LikeRecordFormDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

/**
 * 降级类
 * 为什么没有使用Component注解？
 * 如果在这里使用，其它服务调用要使用该降级类需要每次都来扫描这个这种类
 * 所以为了避免每次都扫描，将该类通过FallbackConfig文件进行配置。但FallbackConfig本身又需要被添加到spring容器中
 * 所以spring.factories文件中添加了FallbackConfig的类路径，只要其它服务调用api模块，就会加载spring.factories
 * 这样，当其它模块调用点赞服务失败时，只需要在spring容器中找到该降级类进行处理即可
 */
@Slf4j
public class RemarkClientFallBack implements FallbackFactory<RemarkClient> {
    // 如果点赞服务调用失败，则调用该方法，
    @Override
    public RemarkClient create(Throwable cause) {
        log.info("调用点赞服务失败，原因是：{}", cause.getMessage());

        return new RemarkClient(){
            @Override
            public Set<Long> getLikedStatusByBizList(Iterable<Long> bizIds) {
                return null;
            }
            @Override
            public void addLikeRecord(LikeRecordFormDTO dto) {
                return;
            }
        };
    }
}
