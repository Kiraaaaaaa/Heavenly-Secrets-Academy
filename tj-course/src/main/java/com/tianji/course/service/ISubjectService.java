package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.course.domain.dto.SubjectPageParamDTO;
import com.tianji.course.domain.dto.SubjectSaveDTO;
import com.tianji.course.domain.po.Subject;
import com.tianji.course.domain.vo.SubjectInfoVO;
import com.tianji.course.domain.vo.SubjectSimpleVO;
import com.tianji.course.domain.vo.SubjectVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 题目 服务类
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-15
 */
public interface ISubjectService extends IService<Subject> {

    PageDTO<SubjectVO> page(SubjectPageParamDTO subjectPageParamDTO, PageQuery pageQuery);

    SubjectInfoVO get(Long id);

    void save(SubjectSaveDTO subjectSaveDTO);

    void delete(Long id);

    /**
     * 批量获取老师的出题数量
     * @param teacherIds 教师id
     * @return 老师出题数量
     */
    Map<Long, Long> countSubjectNumOfTeacher(List<Long> teacherIds);

    /**
     * 根据小节或考试id获取考试题目
     *
     * @param cataId 目录id
     * @return 该目录（小节）下的考试题
     */
    List<SubjectSimpleVO> queryByCataId(Long cataId);
}
