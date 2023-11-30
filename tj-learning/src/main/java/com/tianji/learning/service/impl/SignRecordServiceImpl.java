package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignRecordServiceImpl implements ISignRecordService {
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    @Override
    public SignResultVO addSignRecords() {
        //1.获取登录用户
        Long user = UserContext.getUser();

        //2.签到
        //2.1获取当前日期作为key
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + user + format;
        //2.2使用bitmap尝试进行签到
        //得到今日在bitmap的坐标(偏移量)
        int offset = now.getDayOfMonth() - 1;
        Boolean signSuccess = redisTemplate.opsForValue().setBit(key, offset, true);
        log.info("用户{}(id)于{}签到[{}]", user, format, signSuccess?"失败":"成功");
        if(signSuccess){
            throw new BizIllegalException("签到失败，当日已签到");
        }

        //3.获取连续签到天数
        int signDays = getSignedDays(now.getDayOfMonth(), key);
        //4.计算当日连续签到奖励(每七天叠加一次积分，每次10分)
        int signReward = signDays / 7 * 10;
        //5.签到结果
        SignResultVO signResultVO = SignResultVO.builder()
                .signDays(signDays)
                .signPoints(signReward)
                .build();
        // 6.保存积分明细记录，发送MQ
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(user, signReward + 1));// 签到积分是奖励积分+基本得分
        return signResultVO;
    }

    @Override
    public Deque<Integer> querySignRecords() {
        //1.获取登录用户
        Long user = UserContext.getUser();
        //2.获取日期key
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + user + format;
        //3.获取本月签到详情
        Deque<Integer> list = getDaysOfMonth(now.getDayOfMonth(), key);
        return list;
    }

    /**
     * 获取本月签到详情
     * @param dayOfMonth 非偏移量，为当日到月初的总天数
     * @param key 用户&时间键，格式sign:uid:2:202311
     * @return 本月签到详情List，为当日到月初的总天数，非月总数
     */
    private Deque<Integer> getDaysOfMonth(int dayOfMonth, String key) {
        //1.获取bitMap记录值
        List<Long> list = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        Long bitMapNum = list.get(0);
        log.info("用户本月BitMap签到记录：{}", bitMapNum);

        //2.Long值转List<Integer>
        //2.1转换为二进制字符串(toBinaryString会忽略前面的0)
        String s = Long.toBinaryString(bitMapNum);
        //2.2List添加数据
        Deque<Integer> arr = new LinkedList<>();
        for (char c : s.toCharArray()) {
            arr.addLast(c == '1'?1:0);
        }
        //2.3如果转换后的长度小于当前天数，说明前面是零，补0即可
        for(int i=0; i<dayOfMonth - s.length(); i++){
            arr.addFirst(0);
        }
        return arr;
    }

    /**
     * 获取使用者的目前连续签到天数
     * @param dayOfMonth 非偏移量，为当日到月初的总天数
     * @param key 用户&时间键，格式sign:uid:2:202311
     * @return countOfDay 用户连续签到天数
     */
    private int getSignedDays(int dayOfMonth, String key) {
        //获取bitMap记录值
        List<Long> list = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        Long bitMapNum = list.get(0);
        log.info("用户本月BitMap签到记录：{}", bitMapNum);
        int countOfDay = 0;
        //只要当天值等于1就是签过到
        while((bitMapNum & 1) == 1){
            //已经签过到，连续天数+1
            countOfDay++;
            //右移一位，准备判断昨天是否签到
            bitMapNum = bitMapNum >>> 1;
        }
        return countOfDay;
    }
}
