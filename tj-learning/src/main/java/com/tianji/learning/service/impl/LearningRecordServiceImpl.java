package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author fenny
 * @since 2023-11-21
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取当前用户
        Long user = UserContext.getUser();
        //2.查询lesson信息
        LearningLesson learningLesson = lessonService.queryByUserAndCourseId(user, courseId);
        if(learningLesson == null){
            throw new BizIllegalException("该用户未拥有该课程");
        }
        //3.查询record表的小节学习进度
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, learningLesson.getId())
                .list();
        if(CollUtils.isEmpty(records)){
            return null;
        }

        //4.设置LearningLessonDTO的信息
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(learningLesson.getId());
        learningLessonDTO.setLatestSectionId(learningLesson.getLatestSectionId());

        //5.复制小节记录集合到LearningRecordDTO
        List<LearningRecordDTO> dtos = BeanUtils.copyList(records, LearningRecordDTO.class);
        learningLessonDTO.setRecords(dtos);

        return learningLessonDTO;
    }


    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        Long user = UserContext.getUser();
        SectionType type = recordDTO.getSectionType();
        //判断是否已经考完试或者小节已经全部学完
        boolean finished = false;
        if(type == SectionType.EXAM){
            //考试流程
            finished = handleExamRecord(user, recordDTO);
        }else{
            //看视频流程
            finished = handleVideoRecord(user, recordDTO);
        }
        //更新课表
        handleLearningLessonsChanges(recordDTO, finished);
    }
    private void handleLearningLessonsChanges(LearningRecordFormDTO recordDTO, boolean finished) {
        //1.查询课表
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if(lesson == null){
            throw new BizIllegalException("当前课表不存在");
        }
        //2.查询当前完成小节是否已经达到全部小节数量
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        System.out.println(cinfo);
        System.out.println(lesson);
        boolean allLearned = lesson.getLearnedSections() + 1 >= cinfo.getSectionNum();
        System.out.println(allLearned);
        //3.更新课表
        boolean update = lessonService.lambdaUpdate()
                //由于购买课程后就会初始化lesson，且初始化已学小节为0，所以如果是第一次观看视频，则设置lesson为正在学习
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING)
                //如果小节都学完了就更新lesson为完成状态
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED)
                //没完成小节才更新最后学习时间，不过好像更新了也没啥问题，不过多了一步不必要的操作
                .set(!finished, LearningLesson::getLatestLearnTime, recordDTO.getCommitTime())
                //没完成小节才更新最后学习章节
                .set(!finished, LearningLesson::getLatestSectionId, recordDTO.getSectionId())
                //如果完成了小节则课表完成小节数量+1
                .set(finished, LearningLesson::getLearnedSections, lesson.getLearnedSections()+1)
                // .setSql(finished, "learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
        if(!update){
            throw new DbException("课表更新失败");
        }

    }
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDTO) {
        LearningRecord one = lambdaQuery()
                .eq(LearningRecord::getLessonId, recordDTO.getLessonId())
                .eq(LearningRecord::getSectionId, recordDTO.getSectionId())
                .eq(LearningRecord::getUserId, userId)
                .one();
        //1.没有旧的学习记录
        if(one == null){
            LearningRecord learningRecord = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            learningRecord.setUserId(userId);
            boolean save = save(learningRecord);
            if(!save){
                throw new DbException("新增学习记录失败");
            }
            return false;
        }
        //2.有学习记录，修改学习记录
        //2.1如果该小节视频长度完成50%及以上且小节旧的状态为未完成，才让当前课程表的小节完成数量+1
        boolean finished = false;
        if(!one.getFinished() && recordDTO.getDuration() <= recordDTO.getMoment()*2){
            finished = true;
        }
        //2.2更新小节学习，如果符合完成条件，则更新小节记录的完成时间和状态
        boolean update = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, one.getId())
                .update();
        if(!update){
            throw new DbException("更新学习记录失败");
        }
        return finished;
    }
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDTO) {
        LearningRecord learningRecord = new LearningRecord();
        learningRecord.setFinished(true);
        learningRecord.setFinishTime(recordDTO.getCommitTime());
        learningRecord.setSectionId(recordDTO.getSectionId());
        learningRecord.setLessonId(recordDTO.getLessonId());
        learningRecord.setUserId(userId);
        boolean save = save(learningRecord);
        if(!save){
            throw new DbException("新增考试记录失败");
        }
        return true;
    }
}
