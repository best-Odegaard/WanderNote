package com.gkv.service;

import com.gkv.dto.AdminSaveDTO;
import com.gkv.dto.RoleSaveDTO;
import com.gkv.dto.PageQuery;
import com.gkv.result.PageResult;
import com.gkv.vo.AdminVO;
import com.gkv.vo.RoleVO;

import java.util.List;
import java.util.Map;

public interface AdminManageService {

    /** 分页查询管理员 */
    PageResult<AdminVO> adminPage(PageQuery dto);

    /** 新增/编辑管理员 */
    void adminSave(AdminSaveDTO dto);

    /** 删除管理员（不能删除自己） */
    void adminDelete(Long id);

    /** 启用/禁用管理员 */
    void adminStatus(Long id, Integer status);

    /** 角色列表 */
    List<RoleVO> roleList();

    /** 新增/编辑角色 */
    void roleSave(RoleSaveDTO dto);

    /** 删除角色（有管理员关联时禁止） */
    void roleDelete(Long id);

    /** 全部权限点（key+name） */
    List<Map<String, String>> permList();
}
