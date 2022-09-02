package com.tianji.course.controller;


import com.tianji.api.dto.course.SubjectDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.validate.annotations.ParamChecker;
import com.tianji.course.domain.dto.SubjectPageParamDTO;
import com.tianji.course.domain.dto.SubjectSaveDTO;
import com.tianji.course.domain.po.Subject;
import com.tianji.course.domain.vo.SubjectInfoVO;
import com.tianji.course.domain.vo.SubjectSimpleVO;
import com.tianji.course.domain.vo.SubjectVO;
import com.tianji.course.service.ISubjectService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName SubjectController
 * @author wusongsong
 * @since 2022/7/11 19:56
 * @version 1.0.0
 **/
@Api(tags = "题目相关接口")
@RestController
@RequestMapping("subjects")
@Slf4j
public class SubjectController {

    @Autowired
    private ISubjectService subjectService;

    @GetMapping("list")
    @ApiIgnore
    public List<SubjectDTO> queryByIds(@RequestParam("ids") List<Long> ids) {
        // 1.查询集合
        List<Subject> subjects = subjectService.listByIds(ids);
        // 2.数据处理
        List<SubjectDTO> list = new ArrayList<>(subjects.size());
        for (Subject subject : subjects) {
            SubjectDTO dto = BeanUtils.toBean(subject, SubjectDTO.class);
            dto.setAnswers(subject.getAnswers());
            dto.setOptions(subject.getOptions());
            list.add(dto);
        }
        return list;
    }

    @GetMapping("page")
    @ApiOperation("分页查询题目相关信息")
    public PageDTO<SubjectVO> page(SubjectPageParamDTO subjectPageParamDTO, PageQuery pageQuery) {
        return subjectService.page(subjectPageParamDTO, pageQuery);
    }

    @GetMapping("get/{id}")
    @ApiOperation("获取试题详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "题目id")
    })
    public SubjectInfoVO get(@PathVariable("id") Long id) {
        return subjectService.get(id);
    }

    @PostMapping("save")
    @ApiOperation("保存题目")
    @ParamChecker
    public void save(@RequestBody @Validated SubjectSaveDTO subjectSaveDTO) {
        subjectService.save(subjectSaveDTO);
    }

    @DeleteMapping("delete/{id}")
    @ApiOperation("删除题目")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "题目id")
    })
    public void delete(@PathVariable("id") Long id) {
        subjectService.delete(id);
    }

    @GetMapping("queryByCataId/{cataId}")
    @ApiOperation("根据小节或考试id，获取题目列表")
    public List<SubjectSimpleVO> queryByCataId(@PathVariable("cataId") Long cataId) {
        return subjectService.queryByCataId(cataId);
    }

}
