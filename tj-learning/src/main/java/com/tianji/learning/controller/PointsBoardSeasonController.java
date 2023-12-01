package com.tianji.learning.controller;


import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author fenny
 * @since 2023-11-29
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/boards")
@Api(tags = "积分相关接口")
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService seasonService;

    @ApiOperation("查询赛季信息列表")
    @GetMapping("/seasons/list")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons(){
        List<PointsBoardSeason> list = seasonService.lambdaQuery().lt(PointsBoardSeason::getBeginTime, LocalDateTime.now()).list();
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        return BeanUtils.copyList(list, PointsBoardSeasonVO.class);
    }

}
