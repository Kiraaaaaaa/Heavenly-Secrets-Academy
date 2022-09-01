package com.tianji.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.IterUtil;
import com.tianji.common.validate.Checker;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 继承自 hutool 的集合工具类
 */
public class CollUtils extends CollectionUtil {

    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }

    public static <T> Set<T> emptySet() {
        return Collections.emptySet();
    }

    public static <K,V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }

    public static <T> Set<T> singletonSet(T t) {
        return Collections.singleton(t);
    }

    public static <T> List<T> singletonList(T t) {
        return Collections.singletonList(t);
    }

    public static List<Integer> convertToInteger(List<String> originList){
        return CollUtils.isNotEmpty(originList) ? originList.stream().map(NumberUtils::parseInt).collect(Collectors.toList()) : null;
    }

    public static List<Long> convertToLong(List<String> originLIst){
        return CollUtils.isNotEmpty(originLIst) ? originLIst.stream().map(NumberUtils::parseLong).collect(Collectors.toList()) : null;
    }

    /**
     * 以 conjunction 为分隔符将集合转换为字符串 如果集合元素为数组、Iterable或Iterator，则递归组合其为字符串
     * @param collection 集合
     * @param conjunction 分隔符
     * @param <T> 集合元素类型
     * @return 连接后的字符串
     * See Also: IterUtil.join(Iterator, CharSequence)
     */
    public static <T> String join(Collection<T> collection, CharSequence conjunction) {
        if (null == collection || collection.isEmpty()) {
            return null;
        }
        return IterUtil.join(collection.iterator(), conjunction);
    }

    public static <T> String joinIgnoreNull(Collection<T> collection, CharSequence conjunction) {
        if (null == collection || collection.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (T t : collection) {
            if(t == null) continue;
            sb.append(t).append(",");
        }
        if(sb.length() <= 0){
            return null;
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    /**
     * 集合校验逻辑
     *
     * @param data 要校验的集合
     * @param checker 校验器
     * @param <T> 集合元素类型
     */
    public static  <T> void  check(List<T> data, Checker<T> checker){
        if(data == null){
            return;
        }
        for (T t : data){
            checker.check(t);
        }
    }

    /**
     * 集合校验逻辑
     *
     * @param data 要校验的集合
     * @param <T> 集合元素类型
     */
    public static  <T extends Checker<T>> void  check(List<T> data){
        if(data == null){
            return;
        }
        for (T t : data){
            t.check();
        }
    }
}