package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.entity.TravelJournal;
import com.gkv.entity.TravelJournalComment;
import com.gkv.entity.User;
import com.gkv.entity.UserFeedback;
import com.gkv.mapper.TravelJournalCommentMapper;
import com.gkv.mapper.TravelJournalMapper;
import com.gkv.mapper.UserFeedbackMapper;
import com.gkv.mapper.UserMapper;
import com.gkv.service.AdminDashboardService;
import com.gkv.vo.DashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TravelJournalMapper travelJournalMapper;

    @Autowired
    private TravelJournalCommentMapper travelJournalCommentMapper;

    @Autowired
    private UserFeedbackMapper userFeedbackMapper;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 总量与今日新增
        vo.setTotalUser(userMapper.selectCount(null));
        vo.setTodayUser(userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreateTime, todayStart)));
        vo.setTotalJournal(travelJournalMapper.selectCount(null));
        vo.setTodayJournal(travelJournalMapper.selectCount(
                new LambdaQueryWrapper<TravelJournal>().ge(TravelJournal::getCreateTime, todayStart)));
        vo.setTotalComment(travelJournalCommentMapper.selectCount(null));
        vo.setTotalFeedback(userFeedbackMapper.selectCount(null));
        vo.setPendingFeedback(userFeedbackMapper.selectCount(
                new LambdaQueryWrapper<UserFeedback>().eq(UserFeedback::getStatus, 0)));

        // 近7天新增趋势（含今天）
        List<DashboardVO.TrendItemVO> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();

            DashboardVO.TrendItemVO item = new DashboardVO.TrendItemVO();
            item.setDate(day.format(DAY_FMT));
            item.setUserCount(userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .ge(User::getCreateTime, start)
                            .lt(User::getCreateTime, end)));
            item.setJournalCount(travelJournalMapper.selectCount(
                    new LambdaQueryWrapper<TravelJournal>()
                            .ge(TravelJournal::getCreateTime, start)
                            .lt(TravelJournal::getCreateTime, end)));
            trend.add(item);
        }
        vo.setTrend(trend);

        // 反馈状态分布：0待处理 1已处理 2已关闭
        List<DashboardVO.FeedbackStatusVO> dist = new ArrayList<>();
        for (int status = 0; status <= 2; status++) {
            DashboardVO.FeedbackStatusVO item = new DashboardVO.FeedbackStatusVO();
            item.setStatus(status);
            item.setCount(userFeedbackMapper.selectCount(
                    new LambdaQueryWrapper<UserFeedback>().eq(UserFeedback::getStatus, status)));
            dist.add(item);
        }
        vo.setFeedbackDist(dist);

        return vo;
    }
}
