package com.gkv.service;

import com.gkv.vo.HomeVO;

public interface HomeService {

    /**
     * 获取首页聚合数据
     * @param userId 当前登录用户 ID，可为 null（未登录）
     */
    HomeVO getHomeIndex(Long userId);
}
