package com.tianji.common.utils;

import cn.hutool.core.util.NumberUtil;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class NumberUtils extends NumberUtil {


    /**
     * 如果number为空，将number转换为0，否则原数字返回
     *
     * @return 整型数字，0或原数字
     */
    public static Integer null2Zero(Integer number){
        return number == null ? 0 : number;
    }

    /**
     * 如果number为空，将number转换为0L，否则原数字返回
     *
     * @return 长整型数字，0L或原数字
     */
    public static Long null2Zero(Long number){
        return number == null ? 0L : number;
    }

    public static String parseCentToYuan(Integer amount){
        if (amount == null) {
            return "0";
        }
        return new BigDecimal(amount)
                .divide(new BigDecimal(100), new MathContext(2, RoundingMode.HALF_UP))
                .toString();
    }
    public static String parseDiscountRate(Integer amount){
        if (amount == null) {
            return "";
        }
        return new BigDecimal(amount)
                .divide(new BigDecimal(10), new MathContext(1, RoundingMode.HALF_UP))
                .toString();
    }
}
