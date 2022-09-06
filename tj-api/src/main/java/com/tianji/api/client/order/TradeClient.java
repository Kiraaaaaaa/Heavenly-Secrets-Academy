package com.tianji.api.client.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient("trade-service")
public interface TradeClient {
    @GetMapping("/order-details/enrollNum")
    Map<Long, Integer> countEnrollNumOfCourse(@RequestParam("courseIdList") List<Long> courseIdList);
    @GetMapping("/order-details/course/{id}")
    Boolean checkMyLesson(@PathVariable("id") Long id);
}
