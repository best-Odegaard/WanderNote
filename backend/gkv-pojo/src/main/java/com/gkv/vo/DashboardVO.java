package com.gkv.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据看板统计结果
 */
@Data
public class DashboardVO {
    /** 用户总数 */
    private Long totalUser;
    /** 今日新增用户 */
    private Long todayUser;
    /** 游记总数 */
    private Long totalJournal;
    /** 今日新增游记 */
    private Long todayJournal;
    /** 评论总数 */
    private Long totalComment;
    /** 反馈总数 */
    private Long totalFeedback;
    /** 待处理反馈 */
    private Long pendingFeedback;
    /** 近7天新增趋势（含今天） */
    private List<TrendItemVO> trend;
    /** 反馈状态分布 */
    private List<FeedbackStatusVO> feedbackDist;

    @Data
    public static class TrendItemVO {
        /** 日期 MM-dd */
        private String date;
        private Long userCount;
        private Long journalCount;
    }

    @Data
    public static class FeedbackStatusVO {
        /** 0待处理 1已处理 2已关闭 */
        private Integer status;
        private Long count;
    }
}
