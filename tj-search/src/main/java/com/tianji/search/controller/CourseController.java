package com.tianji.search.controller;

import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseAdminVO;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.search.service.ISearchService;
import com.tianji.common.domain.dto.PageDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("courses")
@Api(tags = "课程搜索接口")
public class CourseController {

    @Resource
    private ISearchService searchService;

    @ApiOperation("管理端课程搜索接口")
    @GetMapping("/admin")
    public PageDTO<CourseAdminVO> queryCoursesForAdmin(CoursePageQuery query){
        return searchService.queryCoursesForAdmin(query);
    }

    @ApiOperation("用户端课程搜索接口")
    @GetMapping("/portal")
    public PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query){
        return searchService.queryCoursesForPortal(query);
    }

    @ApiIgnore
    @GetMapping("/name")
    public List<Long> queryCoursesIdByName(@RequestParam("keyword") String keyword){
        return searchService.queryCoursesIdByName(keyword);
    }
}
