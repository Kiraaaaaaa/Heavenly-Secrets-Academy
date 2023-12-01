package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-11-29
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        //1.获取当前用户
        Long user = UserContext.getUser();
        //2.是否为历史赛季查询
        Long seasonId = query.getSeason();
        boolean isCurrent = seasonId == null || seasonId == 0;
        //3.查询本人的赛季排名和分数
        PointsBoard pointsBoard = !isCurrent ? queryMyHistoryPoints(seasonId, user) : queryMyCurrentPoints(user);

        List<PointsBoard> pointsBoards = null;
        if(!isCurrent){
            //4.分页查询历史赛季排行榜，fromDB
            pointsBoards = queryHistoryPoints(seasonId, query.getPageNo(), query.getPageSize());
        }else{
            //5.分页查询本赛季排行榜，fromRedis
            pointsBoards = queryCurrentPoints(query.getPageNo(), query.getPageSize());
        }
        //6.封装List
        //6.1查询赛季排行榜成员名称
        List<Long> uids = pointsBoards.stream().map(PointsBoard::getUserId).collect(Collectors.toList());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        Map<Long, String> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        //6.2将复制pointsBoard到PointsBoardItemVO
        List<PointsBoardItemVO> vos = pointsBoards.stream().map(i -> {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            //设置远程查询名称
            Long userId = i.getUserId();
            if (userMap != null && userMap.get(userId) != null) {
                itemVO.setName(userMap.get(userId));
            }
            itemVO.setPoints(i.getPoints());
            itemVO.setRank(i.getRank());
            return itemVO;
        }).collect(Collectors.toList());

        //6.封装vo
        PointsBoardVO vo = new PointsBoardVO();
        //个人分数
        vo.setPoints(pointsBoard.getPoints());
        //个人排名
        vo.setRank(pointsBoard.getRank());
        vo.setBoardList(vos);
        return vo;
    }

    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        // getBaseMapper().createPointsBoardTable(LearningConstants.POINTS_BOARD_TABLE_PREFIX + season);
    }

    /**
     * 分页查询历史赛季排行榜，fromDB
     * @param seasonId 赛季id
     * @param pageNo 页码值
     * @param pageSize 页码大小
     * @return 用户积分和排名信息列表
     */
    private List<PointsBoard> queryHistoryPoints(Long seasonId, @Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        if(seasonId == null){
            throw new BadRequestException("查询历史赛季排行榜失败，赛季id不能为空");
        }
        PointsBoard pointsBoard = new PointsBoard();
        lambdaQuery()
                .eq(PointsBoard::getSeason, seasonId)
                .list();
        return  null;
    }

    /**
     * 分页查询当前赛季排行榜，fromRedis
     * @param pageNo 页码数
     * @param pageSize 页码大小
     * @return 用户积分和排名信息列表
     */
    private List<PointsBoard> queryCurrentPoints(@Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        //1.获取本月时间
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //2.构建key
        String key = RedisConstants.POINTS_BORAD_KEY_PREFIX + format;
        //3.计算分页start和end值
        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize - 1;
        //4.获取分页数据
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.boundZSetOps(key).reverseRangeWithScores(start, end);
        if(tuples == null) return CollUtils.emptyList();
        //5.封装list成员
        int rank = start + 1;
        List<PointsBoard> list = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Double score = tuple.getScore();
            String value = tuple.getValue();
            if(score == null || value == null){
                rank++;
                continue;
            }
            PointsBoard pointsBoard = new PointsBoard();
            pointsBoard.setPoints(score.intValue());
            pointsBoard.setUserId(Long.valueOf(value));
            pointsBoard.setRank(rank++);
            list.add(pointsBoard);
        }
        return list;
    }

    /**
     * 查询用户历史该赛季积分和排名，fromDB
     * @param seasonId 赛季id
     * @param userId 用户id
     * @return 用户积分和排名信息
     */
    private PointsBoard queryMyHistoryPoints(Long seasonId, Long userId) {
        if(seasonId == null || userId == null){
            throw  new BadRequestException("错误参数");
        }
        PointsBoard one = lambdaQuery()
                .eq(PointsBoard::getSeason, seasonId)
                .eq(PointsBoard::getUserId, userId)
                .one();
        return one;
    }

    /**
     * 查询用户本赛季当前积分和排名，fromRedis
     * @param userId 用户id
     * @return 用户积分和排名信息
     */
    private PointsBoard queryMyCurrentPoints(Long userId) {
        //获取本月时间
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //构建key
        String key = RedisConstants.POINTS_BORAD_KEY_PREFIX + format;
        //查询分数
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        Double score = ops.score(userId.toString());
        //查询排名 注意排名是从0开始递增
        Long rank = redisTemplate.boundZSetOps(key).reverseRank(userId.toString());

        //封装个人信息
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setPoints(score == null ? 0 : score.intValue());
        pointsBoard.setRank(rank == null ? 0 : rank.intValue() + 1);
        pointsBoard.setId(userId);
        return pointsBoard;
    }
}
