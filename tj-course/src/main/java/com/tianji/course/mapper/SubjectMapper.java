package com.tianji.course.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.course.domain.dto.IdAndNumDTO;
import com.tianji.course.domain.po.Subject;
import com.tianji.course.domain.po.SubjectCategory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 题目 Mapper 接口
 * </p>
 *
 * @author wusongsong
 * @since 2022-07-15
 */
public interface SubjectMapper extends BaseMapper<Subject> {

    String COLUMNS = "s.id,s.name,s.subject_type,s.use_times,s.answer_times,s.difficulty,s.option1,s.option2,s.option3,s.option4,s.option5,s.option6,s.option7,s.option8,s.option9,s.option10,s.answer,s.analysis,s.score,s.dep_id,s.create_time,s.update_time,s.creater,s.updater,s.deleted";

    @Select("<script>select id,subject_id as subjectId,first_cate_id as firstCateId," +
            "second_cate_id as secondCateId,third_cate_id as thirdCateId from subject_category" +
            " where subject_id in (" +
            "<foreach collection='ids' item='id' separator=','>#{id}</foreach>" +
            ")</script>")
    List<SubjectCategory> listSubjectCategoryById(@Param("ids") List<Long> ids);

    @Select("select distinct " + COLUMNS + " from subject s left join subject_category sc " +
            "on s.id=sc.subject_id ${ew.customSqlSegment}")
    Page<Subject> listForPage(Page<Subject> page, @Param("ew") Wrapper<Subject> subjectQueryWrapper);

    /**
     * 批量查询老师所负责的课程数量
     * @param teacherIds
     * @return
     */
    @Select("<script>SELECT ct.teacher_id,count(*) " +
            " from course c LEFT JOIN course_teacher ct on c.id=ct.course_id " +
            "where c.status!=1 and c.deleted=0 and ct.teacher_id in (<foreach collection='teacherIds' " +
            "item='teacherId' separator=','>#{teacherId}</foreach>)" +
            " GROUP BY ct.teacher_id</script>")
    List<IdAndNumDTO> countCourseNumOfTeacher(@Param("teahcerIds")List<Long> teacherIds);

}
