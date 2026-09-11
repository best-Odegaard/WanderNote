package com.gkv.constant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端权限点常量（key=权限点，value=展示名）
 * 新增权限点时在此登记，前端菜单与角色编辑页会展示
 */
public class PermConstant {

    /** 数据看板 */
    public static final String DASHBOARD_VIEW = "dashboard:view";
    /** 反馈查看 */
    public static final String FEEDBACK_VIEW = "feedback:view";
    /** 反馈处理（回复/关闭/删除） */
    public static final String FEEDBACK_HANDLE = "feedback:handle";
    /** 用户查看 */
    public static final String USER_VIEW = "user:view";
    /** 用户管理（禁用/启用） */
    public static final String USER_MANAGE = "user:manage";
    /** 游记查看 */
    public static final String JOURNAL_VIEW = "journal:view";
    /** 游记管理（删除/下架/评论删除） */
    public static final String JOURNAL_MANAGE = "journal:manage";
    /** 内容管理（景点/活动增删改） */
    public static final String CONTENT_MANAGE = "content:manage";
    /** 首页管理（Banner/城市） */
    public static final String HOME_MANAGE = "home:manage";
    /** 系统管理（管理员/角色） */
    public static final String ADMIN_MANAGE = "admin:manage";

    /** 有序权限点列表：key=权限点，value=展示名 */
    public static final Map<String, String> PERMS = new LinkedHashMap<>();

    static {
        PERMS.put(DASHBOARD_VIEW, "数据看板");
        PERMS.put(FEEDBACK_VIEW, "意见反馈查看");
        PERMS.put(FEEDBACK_HANDLE, "意见反馈处理");
        PERMS.put(USER_VIEW, "用户查看");
        PERMS.put(USER_MANAGE, "用户管理");
        PERMS.put(JOURNAL_VIEW, "游记查看");
        PERMS.put(JOURNAL_MANAGE, "游记管理");
        PERMS.put(CONTENT_MANAGE, "内容管理");
        PERMS.put(HOME_MANAGE, "首页管理");
        PERMS.put(ADMIN_MANAGE, "系统管理");
    }

    /** 全部权限点 key 列表 */
    public static List<String> keys() {
        return new ArrayList<>(PERMS.keySet());
    }

    /** 权限点 key+name 列表（供角色编辑页展示） */
    public static List<Map<String, String>> list() {
        List<Map<String, String>> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : PERMS.entrySet()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("name", entry.getValue());
            list.add(item);
        }
        return list;
    }
}
