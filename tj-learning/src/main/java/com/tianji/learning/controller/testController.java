package com.tianji.learning.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Api(tags = "测试")
@RestController
@RequestMapping("/admin/repl")
public class testController {
    @ApiOperation("新增回答或评论-用户端")
    @PostMapping
    public void test(){
        return;
    }
}
