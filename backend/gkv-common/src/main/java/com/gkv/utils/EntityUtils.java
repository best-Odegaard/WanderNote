
package com.gkv.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.dto.PageDTO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>超级强大的<i>POJO</i>数据实体类转换工具</p>
 * <p>{@link EntityUtils}工具类用于基于Lambda表达式实现类型转换，具有如下优点：</p>
 * <p>1. 实现对象转对象；集合转集合；分页对象转分页对象</p>
 * <p>2. 实体类转Vo、实体类转DTO等都能应用此工具类</p>
 * <p>3. 转换参数均为不可变类型，业务更加安全</p>
 *
 **/
public class EntityUtils {


    /**
     * 将IPaged对象以一种类型转换成另一种类型
     *
     * @param page   源Page
     * @param action 转换规则
     * @param <T>    源实体类
     * @param <R>    目标Page类型泛型
     * @return 变换后的分页类型
     */
    public static <T, R> IPage<R> toPage(IPage<T> page, final Function<? super T, ? extends R> action) {
        Objects.requireNonNull(page);
        Objects.requireNonNull(action);
        return page.convert(action);
    }


    /**
     * 返回空分页结果
     * @param p MybatisPlus的分页结果
     * @param <V> 目标VO类型
     * @param <P> 原始PO类型
     * @return VO的分页对象
     */
    public static <V, P> PageDTO<V> empty(Page<P> p){
        return new PageDTO<>(p.getTotal(), p.getPages(), Collections.emptyList());
    }

    /**
     * 将MybatisPlus分页结果转为 VO分页结果
     * @param p MybatisPlus的分页结果
     * @param voClass 目标VO类型的字节码
     * @param <V> 目标VO类型
     * @param <P> 原始PO类型
     * @return VO的分页对象
     */
    public static <V, P> PageDTO<V> of(Page<P> p, Class<V> voClass) {
        // 1.非空校验//                               声明VO字节码
        List<P> records = p.getRecords();
        if (records == null || records.size() <= 0) {
            // 无数据，返回空结果
            return empty(p);
        }
        // 2.数据转换
        List<V> vos = BeanUtil.copyToList(records, voClass);
        // 3.封装返回
        return new PageDTO<>(p.getTotal(), p.getPages(), vos);
    }

    /**
     * 将MybatisPlus分页结果转为 VO分页结果，允许用户自定义PO到VO的转换方式
     * @param p MybatisPlus的分页结果
     * @param convertor PO到VO的转换函数
     * @param <V> 目标VO类型
     * @param <P> 原始PO类型
     * @return VO的分页对象
     */
    public static <V, P> PageDTO<V> of(Page<P> p, Function<P, V> convertor) {
        // 1.非空校验
        List<P> records = p.getRecords();
        if (records == null || records.size() <= 0) {
            // 无数据，返回空结果
            return empty(p);
        }
        // 2.数据转换
        List<V> vos = records.stream().map(convertor).collect(Collectors.toList());
        // 3.封装返回
        return new PageDTO<>(p.getTotal(), p.getPages(), vos);
    }
}
