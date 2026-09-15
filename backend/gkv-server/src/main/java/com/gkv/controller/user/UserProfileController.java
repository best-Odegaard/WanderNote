package com.gkv.controller.user;

import com.gkv.context.BaseContext;
import com.gkv.dto.UserProfileSwitchDTO;
import com.gkv.result.Result;
import com.gkv.service.UserProfileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户端-AI 画像回灌开关
 *
 * 挂在 /user 前缀下，天然要求登录（/user/** 不在匿名白名单里）。
 * 这是用户端对画像唯一的可见入口，所以对外文案是"使用 AI 记住的偏好"，
 * 不出现"画像"这种内部词。
 */
@RestController
@RequestMapping("/user/profile")
@Slf4j
@Api(tags = "用户端-AI偏好记忆开关")
public class UserProfileController {

    @Resource
    private UserProfileService userProfileService;

    @GetMapping("/switch")
    @ApiOperation("查询画像回灌开关（未沉淀过画像时返回默认开启）")
    public Result<Map<String, Object>> getSwitch() {
        Long userId = BaseContext.getCurrentId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", userProfileService.getInjectEnabled(userId));
        return Result.success(data);
    }

    @PutMapping("/switch")
    @ApiOperation("更新画像回灌开关（只关回灌，不关后台沉淀）")
    public Result<Map<String, Object>> updateSwitch(@RequestBody UserProfileSwitchDTO dto) {
        Long userId = BaseContext.getCurrentId();
        boolean enabled = dto == null || dto.getEnabled() == null || dto.getEnabled();
        boolean saved = userProfileService.updateInjectEnabled(userId, enabled);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", saved);
        return Result.success(data);
    }
}
