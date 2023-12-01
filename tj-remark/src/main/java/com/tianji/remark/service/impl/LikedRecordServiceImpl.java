// package com.tianji.remark.service.impl;
//
// import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
// import com.tianji.common.constants.MqConstants;
// import com.tianji.common.utils.StringUtils;
// import com.tianji.common.utils.UserContext;
// import com.tianji.remark.domain.dto.LikeRecordFormDTO;
// import com.tianji.remark.domain.dto.LikedTimesDTO;
// import com.tianji.remark.domain.po.LikedRecord;
// import com.tianji.remark.mapper.LikedRecordMapper;
// import com.tianji.remark.service.ILikedRecordService;
// import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
// import jodd.util.StringUtil;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;
//
// import java.util.List;
// import java.util.Set;
// import java.util.stream.Collectors;
//
// /**
//  * <p>
//  * 点赞记录表 服务实现类
//  * </p>
//  *
//  * @author fenny
//  * @since 2023-11-26
//  */
// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//     private final RabbitMqHelper rabbitMqHelper;
//
//     @Override
//     public void addLikeRecord(LikeRecordFormDTO dto) {
//         //1.获取登录用户
//         Long user = UserContext.getUser();
//         //2.判断点赞类型
//        boolean flag = dto.getLiked() ? liked(user, dto) : unLiked(user, dto);
//         //点赞失败
//         if(!flag){
//             return;
//         }
//         //3.统计业务id的点赞数
//         Integer totalLikedNum = this.lambdaQuery()
//                 .eq(LikedRecord::getBizId, dto.getBizId())
//                 .count();
//         //4.发送消息到MQ
//         String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//         LikedTimesDTO msg = LikedTimesDTO.of(dto.getBizId(), totalLikedNum);
//         log.debug("发送点赞消息{}", msg);
//         rabbitMqHelper.send(
//                 MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                 routingKey,
//                 msg
//         );
//     }
//
//     @Override
//     public Set<Long> getLikedStatusByBizList(List<Long> ids) {
//         //1.获取用户
//         Long user = UserContext.getUser();
//         //2.查询点赞记录
//         Set<Long> LikedBizIds = lambdaQuery()
//                 .eq(LikedRecord::getUserId, user)
//                 .in(LikedRecord::getBizId, ids)
//                 .list()
//                 .stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//         //3.返回集合
//         return LikedBizIds;
//     }
//
//     private boolean unLiked(Long user, LikeRecordFormDTO dto) {
//         LikedRecord one = this.lambdaQuery()
//                 .eq(LikedRecord::getUserId, user)
//                 .eq(LikedRecord::getBizId, dto.getBizId())
//                 .one();
//         //没有点过赞，取消点赞失败
//         if(one == null){
//             return false;
//         }
//         //点过赞，删除该点赞记录
//         boolean remove = this.removeById(one);
//         return remove;
//     }
//
//     private boolean liked(Long user, LikeRecordFormDTO dto) {
//         LikedRecord one = this.lambdaQuery()
//                 .eq(LikedRecord::getUserId, user)
//                 .eq(LikedRecord::getBizId, dto.getBizId())
//                 .one();
//         //已经点过赞，点赞失败
//         if(one != null){
//             return false;
//         }
//         //未点赞，保存该点赞记录
//         LikedRecord likedRecord = new LikedRecord();
//         likedRecord.setUserId(user);
//         likedRecord.setBizId(dto.getBizId());
//         likedRecord.setBizType(dto.getBizType());
//         boolean save = this.save(likedRecord);
//         return save;
//     }
// }
