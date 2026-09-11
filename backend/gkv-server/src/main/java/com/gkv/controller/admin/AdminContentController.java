package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.ActivitySaveDTO;
import com.gkv.dto.AdminContentPageDTO;
import com.gkv.dto.ScenicSaveDTO;
import com.gkv.entity.ActivityInfo;
import com.gkv.entity.ScenicSpot;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.AdminContentService;
import com.gkv.vo.ImportResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

/**
 * 管理端-内容管理（景点/活动）
 */
@RestController
@RequestMapping("/admin/content")
@Slf4j
@Api(tags = "管理端-内容管理（景点/活动）")
public class AdminContentController {

    @Autowired
    private AdminContentService adminContentService;

    // ─────────── 景点 ───────────

    @GetMapping("/scenic")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("景点分页列表（含禁用）")
    public Result<PageResult<ScenicSpot>> scenicPage(AdminContentPageDTO dto) {
        log.info("查询景点列表：keyword={}, city={}, status={}, page={}",
                dto.getKeyword(), dto.getCity(), dto.getStatus(), dto.getPageNum());
        return Result.success(adminContentService.scenicPage(dto));
    }

    @PostMapping("/scenic")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("新增景点")
    public Result scenicAdd(@RequestBody @Validated ScenicSaveDTO dto) {
        log.info("新增景点：{}", dto.getName());
        adminContentService.scenicSave(dto);
        return Result.success();
    }

    @PutMapping("/scenic")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("编辑景点")
    public Result scenicEdit(@RequestBody @Validated ScenicSaveDTO dto) {
        log.info("编辑景点：id={}", dto.getId());
        adminContentService.scenicSave(dto);
        return Result.success();
    }

    @DeleteMapping("/scenic/{id}")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("删除景点")
    public Result scenicDelete(@PathVariable Long id) {
        log.info("删除景点：id={}", id);
        adminContentService.scenicDelete(id);
        return Result.success();
    }

    @PutMapping("/scenic/{id}/status/{status}")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("启用/禁用景点（status：0禁用 1启用）")
    public Result scenicStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改景点状态：id={}, status={}", id, status);
        adminContentService.scenicStatus(id, status);
        return Result.success();
    }

    @PostMapping("/scenic/import")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("Excel批量导入景点（返回成功/失败行明细）")
    public Result<ImportResultVO> scenicImport(@RequestParam("file") MultipartFile file) {
        log.info("批量导入景点，文件名：{}", file.getOriginalFilename());
        return Result.success(adminContentService.importScenic(file));
    }

    @GetMapping("/scenic/template")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("下载景点导入模板（xlsx，含示例行）")
    public void scenicTemplate(HttpServletResponse response) {
        adminContentService.scenicTemplate(response);
    }

    // ─────────── 活动 ───────────

    @GetMapping("/activity")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("活动分页列表（含禁用）")
    public Result<PageResult<ActivityInfo>> activityPage(AdminContentPageDTO dto) {
        log.info("查询活动列表：keyword={}, city={}, status={}, page={}",
                dto.getKeyword(), dto.getCity(), dto.getStatus(), dto.getPageNum());
        return Result.success(adminContentService.activityPage(dto));
    }

    @PostMapping("/activity")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("新增活动")
    public Result activityAdd(@RequestBody @Validated ActivitySaveDTO dto) {
        log.info("新增活动：{}", dto.getTitle());
        adminContentService.activitySave(dto);
        return Result.success();
    }

    @PutMapping("/activity")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("编辑活动")
    public Result activityEdit(@RequestBody @Validated ActivitySaveDTO dto) {
        log.info("编辑活动：id={}", dto.getId());
        adminContentService.activitySave(dto);
        return Result.success();
    }

    @DeleteMapping("/activity/{id}")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("删除活动")
    public Result activityDelete(@PathVariable Long id) {
        log.info("删除活动：id={}", id);
        adminContentService.activityDelete(id);
        return Result.success();
    }

    @PutMapping("/activity/{id}/status/{status}")
    @RequirePerm(PermConstant.CONTENT_MANAGE)
    @ApiOperation("启用/禁用活动（status：0禁用 1启用）")
    public Result activityStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改活动状态：id={}, status={}", id, status);
        adminContentService.activityStatus(id, status);
        return Result.success();
    }
}
