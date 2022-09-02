package com.tianji.api.client.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient("order-service")
public interface OrderClient {
    @GetMapping("/order-details/enrollNum")
    Map<Long, Integer> countEnrollNumOfCourse(List<Long> courseIdList);
    @GetMapping("/order-details/course/{id}")
    Boolean checkMyLesson(@PathVariable("id") Long id);
}
