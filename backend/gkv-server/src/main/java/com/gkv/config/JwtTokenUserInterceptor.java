
package com.gkv.config;

import com.gkv.constant.JwtClaimsConstant;
import com.gkv.context.BaseContext;
import com.gkv.properties.JwtProperties;
import com.gkv.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 从请求头中获取token
        String token = request.getHeader(jwtProperties.getUserTokenName());
        if (token == null || token.length() == 0) {
            token = request.getHeader("Authorization");
        }
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id：{}", userId);
            // 设置当前用户ID到BaseContext
            BaseContext.setCurrentId(userId);
            // 校验通过，放行
            return true;
        } catch (Exception ex) {
            // 校验失败，返回401状态码
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 请求结束后清理线程上下文。
     *
     * 之前只有管理端拦截器做了清理，用户端没有 —— Tomcat 会复用工作线程，
     * 任何「不经过本拦截器」的请求（匿名白名单里的 /home/**、/travel/plan/status/** 等）
     * 落到一个刚跑过登录用户请求的线程上时，BaseContext 里还留着上一个请求的 userId，
     * AutoFillAspect / HomeController 读到的就是别人的身份，属于会串数据的隐患。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
