package com.gkv.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gkv.dto.*;
import com.gkv.entity.TravelSchedule;
import com.gkv.utils.AgentHttpUtil;
import com.gkv.vo.TravelResultVO;
import org.springframework.beans.BeanUtils;
import com.gkv.mapper.TravelScheduleMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

@Service
public class TravelScheduleServiceImpl extends ServiceImpl<TravelScheduleMapper, TravelSchedule>
        implements TravelScheduleService {


    @Resource
    private AgentHttpUtil agentHttpUtil;

    @Override
    public TravelResultVO createPlanByAi(TravelScheduleDTO ScheduleDTO) {
        // ========== 步骤1：暂存数据，【不执行入库】，封装参数准备发给Agent ==========
        AgentRequestDTO agentReq = new AgentRequestDTO();
        
        // 手动设置字段（因为字段名是下划线风格，与Python Agent的BaseInfo对应）
        agentReq.setDeparture_city(ScheduleDTO.getStartCity());
        agentReq.setDestination_city(ScheduleDTO.getDestination());
        // TravelScheduleDTO 中没有 startDate 和 endDate，这里暂时设置为 null
        // Python Agent会根据days自动计算日期范围
        agentReq.setStart_day(null);
        agentReq.setEnd_date(null);
        agentReq.setDays(ScheduleDTO.getTravelDays());
        agentReq.setHobby(ScheduleDTO.getTravelPreference());
        agentReq.setPeople_num(ScheduleDTO.getPeopleNum() != null ? String.valueOf(ScheduleDTO.getPeopleNum()) : null);
        agentReq.setBudget(ScheduleDTO.getBudget() != null ? String.valueOf(ScheduleDTO.getBudget()) : null);

        // ========== 步骤2：调用AI Agent，接收返回数据 ==========
        PlanResponseDTO agentResp = agentHttpUtil.callAgent(agentReq);

        // ========== 步骤3：合并【前端原始表单数据】+【AI返回行程】组装数据库实体 ==========
        TravelSchedule entity = new TravelSchedule();
        // 复制用户填写信息
        BeanUtils.copyProperties(ScheduleDTO, entity);

        // 将 AI 返回的行程结果映射入库
        if (agentResp != null && agentResp.getPlan_data() != null) {
            TripPlanDTO plan = agentResp.getPlan_data();
            // 行程标题
            entity.setPlanTitle(plan.getTitle());
            // 完整原始 JSON（含 day_list 每日景点明细）
            entity.setOriginAiJson(JSON.toJSONString(plan));
            // 拼接完整行程攻略文本（Day1: 景点(时段) -> ...），供详情展示
            entity.setAgentTravelStrategy(buildStrategyText(plan));
        }

        // ========== 步骤4：拿到AI结果之后，才执行数据库入库操作 ==========
        save(entity);

        // ========== 步骤5：封装VO返回前端 ==========
        TravelResultVO vo = new TravelResultVO();
        BeanUtils.copyProperties(entity, vo);
        if (agentResp != null && agentResp.getPlan_data() != null) {
            TripPlanDTO plan = agentResp.getPlan_data();
            vo.setPlanTitle(plan.getTitle());
            vo.setTotalWalk(plan.getTotal_walk());
            if (plan.getDay_list() != null && !plan.getDay_list().isEmpty()) {
                DayScheduleDTO firstDay = plan.getDay_list().get(0);
                vo.setFirstDayDate(firstDay.getDate());
                if (firstDay.getSchedule() != null && !firstDay.getSchedule().isEmpty()) {
                    vo.setFirstSpotName(firstDay.getSchedule().get(0).getSpot_name());
                }
            }
        }
        return vo;
    }

    /** 将 day_list 拼接为可读的完整行程攻略文本 */
    private String buildStrategyText(TripPlanDTO plan) {
        if (plan == null || plan.getDay_list() == null || plan.getDay_list().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (plan.getTitle() != null) {
            sb.append("【").append(plan.getTitle()).append("】\n");
        }
        for (DayScheduleDTO day : plan.getDay_list()) {
            sb.append("\nDay ").append(day.getDate()).append("：");
            if (day.getSchedule() != null) {
                for (AttractionDTO spot : day.getSchedule()) {
                    sb.append("\n  · ").append(spot.getSpot_name());
                    if (spot.getVisit_time_range() != null) {
                        sb.append("（").append(spot.getVisit_time_range()).append("）");
                    }
                }
            }
        }
        return sb.toString();
    }
}
