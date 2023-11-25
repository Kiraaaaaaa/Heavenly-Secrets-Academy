package com.tinaji;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.service.IInteractionQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.function.Predicate;

@SpringBootTest(classes = LearningApplication.class)
public class MPSelectFiedTest {
    @Autowired
    IInteractionQuestionService questionService;

    @Test
    public void test(){
        List<InteractionQuestion> list = questionService.lambdaQuery()
                .select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
                    @Override
                    public boolean test(TableFieldInfo tableFieldInfo) {
                        return !tableFieldInfo.getProperty().equals("description");
                    }
                })
                .eq(InteractionQuestion::getCourseId, "1549025085494521857")
                .eq(InteractionQuestion::getUserId, 2)
                .eq(InteractionQuestion::getHidden, Boolean.FALSE)
                .eq(InteractionQuestion::getSectionId, "1550383240983875586")
                .list();
        System.out.println(list);
    }
}
