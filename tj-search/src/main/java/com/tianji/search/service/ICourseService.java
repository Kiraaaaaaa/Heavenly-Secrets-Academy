package com.tianji.search.service;

import com.tianji.search.enums.CourseStatus;

import java.util.List;

public interface ICourseService {

    void handleNewCourse(Long courseId);

    void handleCourseDelete(Long courseId);

    void handleCourseStatus(Long courseId, CourseStatus expired);

    void handleCourseUp(Long courseId);

    void updateCourseSold(List<Long> courseId, int amount);
}
