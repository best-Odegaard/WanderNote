package com.gkv.controller.user;

import com.gkv.dto.CommentDTO;
import com.gkv.dto.TravelJournalPageDTO;
import com.gkv.dto.TravelJournalPublishDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.TravelJournalService;
import com.gkv.vo.CommentVO;
import com.gkv.vo.TravelJournalVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/journal")
@Slf4j
@Api(tags = "游记相关接口")
public class TravelJournalController {
    @Autowired
    private TravelJournalService travelJournalService;

    @PostMapping("/publish")
    @ApiOperation("发布游记")
    public Result publish(@RequestBody @Validated TravelJournalPublishDTO dto){
        log.info("发布游记：{}", dto.getTitle());
        travelJournalService.publish(dto);
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("游记列表（分页）")
    public Result<PageResult<TravelJournalVO>> list(TravelJournalPageDTO dto) {
        log.info("查询游记列表：pageNum={}, pageSize={}, keyword={}, tag={}",
                dto.getPageNum(), dto.getPageSize(), dto.getKeyword(), dto.getTag());
        PageResult<TravelJournalVO> pageResult = travelJournalService.pageQuery(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @ApiOperation("游记详情")
    public Result<TravelJournalVO> detail(@PathVariable Long id) {
        log.info("查询游记详情：id={}", id);
        TravelJournalVO vo = travelJournalService.getByID(id);
        if (vo == null) {
            return Result.error("游记不存在");
        }
        return Result.success(vo);
    }

    @PutMapping("/like/{journalId}")
    @ApiOperation("点赞/取消点赞")
    public Result like(@PathVariable Long journalId){
        log.info("点赞/取消点赞：{}", journalId);
        travelJournalService.likeJournal(journalId);
        return Result.success();
    }

    @PostMapping("/comment")
    @ApiOperation("发表评论/回复评论")
    public Result addComment(@RequestBody @Validated CommentDTO dto){
        log.info("发表评论：{}",dto.getContent());
        travelJournalService.addComment(dto);
        return Result.success();
    }

    @DeleteMapping("/comment/{commentId}")
    @ApiOperation("删除评论")
    public Result deleteComment(@PathVariable Long commentId){
        log.info("删除评论，评论ID：{}", commentId);
        travelJournalService.deleteComment(commentId);
        return Result.success();
    }

    @GetMapping("/comment/list/{journalId}")
    @ApiOperation("查询评论列表")
    public Result<List<CommentVO>> getCommentList(@PathVariable Long journalId){
        log.info("查询评论列表，游记ID：{}", journalId);
        List<CommentVO> commentList = travelJournalService.getCommentList(journalId);
        return Result.success(commentList);
    }

    @DeleteMapping("/{journalId}")
    @ApiOperation("删除游记")
    public Result deleteJournal(@PathVariable Long journalId){
        log.info("删除游记，游记ID：{}", journalId);
        travelJournalService.deleteJournal(journalId);
        return Result.success();
    }

    @PostMapping("/collect/{journalId}")
    @ApiOperation("收藏游记")
    public Result collect(@PathVariable Long journalId){
        log.info("收藏游记：{}", journalId);
        travelJournalService.collect(journalId);
        return Result.success();
    }

    @DeleteMapping("/collect/{journalId}")
    @ApiOperation("取消收藏游记")
    public Result uncollect(@PathVariable Long journalId){
        log.info("取消收藏游记：{}", journalId);
        travelJournalService.uncollect(journalId);
        return Result.success();
    }

    @GetMapping("/hot")
    @ApiOperation("热门游记列表（按点赞数倒序）")
    public Result<List<TravelJournalVO>> hot(@RequestParam(required = false) Integer limit){
        return Result.success(travelJournalService.hot(limit));
    }

    @GetMapping("/my/list")
    @ApiOperation("我的游记列表（本人发布）")
    public Result<List<TravelJournalVO>> myJournals(){
        return Result.success(travelJournalService.myJournals());
    }

    @GetMapping("/my/collects")
    @ApiOperation("我的收藏游记列表")
    public Result<List<TravelJournalVO>> myCollects(){
        return Result.success(travelJournalService.myCollects());
    }
}


