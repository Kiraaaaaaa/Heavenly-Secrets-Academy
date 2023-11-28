package com.tinaji;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(classes = LearningApplication.class)
public class mainTest {
    @Autowired
    IInteractionReplyService replyService;
    @Autowired
    CourseClient courseClient;
    @Autowired
    UserClient userClient;
    @Autowired
    IInteractionQuestionService questionService;
    @Autowired
    CategoryCache categoryCache;
    @Autowired
    CatalogueClient catalogueClient;
    @Test
    public void testx(){
        Long id = 1729192645861388290L;
        replyService.lambdaUpdate()
                .eq(InteractionReply::getId, id)
                .or()
                .eq(InteractionReply::getAnswerId, id)
                .set(InteractionReply::getHidden, false)
                .update();
    }

    /**
     * 测试获取课程教师姓名
     */
    @Test
    public void testTeacherGet(){
        CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(1L, false, true);
        List<UserDTO> userDTOS = userClient.queryUserByIds(courseInfoById.getTeacherIds());
        System.out.println(courseInfoById);
    }

    @Test
    public void testQuestionAdmin(){
        //1.查出问题集合
        InteractionQuestion question = questionService.lambdaQuery()
                .eq(InteractionQuestion::getId, 1729192571144056834L)
                .one();
        //查询用户ids和章、节ids
        ArrayList<Long> uIds = new ArrayList<>();
        ArrayList<Long> chapterAndSectionIds = new ArrayList<>();
        uIds.add(question.getUserId());
        chapterAndSectionIds.add(question.getSectionId());
        chapterAndSectionIds.add(question.getChapterId());

        //2.远程查询课程信息
        //2.1远程调用搜索服务查询课程ids
        CourseFullInfoDTO course = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if(course == null){
            throw new BizIllegalException("该问题的课程不存在");
        }
        //3.远程批量查询章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if(CollUtils.isEmpty(catas)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        //4.远程批量查询用户信息
        //老师id传入
        uIds.addAll(course.getTeacherIds());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户集合不存在");
        }
        Map<Long, String> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        //6.封装vo
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //6.1设置课程信息
        vo.setCourseName(course.getName());
        //6.2设置用户信息
        if(userMap != null){
            vo.setUserName(userMap.get(question.getUserId()));
            vo.setTeacherName(userMap.get(course.getTeacherIds().get(0)));
        }
        //6.3设置章节信息
        if(cataMap != null){
            vo.setSectionName(cataMap.get(question.getSectionId()));
            vo.setChapterName(cataMap.get(question.getChapterId()));
        }
        //6.4设置分类信息(使用自定义的缓存工具类获取分类缓存信息)
        List<Long> categoryIds = course.getCategoryIds();
        String categoryNames = categoryCache.getCategoryNames(categoryIds);
        vo.setCategoryName(categoryNames);
        System.out.println(vo);
    }
}
