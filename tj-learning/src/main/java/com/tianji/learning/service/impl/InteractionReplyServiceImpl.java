package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-11-24
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;

    @Override
    public void addReplyOrAnswer(ReplyDTO dto) {
        //1.获取用户
        Long user = UserContext.getUser();
        //2.保存回答或评论
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(user);
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        boolean save = save(reply);
        if(!save) throw new DbException("数据库保存该回答/评论失败");
        //3.判断是评论还是回答
        //3.1是回答
        if(dto.getAnswerId() == null && dto.getTargetReplyId() == null){
            //更新AnswerId为0并累计回答次数
            question.setAnswerTimes(question.getAnswerTimes()+1);
            question.setLatestAnswerId(reply.getId());
        }else{
            //3.2是评论
            //更新被评论对象的被评论次数
            InteractionReply byId = getById(dto.getAnswerId());
            byId.setReplyTimes(byId.getReplyTimes()+1);
            updateById(byId);
        }
        //4.是学生，则更新该问题状态为未查看(提醒老师该问题有新的回答)
        if(dto.getIsStudent()){
            question.setStatus(QuestionStatus.UN_CHECK);
            questionMapper.updateById(question);
        }
    }

    @Override
    public PageDTO<ReplyVO> queryReplyOrAnswerPage(ReplyPageQuery query, Boolean isAdmin) {
        //校验QuestionId和AnswerId都为空
        Long answerId = query.getAnswerId();
        Long questionId = query.getQuestionId();
        if(answerId == null && questionId == null){
            throw new BadRequestException("错误参数，被回答id和问题id不能都为NULL");
        }
        //1.查询问题下的回答列表 或者 回答下的评论列表
        Page<InteractionReply> page = lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                //查询回答列表(在问题详情页面触发)，或查询评论列表(在点击评论后触发)
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                //不查询被隐藏的回答或者评论 todo //一个待优化的地方，这里是不查询出被隐藏的回答或者评论，优点是提高了数据库效率，缺点就是子级如果要保留，那他将无法获取被评论者的id。如果想要连同子级的评论一起隐藏，那么就需要在这里查出此条，在vo处排除掉父级是此条的评论
                .eq(!isAdmin, InteractionReply::getHidden, Boolean.FALSE)
                //点赞倒叙
                .page(query.toMpPage("liked_times", false));
        List<InteractionReply> replyList = page.getRecords();
        if(CollUtils.isEmpty(replyList)){
            return PageDTO.empty(page);
        }
        //2.远程批量查询用户信息
        Set<Long> userIds = new HashSet<>();
        for (InteractionReply rep : replyList) {
            //添加回复者id
            userIds.add(rep.getUserId());
            //添加被回复者id(如果只添加回复者id，可能会丢失回复者信息，比如查询评论列表时，replyList的数据是该回答下的，但回答者的信息会丢失)
            if(query.getAnswerId()!=null){
                userIds.add(rep.getTargetUserId());
            }
        }
        Map<Long, UserDTO> userDTOMap = userClient.queryUserByIds(userIds).stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //3.查询用户点赞状态 todo
        // Set<Long> bizLiked = remarkClient.isBizLiked(CollUtils.singletonList(id));
        //3.封装vo
        List<ReplyVO> vos = BeanUtils.copyList(replyList, ReplyVO.class);
        List<ReplyVO> collect = vos.stream().map(i -> {
            //3.1设置用户信息
            //如果该回答或评论非匿名则设置用户信息
            if (!i.getAnonymity() || isAdmin) {
                UserDTO userDTO = userDTOMap.get(i.getUserId());
                if (userDTO != null) {
                    i.setUserIcon(userDTO.getIcon());
                    i.setUserName(userDTO.getName());
                    i.setUserType(userDTO.getType());
                }
            }
            //如果该评论的上一级还是评论，那么设置该评论的评论对象用户信息(注意不是字段一定不要是TargetUserId的值，必须是TargetReplyId才能表示评论的对象也是评论)
            //由于我是先将replyList复制到了List<ReplyVO>，因此丢失了上级用户id，所以自己新增了TargetReplyId和TargetUserId字段
            if (i.getTargetReplyId() != 0) {
                Long upId = i.getTargetReplyId();
                //得到上一级的评论
                InteractionReply upReply = getById(upId);
                //上一级评论不是匿名才设置TargetUserName
                if(!upReply.getAnonymity() || isAdmin){
                    UserDTO userDTO = userDTOMap.get(upReply.getUserId());
                    if (userDTO != null) {
                        i.setTargetUserName(userDTO.getName());
                    }
                }
            }
            //3.2设置用户点赞信息
            // i.setLiked(bizLiked.contains(i.getId()));
            return i;
        }).collect(Collectors.toList());
        return PageDTO.of(page, collect);
    }

    @Override
    public PageDTO<ReplyVO> queryReplyOrAnswerPageAdmin(ReplyPageQuery query) {
        return queryReplyOrAnswerPage(query, Boolean.TRUE);
    }

    @Override
    public void hiddenReplyAdmin(Long id, Boolean hidden) {
        //1.查询该回答或评论
        InteractionReply reply = lambdaQuery()
                .eq(InteractionReply::getId, id)
                .one();
        //参数校验，如果是隐藏，hidden参数为true，反之。只有满足此判断才是正常的请求，防止刷接口
        if(hidden && reply.getHidden()){
            throw new BadRequestException("参数错误，禁止重复提交");
        }
        //2.设置该回答或评论是否隐藏
        reply.setHidden(hidden);
        updateById(reply);

        //3.设置该评论上级答案的评论数变化 (把隐藏和显示两种情况抽象成一个逻辑)
        //设置操作数
        int num = hidden ? -1 : 1;
        //如果当前对象是评论(则需要设置答案的评论数变化)
        if(reply.getAnswerId() != 0){
            InteractionReply answer = lambdaQuery()
                    .eq(InteractionReply::getId, reply.getAnswerId())
                    .one();
            //设置答案的评论数
            answer.setReplyTimes(answer.getReplyTimes() + num);
            updateById(answer);
        }else{
            //如果当前对象是回答(则需要设置问题的回答数变化)
            InteractionQuestion question = questionMapper.selectById(reply.getQuestionId());
            //设置问题的答案数
            question.setAnswerTimes(question.getAnswerTimes() + num);
            questionMapper.updateById(question);
        }
    }

    @Override
    public ReplyVO queryReplyById(Long id) {
        //1.查询评论
        InteractionReply reply = lambdaQuery()
                .eq(InteractionReply::getId, id)
                .one();
        ReplyVO replyVO = BeanUtils.copyBean(reply, ReplyVO.class);
        //2.设置用户信息
        UserDTO userDTO = userClient.queryUserById(replyVO.getUserId());
        replyVO.setUserIcon(userDTO.getIcon());
        replyVO.setUserName(userDTO.getName());
        replyVO.setUserId(userDTO.getId());
        return replyVO;
    }
}
