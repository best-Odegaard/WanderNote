package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.AdminJournalPageDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.AdminJournalService;
import com.gkv.vo.AdminJournalVO;
import com.gkv.vo.JournalDetailVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-游记管理（含评论）
 */
@RestController
@RequestMapping("/admin/journal")
@Slf4j
@Api(tags = "管理端-游记管理")
public class AdminJournalController {

    @Autowired
    private AdminJournalService adminJournalService;

    @GetMapping
    @RequirePerm(PermConstant.JOURNAL_VIEW)
    @ApiOperation("游记分页列表（标题关键词/状态筛选）")
    public Result<PageResult<AdminJournalVO>> page(AdminJournalPageDTO dto) {
        log.info("查询游记列表：keyword={}, status={}, page={}",
                dto.getKeyword(), dto.getStatus(), dto.getPageNum());
        return Result.success(adminJournalService.page(dto));
    }

    @GetMapping("/{id}")
    @RequirePerm(PermConstant.JOURNAL_VIEW)
    @ApiOperation("游记详情（含评论列表）")
    public Result<JournalDetailVO> detail(@PathVariable Long id) {
        return Result.success(adminJournalService.detail(id));
    }

    @DeleteMapping("/{id}")
    @RequirePerm(PermConstant.JOURNAL_MANAGE)
    @ApiOperation("删除/下架游记")
    public Result delete(@PathVariable Long id) {
        log.info("删除游记：id={}", id);
        adminJournalService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status/{status}")
    @RequirePerm(PermConstant.JOURNAL_MANAGE)
    @ApiOperation("修改游记状态（status：0下架 1正常）")
    public Result updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改游记状态：id={}, status={}", id, status);
        adminJournalService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/comment/{commentId}")
    @RequirePerm(PermConstant.JOURNAL_MANAGE)
    @ApiOperation("删除评论（连同其回复）")
    public Result deleteComment(@PathVariable Long commentId) {
        log.info("删除评论：commentId={}", commentId);
        adminJournalService.deleteComment(commentId);
        return Result.success();
    }
}
