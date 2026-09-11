package com.gkv.service;

import com.gkv.dto.AdminUserPageDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.AdminUserVO;

public interface AdminUserService {

    /** 分页查询 App 用户 */
    PageResult<AdminUserVO> page(AdminUserPageDTO dto);

    /** 用户详情 */
    AdminUserVO detail(Long id);

    /** 启用/禁用用户 */
    void updateStatus(Long id, Integer status);
}
