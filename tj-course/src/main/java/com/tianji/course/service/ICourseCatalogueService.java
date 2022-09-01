package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.course.MediaQuoteDTO;
import com.tianji.api.dto.course.SectionInfoDTO;
import com.tianji.course.domain.po.CourseCatalogue;
import com.tianji.course.domain.vo.CataSimpleInfoVO;
import com.tianji.course.domain.vo.CataVO;

import java.util.List;

/**
 * <p>
 * 目录草稿 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-19
 */
public interface ICourseCatalogueService extends IService<CourseCatalogue> {

    /**
     * 查询线上课程目录
     *
     * @param courseId
     * @return
     */
    List<CataVO> queryCourseCatalogues(Long courseId,Boolean withPractice);

    /**
     * 批量统计媒资id引用次数
     *
     * @param mediaIds
     * @return
     */
    List<MediaQuoteDTO> countMediaUserInfo(List<Long> mediaIds);

    /**
     * 获取简单的小节信息，
     *
     * @param sectionId 小节id
     * @return 课程id，媒资id，是否支持免费试看，免费试看时长
     */
    SectionInfoDTO getSimpleSectionInfo(Long sectionId);

    /**
     * 根据课程id获取课程的目录列表
     *
     * @param courseId
     * @return
     */
    List<CataSimpleInfoVO> getCatasIndexList(Long courseId);

    List<CataSimpleInfoVO> getManyCataSimpleInfo(List<Long> ids);
}
