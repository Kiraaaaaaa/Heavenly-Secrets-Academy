package com.tianji.course.constants;

/**
 * @ClassName CourseConstants
 * @Author wusongsong
 * @Date 2022/7/14 13:44
 * @Version
 **/
public class CourseConstants {

    public static final long CATEGORY_ROOT = 0;

    public class CourseStep {
        public static final int BASE_INFO = 1; //基本信息
        public static final int CATALOGUE = 2; //目录
        public static final int MEDIA = 3; //视频
        public static final int SUBJECT = 4; //题目
        public static final int TEACHER = 5; //老师
    }

    //目录类型
    public class CataType{
        public static final int CHAPTER = 1; //章
        public static final int SECTION = 2; //节
        public static final int PRATICE = 3; //练习或测试
    }

}
