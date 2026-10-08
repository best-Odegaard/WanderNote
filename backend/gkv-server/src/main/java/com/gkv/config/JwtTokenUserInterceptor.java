package com.gkv.config;

import com.gkv.constant.JwtClaimsConstant;
import com.gkv.context.BaseContext;
import com.gkv.properties.JwtProperties;
import com.gkv.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 允许**匿名读取**的内容接口（只对 GET/HEAD 生效）。
     *
     * 为什么需要它：
     *   内容页（探索 / 社区 / 景点 / 活动）在未登录时也必须能浏览。
     *   之前这些读接口全在登录态之后，未登录一进「探索」tab 就拿到 401，
     *   前端 utils/request.ts 的 401 处理会弹「请先登录」并把用户踢去登录页 ——
     *   与「首页可以匿名打开」自相矛盾，也把内容入口整块挡在登录墙后面。
     *
     * 为什么按「方法 + 路径」而不是只加 excludePathPatterns：
     *   同一个前缀下既有公开读、又有必须登录的写与「我的」数据：
     *     GET  /journal/list        公开
     *     POST /journal/publish     必须登录（要用 userId 落库）
     *     GET  /journal/my/list     必须登录（返回本人的游记）
     *   只把前缀加进 excludePathPatterns 会把这些写接口和「我的」一起放成匿名。
     *   所以在拦截器内部判断方法：写接口与 /journal/my/** 一律照旧拦截。
     *
     * 维护约定：新增内容读接口时同步加进这里，并确认对应 service 在 userId 为空时
     *   不会抛异常（ScenicServiceImpl / ActivityServiceImpl 已有 `userId == null` 分支，
     *   TravelJournalServiceImpl 的 list/hot/detail 不使用 userId）。
     */
    private static final List<String> ANONYMOUS_READ_PATTERNS = Arrays.asList(
            "/featured/list", "/featured/*",
            "/journal/list", "/journal/hot", "/journal/*", "/journal/comment/list/*",
            "/scenic/list", "/scenic/hot", "/scenic/search", "/scenic/*",
            "/activity/list", "/activity/hot", "/activity/*"
    );

    /**
     * 匿名白名单里的例外：/journal/* 会匹配到 /journal/my，本人数据必须登录。
     * 命中这里一律走正常鉴权流程。
     */
    private static final List<String> ANONYMOUS_READ_DENY = Arrays.asList(
            "/journal/my", "/journal/my/**"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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

        boolean anonymousRead = isAnonymousRead(request);

        // 没有令牌：内容读接口放行（不设 userId，service 走「无个性化」分支），其余 401
        if (token == null || token.length() == 0) {
            if (anonymousRead) {
                return true;
            }
            response.setStatus(401);
            return false;
        }

        // 校验令牌
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            // 设置当前用户ID到BaseContext
            BaseContext.setCurrentId(userId);
            // 校验通过，放行
            return true;
        } catch (Exception ex) {
            // 令牌过期 / 被篡改：
            //   · 内容读接口照常匿名放行 —— 否则带着过期令牌的用户一进探索就被弹登录，
            //     而且会反复跳（前端 401 处理每次都跳一次）；
            //   · 其余接口维持 401，交给前端清令牌 + 跳登录处理。
            if (anonymousRead) {
                log.warn("游客读取内容接口，令牌无效已按匿名处理：{} {}", request.getMethod(), request.getRequestURI());
                return true;
            }
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 这次请求是不是「内容读接口」。
     *
     * 只认 GET/HEAD：写操作（发布游记、点赞、收藏、报名、导入行程…）必须登录，
     * 与路径前缀里是否公开无关。
     */
    private boolean isAnonymousRead(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        for (String deny : ANONYMOUS_READ_DENY) {
            if (PATH_MATCHER.match(deny, path)) {
                return false;
            }
        }
        for (String pattern : ANONYMOUS_READ_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 请求结束后清理线程上下文。
     *
     * 之前只有管理端拦截器做了清理，用户端没有 —— Tomcat 会复用工作线程，
     * 任何「不经过本拦截器」的请求（匿名白名单里的 /home/**、/travel/plan/status/** 等）
     * 落到一个刚跑过登录用户请求的线程上时，BaseContext 里还留着上一个请求的 userId，
     * AutoFillAspect / HomeController 读到的就是别人的身份，属于会串数据的隐患。
     *
     * 匿名读接口同样要走这里：它可能压根没设过 userId，
     * 但线程复用下仍要先清一遍再交还。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
