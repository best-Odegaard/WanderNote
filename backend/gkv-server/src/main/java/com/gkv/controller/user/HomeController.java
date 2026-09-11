package com.gkv.controller.user;

import com.gkv.context.BaseContext;
import com.gkv.result.Result;
import com.gkv.service.HomeService;
import com.gkv.vo.HomeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页相关接口
 */
@RestController
@RequestMapping("/home")
@Slf4j
@Api(tags = "首页相关接口")
public class HomeController {

    @Autowired
    private HomeService homeService;

    @GetMapping("/index")
    @ApiOperation("获取首页聚合数据")
    public Result<HomeVO> index() {
        Long userId = BaseContext.getCurrentId();
        log.info("获取首页数据，userId={}", userId);
        HomeVO vo = homeService.getHomeIndex(userId);
        return Result.success(vo);
    }
}
