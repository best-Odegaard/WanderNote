package com.gkv.service;

import com.gkv.dto.ActivitySaveDTO;
import com.gkv.dto.AdminContentPageDTO;
import com.gkv.dto.ScenicSaveDTO;
import com.gkv.entity.ActivityInfo;
import com.gkv.entity.ScenicSpot;
import com.gkv.result.PageResult;
import com.gkv.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface AdminContentService {

    /** 分页查询景点（含禁用） */
    PageResult<ScenicSpot> scenicPage(AdminContentPageDTO dto);

    /** 新增/编辑景点 */
    void scenicSave(ScenicSaveDTO dto);

    /** 删除景点 */
    void scenicDelete(Long id);

    /** 启用/禁用景点 */
    void scenicStatus(Long id, Integer status);

    /** Excel 批量导入景点，返回导入结果（含失败行明细） */
    ImportResultVO importScenic(MultipartFile file);

    /** 生成景点导入模板（xlsx）并写入响应流 */
    void scenicTemplate(HttpServletResponse response);

    /** 分页查询活动（含禁用） */
    PageResult<ActivityInfo> activityPage(AdminContentPageDTO dto);

    /** 新增/编辑活动 */
    void activitySave(ActivitySaveDTO dto);

    /** 删除活动 */
    void activityDelete(Long id);

    /** 启用/禁用活动 */
    void activityStatus(Long id, Integer status);
}
