package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author fenny
 * @since 2023-11-24
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void addReplyOrAnswer(ReplyDTO dto);

    PageDTO<ReplyVO> queryReplyOrAnswerPage(ReplyPageQuery query, Boolean isAdmin);

    PageDTO<ReplyVO> queryReplyOrAnswerPageAdmin(ReplyPageQuery query);

    void hiddenReplyAdmin(Long id, Boolean hidden);

    ReplyVO queryReplyById(Long id);
}
