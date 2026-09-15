package com.gkv.service;

import com.alibaba.fastjson.JSON;
import com.gkv.dto.PlanResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 热门行程结果缓存：同一 城市+天数+偏好+预算 的行程 24h 内命中直接秒开。
 * Redis 不可用时自动降级为未命中，不影响正常生成流程。
 */
@Service
@Slf4j
public class TravelPlanCacheService {

    /** 缓存有效期：24 小时 */
    private static final long TTL_SECONDS = 24 * 3600;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 缓存 key：plan:cache:{city}:{days}:{hobby}:{budget} */
    public static String buildKey(String city, int days, java.util.List<String> hobby, String budget) {
        return buildKey(city, days, hobby, budget, null);
    }

    /**
     * 缓存 key（带画像指纹）：plan:cache:{city}:{days}:{hobby}:{budget}:p{指纹}
     *
     * 为什么必须带指纹：加了画像回灌之后，同样的「城市+天数+偏好+预算」在不同画像下
     * 应该产出不同结果。指纹不进 key 的话，第二个用户（或同一用户改了画像之后）
     * 会直接命中别人的缓存 —— 回灌看起来做好了，实际静默失效，这是最隐蔽的一个坑。
     *
     * 无回灌（开关关闭 / 没有画像）时保持原 key 不变，兼容历史缓存。
     */
    public static String buildKey(String city, int days, java.util.List<String> hobby, String budget, String profileFingerprint) {
        java.util.List<String> sorted = hobby == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(hobby);
        java.util.Collections.sort(sorted);
        String base = "plan:cache:" + city + ":" + days + ":" + String.join(",", sorted) + ":" + (budget == null ? "" : budget);
        if (profileFingerprint == null || profileFingerprint.isEmpty()) {
            return base;
        }
        return base + ":p" + profileFingerprint;
    }

    public PlanResponseDTO get(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return JSON.parseObject(json, PlanResponseDTO.class);
        } catch (Exception e) {
            log.warn("[TravelPlanCache] Redis 读取失败，降级为未命中: {}", e.getMessage());
            return null;
        }
    }

    public void put(String key, PlanResponseDTO plan) {
        try {
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(plan), TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[TravelPlanCache] Redis 写入失败，跳过缓存: {}", e.getMessage());
        }
    }
}
