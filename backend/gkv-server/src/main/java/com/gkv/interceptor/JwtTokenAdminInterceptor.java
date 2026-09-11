package com.gkv.interceptor;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.JwtClaimsConstant;
import com.gkv.constant.StatusConstant;
import com.gkv.context.AdminContext;
import com.gkv.context.BaseContext;
import com.gkv.entity.Admin;
import com.gkv.entity.SysRole;
import com.gkv.mapper.AdminMapper;
import com.gkv.mapper.SysRoleMapper;
import com.gkv.properties.JwtProperties;
import com.gkv.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * jwt令牌校验的拦截器（管理端）
 * 1、校验管理端 token 是否合法
 * 2、加载管理员与角色权限，写入 AdminContext
 * 3、校验方法上的 @RequirePerm 权限点
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //2、校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long adminId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            log.info("当前管理员id：{}", adminId);
            //取操作人id
            BaseContext.setCurrentId(adminId);

            //3、加载管理员与角色权限
            Admin admin = adminMapper.selectById(adminId);
            if (admin == null || admin.getStatus() != StatusConstant.ENABLE) {
                log.warn("管理员不存在或已被禁用：{}", adminId);
                response.setStatus(401);
                return false;
            }
            AdminContext.setPerms(loadPerms(admin));

            //4、校验方法上的 @RequirePerm 权限点
            RequirePerm requirePerm = ((HandlerMethod) handler).getMethodAnnotation(RequirePerm.class);
            if (requirePerm != null && !AdminContext.hasPerm(requirePerm.value())) {
                log.warn("管理员{}无权限：{}", adminId, requirePerm.value());
                response.setStatus(403);
                return false;
            }

            //5、通过，放行
            return true;
        } catch (Exception ex) {
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理线程上下文，避免线程复用串数据
        AdminContext.remove();
        BaseContext.removeCurrentId();
    }

    /** 解析管理员角色权限点列表 */
    private List<String> loadPerms(Admin admin) {
        if (admin.getRoleId() == null) {
            return Collections.emptyList();
        }
        SysRole role = sysRoleMapper.selectById(admin.getRoleId());
        if (role == null || !StringUtils.hasText(role.getPerms())) {
            return Collections.emptyList();
        }
        return Arrays.stream(role.getPerms().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
