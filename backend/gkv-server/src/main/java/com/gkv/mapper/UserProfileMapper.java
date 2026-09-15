package com.gkv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gkv.entity.UserProfile;

/**
 * AI 用户画像 Mapper
 *
 * 标签筛选用不了 BaseMapper 的现成方法，走 UserProfileServiceImpl 里的
 * LambdaQueryWrapper.apply("pref_tags like {0}", pattern) 参数化子串匹配。
 * 列名以数据库为准（开发库里这张表已存在，标签列叫 pref_tags）。
 */
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
