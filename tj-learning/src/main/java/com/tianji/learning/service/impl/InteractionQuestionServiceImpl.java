package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

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
        return null;
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
        List<Long> replyList = new ArrayList<>();
        List<Long> askerIds = new ArrayList<>();
        //存放回答ids和提问者ids，如果采用stream流会循环两次，这里一次
        for (InteractionQuestion question : questions) {
            Long latestAnswerId = question.getLatestAnswerId();
            //该问题有回答者才存放回答id
            if(latestAnswerId != null){
                replyList.add(latestAnswerId);
            }
            //该问题如果不是匿名才存放提问者id
            if(!question.getAnonymity()){
                askerIds.add(question.getUserId());
            }
        }
        Map<Long, InteractionReply> replyMap = null;
        if(CollUtils.isNotEmpty(replyList)){
            //映射ids为回答map

            // replyMap = replyService.listByIds(replyList).stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));
            //直接查询回答的话有可能该回答已经被隐藏了，所以只查出未隐藏的回答
            replyMap = replyService.lambdaQuery()
                    .eq(InteractionReply::getHidden, Boolean.FALSE)
                    .in(InteractionReply::getId, replyList)
                    .list()
                    .stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));
        }

        //5.批量远程查询用户信息->获取用户名称和头像
        Map<Long, UserDTO> userDTOMap = userClient.queryUserByIds(askerIds).stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //6.封装vo信息
        Map<Long, InteractionReply> finalReplyMap = replyMap;
        List<QuestionVO> questionVOS = questions.stream().map(question -> {
            QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
            //提问者不是匿名状态则设置信息
            if (!question.getAnonymity() && userDTOMap != null) {
                questionVO.setUserIcon(userDTOMap.get(question.getUserId().intValue()).getIcon());
                questionVO.setUserName(userDTOMap.get(question.getUserId().intValue()).getName());
            }
            //该问题有回答
            if(finalReplyMap != null){
                InteractionReply reply = finalReplyMap.get(question.getLatestAnswerId());
                if(reply != null){
                    //该回答者未匿名才设置回答者id(前端会通过id查询回答者名称)
                    if(!reply.getAnonymity() && userDTOMap != null){
                        UserDTO userDTO = userDTOMap.get(reply.getUserId());
                        if(userDTO != null){
                            questionVO.setLatestReplyUser(userDTO.getName());
                        }
                    }
                    //设置回答
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
        //2.封装vo
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        //2.远程查询用户信息
        if(!question.getAnonymity()){
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if(userDTO != null){
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
            }
        }

        return vo;
    }
}
