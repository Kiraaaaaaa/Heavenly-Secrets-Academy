package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.contants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * 基于Redis实现点赞记录的缓存
 * </p>
 *
 * @author fenny
 * @since 2023-11-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        //1.获取登录用户
        Long user = UserContext.getUser();
        //2.判断点赞类型并存储到Redis
       boolean flag = dto.getLiked() ? liked(user, dto) : unLiked(user, dto);
        //点赞失败
        if(!flag){
            return;
        }
        //3.在Redis点赞记录表中统计业务id的点赞数(统计人数)
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikedNum = redisTemplate.boundSetOps(key).size();
        if(totalLikedNum == null){
            return;
        }
        //4.把业务点赞数存入Redis点赞统计表(zset)
        String bizTypeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + dto.getBizType();
        redisTemplate.boundZSetOps(bizTypeKey).add(dto.getBizId().toString(), totalLikedNum);
    }

    @Override
    public Set<Long> getLikedStatusByBizList(List<Long> ids) {
        if(CollUtils.isEmpty(ids)){
            return new HashSet<>();
        }
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态，使用pipelined批量查询(适合短时间内大量数据)
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : ids) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(ids::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1.查询redis中30个业务id
        String typeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.boundZSetOps(typeKey).popMin(maxBizSize);
        if(CollUtils.isEmpty(typedTuples)){
            return;
        }
        ArrayList<LikedTimesDTO> list = new ArrayList<>(typedTuples.size());
        //2.将查询的业务id封装到list中
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Double score = typedTuple.getScore();
            String value = typedTuple.getValue();
            //如果有错误数据就跳过
            if(score == null || StringUtils.isBlank(value)){
                continue;
            }
            //封装redis数据为待发送的MQ消息数据
            LikedTimesDTO msg = LikedTimesDTO.of(Long.valueOf(value), score.intValue());
            list.add(msg);
        }
        //3.发送到MQ
        log.debug("准备批量发送点赞统计消息:{}", list);
        rabbitMqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType),
                list);
    }

    private boolean unLiked(Long user, LikeRecordFormDTO dto) {
        //Redis中移除点赞
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.boundSetOps(key).remove(user.toString());
        //如果result大于0，说明该点赞已经被移除
        return result != null && result > 0;
    }

    private boolean liked(Long user, LikeRecordFormDTO dto) {
        //判断该业务id是否已经点过赞，没有则添加点赞
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.boundSetOps(key).add(user.toString());
        //如果result大于0，说明该业务id已经点过赞
        return result != null && result > 0;
    }
}
