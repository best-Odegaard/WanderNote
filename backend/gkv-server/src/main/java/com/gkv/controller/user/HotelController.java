package com.gkv.controller.user;

import com.gkv.dto.HotelSelectDTO;
import com.gkv.result.Result;
import com.gkv.service.HotelService;
import com.gkv.vo.HotelVO;
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
 * 酒店（住宿）接口。
 *
 * 交易边界：本站只做「候选筛选 + 跳转携程深链」，不做站内下单。
 *   携程没有公开开放 API，价格是参考价，接口里没有库存/房态字段 ——
 *   这是有意为之：不给用户「有房/可订」的错觉（见 产品项目文档 5.7.2）。
 */
@RestController
@RequestMapping("/hotel")
@Slf4j
@Api(tags = "酒店相关接口")
public class HotelController {

    @Autowired
    private HotelService hotelService;

    @GetMapping("/search")
    @ApiOperation("酒店候选（按城市/日期/档次/位置筛选，每条附携程深链）")
    public Result<List<HotelVO>> search(@RequestParam(required = false) String city,
                                        @RequestParam(required = false) String checkin,
                                        @RequestParam(required = false) String checkout,
                                        @RequestParam(required = false) String style,
                                        @RequestParam(required = false) String area) {
        log.info("查询酒店候选：city={}, checkin={}, checkout={}, style={}, area={}",
                city, checkin, checkout, style, area);
        return Result.success(hotelService.search(city, checkin, checkout, style, area));
    }

    @PostMapping("/select")
    @ApiOperation("选定酒店：写行程住宿并回写行程的住宿名（行程未保存时只校验，不落库）")
    public Result<HotelVO> select(@RequestBody HotelSelectDTO dto) {
        log.info("选定酒店：tripId={}, hotelCode={}, name={}",
                dto == null ? null : dto.getTripId(),
                dto == null ? null : dto.getHotelCode(),
                dto == null ? null : dto.getName());
        return Result.success(hotelService.selectForTrip(dto));
    }

    @GetMapping("/trip/{tripId}")
    @ApiOperation("读行程已保存的住宿（详情页/路线页恢复闭环坐标用）")
    public Result<HotelVO> tripHotel(@PathVariable Long tripId) {
        return Result.success(hotelService.getTripHotel(tripId));
    }
}
