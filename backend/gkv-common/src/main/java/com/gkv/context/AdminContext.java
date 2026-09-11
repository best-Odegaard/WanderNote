package com.gkv.context;

import java.util.List;

/**
 * 管理端上下文：存放当前线程管理员拥有的权限点列表
 * 由 JwtTokenAdminInterceptor 在 JWT 校验通过后写入
 */
public class AdminContext {

    private static ThreadLocal<List<String>> threadLocal = new ThreadLocal<>();

    public static void setPerms(List<String> perms) {
        threadLocal.set(perms);
    }

    public static List<String> getPerms() {
        return threadLocal.get();
    }

    /** 当前管理员是否拥有指定权限点 */
    public static boolean hasPerm(String perm) {
        List<String> perms = threadLocal.get();
        return perms != null && perms.contains(perm);
    }

    public static void remove() {
        threadLocal.remove();
    }
}
