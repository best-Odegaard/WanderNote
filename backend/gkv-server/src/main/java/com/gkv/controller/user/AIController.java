package com.gkv.controller.user;

import com.gkv.dto.AgentRequestDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.result.Result;
import com.gkv.utils.AgentHttpUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/ai")
@Api(tags = "AI智能体接口")
public class AIController {
    
    @Resource
    private AgentHttpUtil agentHttpUtil;
    
    @PostMapping("/generate-plan")
    @ApiOperation("生成旅游行程规划")
    public Result<PlanResponseDTO> generatePlan(@RequestBody AgentRequestDTO request) {
        try {
            PlanResponseDTO response = agentHttpUtil.callAgent(request);
            return Result.success(response);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("调用AI服务失败：" + e.getMessage());
        }
    }
}
