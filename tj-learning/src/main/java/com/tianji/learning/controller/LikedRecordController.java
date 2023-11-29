package com.tianji.learning.controller;


import com.tianji.api.client.remark.RemarkClient;
import com.tianji.common.domain.dto.LikeRecordFormDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author fenny
 * @since 2023-11-26
 */
@Api(tags = "点赞相关接口")
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikedRecordController {

    private final RemarkClient remarkClient;
    @ApiOperation("点赞或者取消赞")
    @PostMapping()
    public void addLikeRecord(@RequestBody @Validated LikeRecordFormDTO dto){
        remarkClient.addLikeRecord(dto);
    }
}
