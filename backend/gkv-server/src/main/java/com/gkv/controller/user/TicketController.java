package com.gkv.controller.user;

import com.gkv.dto.TicketSelectDTO;
import com.gkv.result.Result;
import com.gkv.service.TicketService;
import com.gkv.vo.TicketVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订票（火车票 / 飞机票）接口。
 *
 * 交易边界（合规红线，见 产品项目文档 5.7.1）：
 *   本站只做「候选班次展示 + 跳转 12306/携程深链」，**不做站内下单、不代购**。
 *   12306 与航司都没有公开开放 API，接口里刻意没有余票/舱位/库存字段 ——
 *   这是有意为之：不给用户「有票/可订」的错觉。
 *   也绝不收集用户 12306 账号、证件号等购票凭证。
 *
 * 接口一览：
 *   GET  /ticket/search          候选班次（火车/飞机，按线路+日期）
 *   POST /ticket/select          选定去程或返程，写 trip_ticket
 *   GET  /ticket/trip/{tripId}   读行程已选的票（详情页显示「怎么去、怎么回」）
 *   POST /ticket/clear           清除某个方向（或全部）的票
 */
@RestController
@RequestMapping("/ticket")
@Slf4j
@Api(tags = "订票相关接口")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @GetMapping("/search")
    @ApiOperation("候选班次（type=train/flight，按出发/到达城市与日期筛选，每条附购买深链）")
    public Result<List<TicketVO>> search(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) String date) {
        log.info("查询班次候选：type={}, from={}, to={}, date={}", type, from, to, date);
        return Result.success(ticketService.search(type, from, to, date));
    }

    @PostMapping("/select")
    @ApiOperation("选定班次：写行程票务（去程/返程各一条；行程未保存时只校验，不落库）")
    public Result<TicketVO> select(@RequestBody TicketSelectDTO dto) {
        log.info("选定班次：tripId={}, direction={}, ticketCode={}, ticketNo={}",
                dto == null ? null : dto.getTripId(),
                dto == null ? null : dto.getDirection(),
                dto == null ? null : dto.getTicketCode(),
                dto == null ? null : dto.getTicketNo());
        return Result.success(ticketService.selectForTrip(dto));
    }

    @GetMapping("/trip/{tripId}")
    @ApiOperation("读行程已选的票（去程在前、返程在后；没有则返回空列表）")
    public Result<List<TicketVO>> tripTickets(@PathVariable Long tripId) {
        return Result.success(ticketService.getTripTickets(tripId));
    }

    @PostMapping("/clear")
    @ApiOperation("清除行程的票（direction 为空则去程与返程都清）")
    public Result<Void> clear(@RequestParam Long tripId,
                              @RequestParam(required = false) String direction) {
        log.info("清除行程票务：tripId={}, direction={}", tripId, direction);
        ticketService.clearForTrip(tripId, direction);
        return Result.success();
    }
}
