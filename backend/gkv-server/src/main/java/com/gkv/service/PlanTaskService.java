package com.gkv.service;

import com.gkv.dto.ChatRequestDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.dto.TripPlanFrameDTO;
import com.gkv.utils.AgentHttpUtil;
import com.gkv.vo.PlanSubmitVO;
import com.gkv.vo.PlanTaskVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 行程生成任务管理器：
 * 1. 提交后立即返回 taskId，后台线程分两阶段生成（先框架、再详情）；
 * 2. 前端轮询 /travel/plan/status/{taskId} 获取阶段进度与骨架预览；
 * 3. 生成完成后写入 Redis 缓存，同一行程再次提交直接秒开。
 */
@Service
@Slf4j
public class PlanTaskService {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_CANCELED = "CANCELED";

    public static final String STAGE_RETRIEVING = "retrieving";
    public static final String STAGE_GENERATING_FRAME = "generating_frame";
    public static final String STAGE_GENERATING_DETAIL = "generating_detail";
    public static final String STAGE_DONE = "done";

    /** 任务在内存中的保留时间：30 分钟 */
    private static final long TASK_TTL_MILLIS = 30 * 60 * 1000L;

    @Resource
    private AgentHttpUtil agentHttpUtil;

    @Resource
    private TravelPlanCacheService travelPlanCacheService;

    private final Map<String, PlanTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Data
    public static class PlanTask {
        private final String taskId;
        private final ChatRequestDTO req;
        private final long startTime = System.currentTimeMillis();
        private volatile String status = STATUS_PROCESSING;
        private volatile String stage = STAGE_RETRIEVING;
        private volatile String message = "正在准备生成…";
        private volatile TripPlanFrameDTO frame;
        private volatile PlanResponseDTO plan;
        private volatile String errorMsg;
        private volatile boolean canceled = false;
    }

    /**
     * 提交行程生成任务。命中缓存时直接返回完整行程（fromCache=true），否则返回 taskId。
     */
    public PlanSubmitVO submit(ChatRequestDTO req) {
        if (req.getSession_id() == null || req.getSession_id().isEmpty()) {
            req.setSession_id(UUID.randomUUID().toString().replace("-", ""));
        }
        String cacheKey = buildCacheKey(req);

        // 1. 优先命中缓存：同一行程秒开
        PlanResponseDTO cached = travelPlanCacheService.get(cacheKey);
        if (cached != null) {
            PlanSubmitVO hit = new PlanSubmitVO();
            hit.setFromCache(true);
            hit.setPlan(cached);
            log.info("[PlanTask] 缓存命中，直接返回: {}", cacheKey);
            return hit;
        }

        // 2. 未命中：创建后台任务
        String taskId = UUID.randomUUID().toString().replace("-", "");
        PlanTask task = new PlanTask(taskId, req);
        tasks.put(taskId, task);
        log.info("[PlanTask] 创建任务 {}，key={}", taskId, cacheKey);
        executor.submit(() -> run(task, cacheKey));

        PlanSubmitVO vo = new PlanSubmitVO();
        vo.setTaskId(taskId);
        vo.setFromCache(false);
        return vo;
    }

    /** 后台任务：检索 → 框架 → 详情 → 缓存 */
    private void run(PlanTask task, String cacheKey) {
        try {
            ChatRequestDTO req = task.getReq();

            // 阶段一：行程框架（快，先给骨架）
            if (task.isCanceled()) return;
            task.setStage(STAGE_GENERATING_FRAME);
            task.setMessage("AI 正在生成行程框架…");
            TripPlanFrameDTO frame = agentHttpUtil.callPlanFrame(req);
            if (task.isCanceled()) return;
            task.setFrame(frame);

            // 阶段二：完整详情
            task.setStage(STAGE_GENERATING_DETAIL);
            task.setMessage("AI 正在细化每日行程…");
            PlanResponseDTO plan = agentHttpUtil.callPlanDetail(req, frame);
            if (task.isCanceled()) return;
            task.setPlan(plan);
            // 先写缓存再置 DONE：避免前端看到 DONE 后立即重提时落入缓存未写入的极小窗口
            travelPlanCacheService.put(cacheKey, plan);
            task.setStage(STAGE_DONE);
            task.setStatus(STATUS_DONE);
            task.setMessage("生成完成");
            log.info("[PlanTask] 任务 {} 完成，已写入缓存", task.getTaskId());
        } catch (Exception e) {
            task.setStatus(STATUS_ERROR);
            task.setMessage("生成失败，请稍后重试");
            task.setErrorMsg(e.getMessage());
            log.error("[PlanTask] 任务 {} 失败: {}", task.getTaskId(), e.getMessage(), e);
        }
    }

    /**
     * 查询任务状态；已过期（>30min）的任务视为不存在
     */
    public PlanTaskVO getStatus(String taskId) {
        PlanTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        if (System.currentTimeMillis() - task.getStartTime() > TASK_TTL_MILLIS) {
            tasks.remove(taskId);
            return null;
        }
        PlanTaskVO vo = new PlanTaskVO();
        vo.setTaskId(taskId);
        vo.setStatus(task.getStatus());
        vo.setStage(task.getStage());
        vo.setMessage(task.getMessage());
        vo.setElapsedSec((int) ((System.currentTimeMillis() - task.getStartTime()) / 1000));
        vo.setFrame(task.getFrame());
        vo.setPlan(task.getPlan());
        vo.setErrorMsg(task.getErrorMsg());
        return vo;
    }

    /** 取消生成（后台 Python 调用无法中断，但结果会被丢弃，任务标记为已取消） */
    public void cancel(String taskId) {
        PlanTask task = tasks.get(taskId);
        if (task != null && !STATUS_DONE.equals(task.getStatus())) {
            task.setCanceled(true);
            task.setStatus(STATUS_CANCELED);
            task.setStage("canceled");
            task.setMessage("已取消");
        }
    }

    private String buildCacheKey(ChatRequestDTO req) {
        com.gkv.dto.BaseInfoDTO base = req.getBase_info();
        return TravelPlanCacheService.buildKey(
                base == null ? "" : base.getDestination_city(),
                base == null ? 0 : (base.getDays() == null ? 0 : base.getDays()),
                base == null ? null : base.getHobby(),
                base == null ? null : base.getBudget());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
