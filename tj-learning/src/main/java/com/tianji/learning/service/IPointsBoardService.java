package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import javax.validation.constraints.Min;
import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author fenny
 * @since 2023-11-29
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query);

    List<PointsBoard> queryCurrentPoints(@Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize, boolean toDB);


    void createPointsBoardTableBySeason(Integer season);
}
