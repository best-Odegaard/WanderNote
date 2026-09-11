package com.gkv.aspect;

import com.gkv.annotation.AutoFill;
import com.gkv.constant.AutoFillConstant;
import com.gkv.context.BaseContext;
import com.gkv.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点
     */
    @Pointcut("execution(* com.gkv.mapper.*.*(..)) && @annotation(com.gkv.annotation.AutoFill)")
    public void autoFIllPointCut(){

    }

    /**
     * 前置通知：在通知中进行公共字段的赋值
     */
    @Before("autoFIllPointCut()")
    public void autoFill(JoinPoint joinPoint){

        log.info("开始进行公共字段自动填充...");

        //获取当前被拦截的方法上的数据库操作类型
        MethodSignature signature =(MethodSignature) joinPoint.getSignature(); //方法签名对象
        AutoFill autoFill =signature.getMethod().getAnnotation(AutoFill.class);//获取方法上的注解对象
        OperationType operationType =autoFill.value(); //获取数据库操作类型

        //获取到前面被拦截的方法的参数---实体对象
        Object[] args=joinPoint.getArgs(); //获得所有参数
        if(args==null || args.length ==0){ //防止空指针
            return;
        }

        Object entity =args[0];

        //准备赋值的数据
        LocalDateTime now =LocalDateTime.now();
        Long currentId= BaseContext.getCurrentId();

        //根据当前不同的操作类型，为对应的属性通过反射来赋值
        if(operationType == OperationType.INSERT){ //对insert 语句
            //为4个公共字段赋值
            try{ //获取实体类的set方法
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME,LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER,Long.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);

                //通过反射为目标对象属性赋值
                setCreateTime.invoke(entity,now);
                setCreateUser.invoke(entity,currentId);
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);


            } catch (Exception e){
                e.printStackTrace();
            }
        }else if(operationType ==OperationType.UPDATE){//修改
            //为2个公共字段赋值
            try{
                Method setUpdateTime =entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser =entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                //通过反射为目标对象赋值
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);


            }catch (Exception e){
                e.printStackTrace();
            }
        }

    }
}
