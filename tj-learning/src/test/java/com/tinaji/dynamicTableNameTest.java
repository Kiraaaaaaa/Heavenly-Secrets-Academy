package com.tinaji;

import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = LearningApplication.class)
public class dynamicTableNameTest {
    @Autowired
    IPointsBoardService pointsBoardService;

    /**
     * 测试动态表名拦截器是否生效
     */
    @Test
    public void test() {
        //threadlocal保存动态表名
        TableInfoContext.setInfo("points_board_11");
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setId(1L);
        pointsBoard.setPoints(1);
        pointsBoard.setUserId(1L);
        //动态表名拦截器应该会拦截到这个操作，并将表名改为points_board_11
        pointsBoardService.save(pointsBoard);
    }
}
