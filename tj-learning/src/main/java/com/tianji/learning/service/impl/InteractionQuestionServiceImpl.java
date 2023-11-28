package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-11-24
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;

    private final UserClient userClient;

    private final CourseClient courseClient;

    private final SearchClient searchClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.数据转换
        InteractionQuestion question = BeanUtils.toBean(questionDTO, InteractionQuestion.class);
        // 3.补充数据
        question.setUserId(userId);
        // 4.保存问题
        save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionDTO) {
        if(StringUtils.isBlank(questionDTO.getTitle()) || StringUtils.isBlank(questionDTO.getDescription()) || questionDTO.getAnonymity()==null){
            throw new BadRequestException("请求参数不能为空");
        }
        InteractionQuestion question = getById(id);
        if(question == null){
            throw new BizIllegalException("该问题已被删除");
        }
        question.setTitle(questionDTO.getTitle());
        question.setDescription(questionDTO.getDescription());
        question.setAnonymity(questionDTO.getAnonymity());
        updateById(question);
    }

    @Override
    public void deleteQuestion(Long id) {
        Long user = UserContext.getUser();
        InteractionQuestion question = getById(id);
        if(question == null){
            return;
        }
        if(!user.equals(question.getUserId())){
            throw new BizIllegalException("该问题提问者非当前用户，无法删除");
        }
        removeById(id);
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        //查询出符合搜索条件的课程ids
        String courseName = query.getCourseName();
        List<Long> cIds = new ArrayList<>();
        if(StringUtils.isNotBlank(courseName)){
            cIds = searchClient.queryCoursesIdByName(courseName);
            //没查到课程就直接返回空数据
            if(CollUtils.isEmpty(cIds)){
                return PageDTO.empty(0L, 0L);
            }
        }
        //1.查出问题集合
        Page<InteractionQuestion> page = this.lambdaQuery()
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .in(CollUtils.isNotEmpty(cIds), InteractionQuestion::getCourseId, cIds)
                .gt(query.getBeginTime() != null, InteractionQuestion::getCreateTime, query.getBeginTime())
                .lt(query.getEndTime() != null, InteractionQuestion::getCreateTime, query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> questions = page.getRecords();
        if(CollUtils.isEmpty(questions)) return PageDTO.empty(0L, 0L);
        //查询用户ids和章、节ids
        ArrayList<Long> uIds = new ArrayList<>();
        ArrayList<Long> chapterAndSectionIds = new ArrayList<>();
        for (InteractionQuestion question : questions) {
            uIds.add(question.getUserId());
            chapterAndSectionIds.add(question.getSectionId());
            chapterAndSectionIds.add(question.getChapterId());
        }

        //2.远程查询课程信息
        //2.1远程调用搜索服务查询课程ids
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(cIds);
        if(CollUtils.isEmpty(cinfos)){
            throw new BizIllegalException("课程集合不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cInfoMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //3.远程批量查询章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if(CollUtils.isEmpty(catas)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        //4.远程批量查询用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户集合不存在");
        }
        Map<Long, String> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        //6.封装vo
        List<QuestionAdminVO> vos = questions.stream().map(i -> {
            QuestionAdminVO vo = BeanUtils.copyBean(i, QuestionAdminVO.class);
            //6.1设置课程信息
            CourseSimpleInfoDTO cInfoDTO = cInfoMap.get(i.getCourseId());
            vo.setCourseName(cInfoDTO.getName());
            //6.2设置用户信息
            if(userMap != null){
                vo.setUserName(userMap.get(i.getUserId()));
            }
            //6.3设置章节信息
            if(cataMap != null){
                vo.setSectionName(cataMap.get(i.getSectionId()));
                vo.setChapterName(cataMap.get(i.getChapterId()));
            }
            //6.4设置分类信息(使用自定义的缓存工具类获取分类缓存信息)
            List<Long> categoryIds = cInfoDTO.getCategoryIds();
            String categoryNames = categoryCache.getCategoryNames(categoryIds);
            vo.setCategoryName(categoryNames);
            return vo;
        }).collect(Collectors.toList());
        return PageDTO.of(page, vos);
    }

    @Override
    public void hiddenQuestionAdmin(Long id, Boolean hidden) {
        InteractionQuestion question = getById(id);
        if(question == null){
            throw new BadRequestException("该问题不存在");
        }
        question.setHidden(hidden);
        updateById(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1.校验courseId
        AssertUtils.isNotNull(query.getCourseId(), "错误请求，课程id不能为空");
        //2.获取用户
        Long user = UserContext.getUser();
        //3.查询问题分页
        Page<InteractionQuestion> questionPage = lambdaQuery()
                //排除问题详情字段，主要原因是详情占用情况(2048字节)太大
                .select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
                    @Override
                    public boolean test(TableFieldInfo tableFieldInfo) {
                        return !tableFieldInfo.getProperty().equals("description");
                    }
                })
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, user)
                .eq(InteractionQuestion::getHidden, Boolean.FALSE)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> questions = questionPage.getRecords();
        //4.批量查询reply表->获取最新回答信息
        List<Long> replyIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        //存放最新回答ids和用户ids，如果采用stream流会循环两次，这里一次
        for (InteractionQuestion question : questions) {
            Long latestAnswerId = question.getLatestAnswerId();
            //该问题有回答者才存放回答id
            if(latestAnswerId != null){
                replyIds.add(latestAnswerId);
            }
            //该问题如果不是匿名才存放提问者id
            if(!question.getAnonymity()){
                userIds.add(question.getUserId());
            }
        }
        Map<Long, InteractionReply> replyMap = null;
        if(CollUtils.isNotEmpty(replyIds)){
            //映射ids为回答map

            // replyMap = replyService.listByIds(replyList).stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));
            //直接查询回答的话有可能该回答已经被隐藏了，所以只查出未隐藏的回答
            List<InteractionReply> replyList = replyService.lambdaQuery()
                    .eq(InteractionReply::getHidden, Boolean.FALSE)
                    .in(InteractionReply::getId, replyIds)
                    .list();
            //把回答的回答者id拿出来，放到用户集合待查询个人信息
            for (InteractionReply reply : replyList) {
                if(!reply.getAnonymity()){
                    userIds.add(reply.getUserId());
                }
            }
            replyMap = replyList.stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));
        }

        //5.批量远程查询用户信息->获取用户名称和头像(回答者和提问者)
        Map<Long, UserDTO> userDTOMap = userClient.queryUserByIds(userIds).stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //6.封装vo信息
        Map<Long, InteractionReply> finalReplyMap = replyMap;
        List<QuestionVO> questionVOS = questions.stream().map(question -> {
            QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
            //提问者不是匿名状态则设置信息
            if (!question.getAnonymity() && userDTOMap != null) {
                questionVO.setUserIcon(userDTOMap.get(question.getUserId()).getIcon());
                questionVO.setUserName(userDTOMap.get(question.getUserId()).getName());
            }
            //该问题有回答
            if(finalReplyMap != null){
                InteractionReply reply = finalReplyMap.get(question.getLatestAnswerId());
                if(reply != null && userDTOMap != null){
                    UserDTO userDTO = userDTOMap.get(reply.getUserId());
                    if(userDTO != null){
                        questionVO.setLatestReplyUser(reply.getAnonymity()?"匿名用户":userDTO.getName());
                    }
                    //设置回答内容
                    questionVO.setLatestReplyContent(reply.getContent());
                }

            }
            return questionVO;
        }).collect(Collectors.toList());



        return PageDTO.of(questionPage, questionVOS);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.参数校验
        InteractionQuestion question = getById(id);
        if(id == null || question == null || question.getHidden()) return null;
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        //2.远程查询用户信息
        if(!question.getAnonymity()){
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if(userDTO != null){
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
            }
        }
        //3.设置
        return vo;
    }

    @Override
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        //1.查出问题集合
        InteractionQuestion question = this.lambdaQuery()
                .eq(InteractionQuestion::getId, id)
                .one();
        //查询用户ids和章、节ids
        ArrayList<Long> uIds = new ArrayList<>();
        ArrayList<Long> chapterAndSectionIds = new ArrayList<>();
        uIds.add(question.getUserId());
        chapterAndSectionIds.add(question.getSectionId());
        chapterAndSectionIds.add(question.getChapterId());

        //2.远程查询课程信息
        //2.1远程调用搜索服务查询课程id
        CourseFullInfoDTO course = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if(course == null){
            throw new BizIllegalException("该问题的课程不存在");
        }
        //3.远程批量查询章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if(CollUtils.isEmpty(catas)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        //4.远程批量查询用户信息
        uIds.addAll(course.getTeacherIds()); //管理端才查询教师信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户集合不存在");
        }
        Map<Long, UserDTO> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //6.封装vo
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //6.1设置课程信息
        vo.setCourseName(course.getName());
        //6.2设置用户信息
        if(userMap != null){
            vo.setUserName(userMap.get(question.getUserId()).getName());
            vo.setUserIcon(userMap.get(question.getUserId()).getIcon());
            vo.setTeacherName(userMap.get(course.getTeacherIds().get(0)).getName());
        }
        //6.3设置章节信息
        if(cataMap != null){
            vo.setSectionName(cataMap.get(question.getSectionId()));
            vo.setChapterName(cataMap.get(question.getChapterId()));
        }
        //6.4设置分类信息(使用自定义的缓存工具类获取分类缓存信息)
        List<Long> categoryIds = course.getCategoryIds();
        String categoryNames = categoryCache.getCategoryNames(categoryIds);
        vo.setCategoryName(categoryNames);
        //6.5设置被回答数量
        Integer anwserTimes = replyService.lambdaQuery()
                .eq(InteractionReply::getQuestionId, id)
                .eq(InteractionReply::getAnswerId, 0L)
                .count();
        vo.setAnswerTimes(anwserTimes);
        //7.修改问题状态为已查看
        boolean update = lambdaUpdate()
                .eq(InteractionQuestion::getId, id)
                .set(InteractionQuestion::getStatus, QuestionStatus.CHECKED)
                .update();
        if(update == false){
            throw new DbException("问题状态修改失败");
        }
        return vo;
    }
}
