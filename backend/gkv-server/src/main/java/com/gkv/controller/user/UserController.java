package com.gkv.controller.user;

import com.gkv.dto.UserInfoDTO;
import com.gkv.dto.UserLoginDTO;
import com.gkv.dto.UserRegisterDTO;
import com.gkv.result.Result;
import com.gkv.service.UserService;
import com.gkv.vo.UserInfoVO;
import com.gkv.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Slf4j
@Api(tags = "用户相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    //用户注册
    @PostMapping("/register")
    @ApiOperation("用户注册")
    public Result register(@RequestBody @Validated UserRegisterDTO dto){
        log.info("用户注册：{}",dto.getUsername());
        userService.register(dto);
        return Result.success();
    }

    //用户登录
    @PostMapping("/login")
    @ApiOperation("用户登录")
    public Result<UserLoginVO> login(@RequestBody @Validated UserLoginDTO dto){
        log.info("用户登录：{}",dto.getUsername());
        UserLoginVO vo = userService.login(dto);
        log.info("用户登录成功，userId={}，token={}", vo.getId(), vo.getToken());
        return Result.success(vo);
    }

    //获取当前登录用户信息
    @GetMapping("/info")
    @ApiOperation("获取当前用户信息")
    public Result<UserInfoVO> info(){
        return Result.success(userService.getInfo());
    }

    //更新当前登录用户信息
    @PutMapping("/info")
    @ApiOperation("更新当前用户信息")
    public Result<UserInfoVO> updateInfo(@RequestBody UserInfoDTO dto){
        return Result.success(userService.updateInfo(dto));
    }
}
