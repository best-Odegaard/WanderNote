package com.gkv.service;

import com.gkv.dto.AdminUserPageDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.AdminUserVO;

import java.util.List;

public interface AdminUserService {

    /** 分页查询 App 用户（支持按画像标签 / 画像标记筛选） */
    PageResult<AdminUserVO> page(AdminUserPageDTO dto);

    /**
     * 按当前筛选条件导出（不分页，有上限）
     *
     * 列表项与分页接口同构，前端拿到后自己转 CSV
     * （服务端不生成文件：导出列由运营视角决定，放前端改起来更快）。
     */
    List<AdminUserVO> export(AdminUserPageDTO dto);

    /** 用户详情 */
    AdminUserVO detail(Long id);

    /** 启用/禁用用户 */
    void updateStatus(Long id, Integer status);
}
