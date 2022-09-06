package com.tianji.course.controller;


/**
 * @author wusongsong
 * @since 2022/7/11 19:56
 * @version 1.0.0
 **/
/*@Api(tags = "题目相关接口")
@RestController
@RequestMapping("subjects")
@Slf4j*/
public class SubjectController {

   /* @Autowired
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
*/
}
