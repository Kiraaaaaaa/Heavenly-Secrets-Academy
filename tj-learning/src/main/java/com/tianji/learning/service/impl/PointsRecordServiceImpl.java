package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-11-29
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 增加积分记录，积分类型目前包括：1-课程学习，2-每日签到，3-课程问答， 4-课程笔记，5-课程评价
     * @param userId 用户id
     * @param points 积分值
     * @param type 积分类型
     */
    @Override
    public void addPointsRecord(Long userId, Integer points, PointsRecordType type) {
        //0.校验参数
        if(userId == null || points == null){
            throw new BadRequestException("参数错误");
        }
        PointsRecord record = null;
        //1.查询该类型积分是否有当日上限
        if(type.getMaxPoints() > 0){
            //1.1查询该用户当日此类型积分获取是否上限
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = DateUtils.getDayStartTime(now);
            LocalDateTime endTime = DateUtils.getDayEndTime(now);
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("SUM(points) AS points");
            wrapper.eq("user_id", userId);
            wrapper.eq("type", type.getValue());
            wrapper.between("create_time", startTime, endTime);
            wrapper.groupBy("user_id", "type");
            record = getOne(wrapper);
            if(record != null){
                if(record.getPoints() >= type.getMaxPoints()){
                    throw new BizIllegalException("已达到本人最大获取积分值");
                }
                //如果积分超过上限，则只记录上限值，避免积分累加溢出
                if(record.getPoints() + points > type.getMaxPoints()){
                    record.setPoints(type.getMaxPoints() - record.getPoints().intValue());
                }else{
                    //否则记录默认积分值
                    record.setPoints(points);
                }
            }
        }
        //2.mysql新增积分记录
        if(record == null) record = new PointsRecord();
        record.setPoints(points);
        record.setType(type);
        record.setUserId(userId);
        save(record);

        //3.累加更新redis用户赛季积分
        LocalDate now = LocalDate.now();
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BORAD_KEY_PREFIX + yyyyMM;
        System.out.println(key);
        redisTemplate.boundZSetOps(key).incrementScore(userId.toString(), points);
    }


    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        //1.获取用户
        Long user = UserContext.getUser();
        //2.获取当天开始时间和结束时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = DateUtils.getDayStartTime(now);
        LocalDateTime endTime = DateUtils.getDayEndTime(now);

        //3.查询积分记录
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("user_id, type, SUM(points) AS points");
        wrapper.eq("user_id", user);
        wrapper.between("create_time", startTime, endTime);
        wrapper.groupBy("user_id", "type");
        List<PointsRecord> records = list(wrapper);

        Map<PointsRecordType, PointsRecord> map = null;
        if(CollUtils.isNotEmpty(records)){
            map = records.stream().collect(Collectors.toMap(PointsRecord::getType, c -> c));
        }
        ArrayList<PointsStatisticsVO> list = new ArrayList<>();

        //4.遍历几种枚举值，组装VO
        for (PointsRecordType type : PointsRecordType.values()) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            //5.如果该类型积分有记录，则取出积分值，否则默认0
            if(map!= null && map.containsKey(type)){
                vo.setPoints(map.get(type).getPoints());
            }else{
                vo.setPoints(0);
            }
            vo.setType(type.getDesc());
            vo.setMaxPoints(type.getMaxPoints());
            list.add(vo);
        }
        return list;
    }
}
