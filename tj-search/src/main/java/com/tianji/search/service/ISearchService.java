package com.tianji.search.service;

import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseAdminVO;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.common.domain.dto.PageDTO;

import java.util.List;

public interface ISearchService {

    List<CourseVO> queryCourseByCateId(Long cateLv2Id);

    List<CourseVO> queryBestTopN();

    List<CourseVO> queryNewTopN();

    List<CourseVO> queryFreeTopN();

    PageDTO<CourseAdminVO> queryCoursesForAdmin(CoursePageQuery query);

    PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query);

    List<Long> queryCoursesIdByName(String keyword);
}
