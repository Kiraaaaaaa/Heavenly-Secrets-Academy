package com.tianji.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.course.MediaQuoteDTO;
import com.tianji.api.dto.course.SectionInfoDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.course.constants.CourseConstants;
import com.tianji.course.constants.CourseErrorInfo;
import com.tianji.course.domain.po.CataIdAndSubScore;
import com.tianji.course.domain.po.CourseCatalogue;
import com.tianji.course.domain.vo.CataSimpleInfoVO;
import com.tianji.course.domain.vo.CataVO;
import com.tianji.course.mapper.CourseCataSubjectMapper;
import com.tianji.course.mapper.CourseCatalogueMapper;
import com.tianji.course.properties.CourseProperties;
import com.tianji.course.service.ICourseCatalogueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 目录草稿 服务实现类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-19
 */
@Service
public class CourseCatalogueServiceImpl extends ServiceImpl<CourseCatalogueMapper, CourseCatalogue> implements ICourseCatalogueService {

    @Autowired
    private CourseCataSubjectMapper courseCataSubjectMapper;

    @Autowired
    private CourseProperties courseProperties;

    @Override
    public List<CataVO> queryCourseCatalogues(Long courseId,Boolean withPractice) {
        LambdaQueryWrapper<CourseCatalogue> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseCatalogue::getCourseId, courseId);
        if(!withPractice){
            queryWrapper.in(CourseCatalogue::getType,
                    Arrays.asList(CourseConstants.CataType.SECTION, CourseConstants.CataType.CHAPTER));
        }
        queryWrapper.last(" order by type,c_index");
        List<CourseCatalogue> courseCatalogues = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courseCatalogues)) {
            return null;
        }

        //课程的数量和分数
        List<CataIdAndSubScore> cataIdAndSubScores = courseCataSubjectMapper.queryCataIdAndScoreByCorseId(courseId);
        //练习和题目数量map
        Map<Long, Long> cataIdAndNumMap = CollUtils.isEmpty(cataIdAndSubScores) ? new HashMap<>() :
                cataIdAndSubScores.stream().collect(Collectors.groupingBy(CataIdAndSubScore::getCataId, Collectors.counting()));
        Map<Long, Integer> cataIdAndTotalScoreMap = CollUtils.isEmpty(cataIdAndSubScores) ? new HashMap<>() :
                cataIdAndSubScores.stream().collect(Collectors.groupingBy(CataIdAndSubScore::getCataId, Collectors.summingInt(CataIdAndSubScore::getScore)));


        List<CataVO> cataVOS = TreeDataUtils.parseToTree(courseCatalogues, CataVO.class, (courseCatalogue, cataVO)->{
            cataVO.setMediaName(courseCatalogue.getVideoName());
            cataVO.setIndex(courseCatalogue.getCIndex());
            cataVO.setSubjectNum(NumberUtils.null2Zero(cataIdAndNumMap.get(courseCatalogue.getId())).intValue()); //练习总数量
            cataVO.setTotalScore(NumberUtils.null2Zero(cataIdAndTotalScoreMap.get(courseCatalogue.getId()))); //练习总分数
        }, new CourseCatalogDataWrapper());

        return cataVOS;
    }

    @Override
    public List<MediaQuoteDTO> countMediaUserInfo(List<Long> mediaIds) {
        if (CollUtils.isEmpty(mediaIds)) {
            return new ArrayList<>();
        }
        //根据媒资id查询所有的媒资资料
        LambdaQueryWrapper<CourseCatalogue> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(CourseCatalogue::getMediaId, mediaIds);
        List<CourseCatalogue> courseCatalogues = baseMapper.selectList(queryWrapper);
        if (CollUtils.isEmpty(courseCatalogues)) {
            //媒资都未被引用，都给一个引用次数0
            return mediaIds.stream().map(mediaId -> new MediaQuoteDTO(mediaId, 0)).collect(Collectors.toList());
        }
        //分组求和
        Map<Long, Long> mediaAndCount = courseCatalogues.stream().collect(Collectors.groupingBy(CourseCatalogue::getMediaId, Collectors.counting()));
        //遍历媒资id为每个媒资id设置引用次数
        return mediaIds.stream().map(
                mediaId -> new MediaQuoteDTO(mediaId, NumberUtils.null2Zero(mediaAndCount.get(mediaId)).intValue())
        ).collect(Collectors.toList());
    }

    @Override
    public SectionInfoDTO getSimpleSectionInfo(Long sectionId) {
        if (sectionId == null) {
            throw new BizIllegalException(CourseErrorInfo.Msg.CATEGORY_QUERY_ID_NULL);
        }
        //sectionId小节id，也是目录id
        CourseCatalogue courseCatalogue = baseMapper.selectById(sectionId);
        if (courseCatalogue == null) {
            return new SectionInfoDTO();
        }
        if (courseCatalogue.getType() != CourseConstants.CataType.SECTION) {
            return new SectionInfoDTO();
        }
        SectionInfoDTO sectionInfoDTO = BeanUtils.toBean(courseCatalogue, SectionInfoDTO.class);
        sectionInfoDTO.setFreeDuration(courseCatalogue.getTrailer() == 1 ?
                courseProperties.getMedia().getTrailerDuration() : 0);
        return sectionInfoDTO;
    }

    @Override
    public List<CataSimpleInfoVO> getCatasIndexList(Long courseId) {

        //获取目录,练习不查询
        LambdaQueryWrapper<CourseCatalogue> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseCatalogue::getCourseId, courseId)
                .in(CourseCatalogue::getType, Arrays.asList(
                        CourseConstants.CataType.CHAPTER,
                        CourseConstants.CataType.SECTION
                ));
        List<CourseCatalogue> courseCatalogues = baseMapper.selectList(queryWrapper);
        if(CollUtils.isEmpty(courseCatalogues)){
            return new ArrayList<>();
        }
        //章id与章序号映射关系
        Map<Long, Integer> chapterMap = courseCatalogues.stream().filter(
                        courseCatalogue -> courseCatalogue.getType() == CourseConstants.CataType.CHAPTER)
                .collect(Collectors.toMap(CourseCatalogue::getId, CourseCatalogue::getCIndex));
        //课程
        List<CataSimpleInfoVO> simpleInfoVOS = new ArrayList<>();
        courseCatalogues.stream().filter(
                courseCatalogue -> courseCatalogue.getType() != CourseConstants.CataType.CHAPTER)
                .forEach(courseCatalogue -> {
                    //序号 例如1-1
                    String index = StringUtils.format("{}-{}",
                            chapterMap.get(courseCatalogue.getParentCatalogueId()),courseCatalogue.getCIndex());
                    simpleInfoVOS.add(new CataSimpleInfoVO(courseCatalogue.getId(),
                            courseCatalogue.getName(), index, courseCatalogue.getCIndex()));
                });
        return simpleInfoVOS;
    }

    @Override
    public List<CataSimpleInfoVO> getManyCataSimpleInfo(List<Long> ids) {
        if(CollUtils.isEmpty(ids)){
            return new ArrayList<>();
        }
        //获取目录
        LambdaQueryWrapper<CourseCatalogue> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(CourseCatalogue::getId, ids);
        List<CourseCatalogue> courseCatalogues = baseMapper.selectList(queryWrapper);
        return BeanUtils.copyList(courseCatalogues, CataSimpleInfoVO.class);
    }

    private static class CourseCatalogDataWrapper implements TreeDataUtils.DataProcessor<CataVO, CourseCatalogue> {


        @Override
        public Object getParentKey(CourseCatalogue courseCatalogue) {
            return courseCatalogue.getParentCatalogueId();
        }

        @Override
        public Object getKey(CourseCatalogue courseCatalogue) {
            return courseCatalogue.getId();
        }

        @Override
        public Object getRootKey() {
            return 0L;
        }

        @Override
        public List<CataVO> getChild(CataVO cataVO) {
            return cataVO.getSections();
        }

        @Override
        public void setChild(CataVO parent, List<CataVO> child) {
            parent.setSections(child);
        }
    }
}
