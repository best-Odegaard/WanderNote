package com.gkv.service;

import com.gkv.dto.TravelScheduleDTO;
import com.gkv.vo.TravelResultVO;

public interface TravelScheduleService {
    /**
     * 完整流程：接收前端表单 → 调用AI Agent → 获取结果 → 合并数据入库 → 返回VO
     */
    TravelResultVO createPlanByAi(TravelScheduleDTO ScheduleDTO);
}
