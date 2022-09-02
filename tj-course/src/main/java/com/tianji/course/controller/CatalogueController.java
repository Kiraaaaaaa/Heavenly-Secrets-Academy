package com.tianji.course.controller;

import com.tianji.course.domain.vo.CataSimpleInfoVO;
import com.tianji.course.service.ICourseCatalogueService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录课程相关接口
 *
 * @author wusongsong
 * @since 2022/7/27 13:59
 * @version 1.0.0
 **/
@Api(tags = "章节目录相关接口")
@RestController
@RequestMapping("catalogues")
public class CatalogueController {

    @Autowired
    private ICourseCatalogueService courseCatalogueService;

    @GetMapping("batchQuery")
    @ApiOperation("根据章节目录批量查询基础信息")
    public List<CataSimpleInfoVO> batchQuery(@RequestParam("ids") List<Long> ids) {
        return courseCatalogueService.getManyCataSimpleInfo(ids);
    }
}
