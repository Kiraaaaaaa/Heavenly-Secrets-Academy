package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author fenny
 * @since 2023-11-29
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    Integer querySeasonByTime(LocalDateTime time);
}
