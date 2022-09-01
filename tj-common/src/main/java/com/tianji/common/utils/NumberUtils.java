package com.tianji.common.utils;

import cn.hutool.core.util.NumberUtil;

public class NumberUtils extends NumberUtil {


    /**
     * 如果number为空，将number转换为0，否则原数字返回
     *
     * @param number
     * @return 整型数字，0或原数字
     */
    public static Integer null2Zero(Integer number){
        return number == null ? 0 : number;
    }

    /**
     * 如果number为空，将number转换为0L，否则原数字返回
     *
     * @param number
     * @return 长整型数字，0L或原数字
     */
    public static Long null2Zero(Long number){
        return number == null ? 0L : number;
    }
}
