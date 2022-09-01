package com.tianji.api.client.learning;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient("learning-service")
public interface LearningClient {

    @GetMapping("/lessons/me/{id}")
    Boolean checkMyLesson(@PathVariable("id") Long id);

    @GetMapping("/interests/ids")
    List<Long> queryMyInterestsIds();
}
