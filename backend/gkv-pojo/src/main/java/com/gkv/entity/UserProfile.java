package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 用户画像（对话沉淀，一个用户一行，user_id 唯一）
 *
 * 列名对齐「线上真正生效的那张表 geek012.user_profile」：
 *   先确认了库里实际存在的表结构（开发库 2026-09-14 已有该表，字段名如下），
 *   再按它的列名映射，而不是另起一套名字 —— 否则 create table if not exists 会因为
 *   "表已存在" 直接跳过，代码里的列名一个都对不上，运行时报 Unknown column，
 *   而画像链路整条包在 try/catch 里，表现是"功能静默失效"，最难查。
 *
 * 因此下面凡是列名与驼峰不一致的字段都显式标了 @TableField，映射集中在一处，
 * 一眼能看出 Java 字段与数据库列的对应关系。
 */
@Data
@TableName("user_profile")
public class UserProfile {

    /** 最后更新来源：规则累加（每轮对话） */
    public static final String SOURCE_RULE = "rule";
    /** 最后更新来源：摘要模型重写 */
    public static final String SOURCE_MODEL = "model";
    /** 最后更新来源：运营人工修正 */
    public static final String SOURCE_MANUAL = "manual";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 受控偏好标签及权重，JSON 数组：[{"tag":"自然风光","weight":3}]，按权重倒序 */
    @TableField("pref_tags")
    private String preferenceTags;

    /** 自由标签，JSON 数组，仅展示不参与筛选聚合 */
    @TableField("free_tags")
    private String freeTags;

    /** 硬性约束，多个用中文分号「；」分隔 */
    @TableField("constraints")
    private String constraintsText;

    /** 预算档位：经济/适中/高档 */
    private String budgetLevel;

    /** 出行节奏：悠闲/常规/暴走 */
    private String pace;

    /** 同行人：独自/情侣/朋友/家庭 */
    private String companions;

    /** 偏好天数 */
    private Integer preferDays;

    /** AI 文字画像摘要 */
    private String summaryText;

    /** 摘要最后生成时间 */
    @TableField("summary_update_time")
    private LocalDateTime summaryTime;

    /** 累计对话轮数 */
    @TableField("chat_round_count")
    private Integer chatRounds;

    /** 最后更新来源：rule 规则累加 / model 摘要模型重写 / manual 人工编辑 */
    @TableField("last_source")
    private String lastUpdateSource;

    /** 画像回灌开关：0 关 1 开（用户端唯一可见开关） */
    @TableField("use_preference")
    private Integer injectEnabled;

    /** 运营备注 */
    private String remark;

    /** 运营标记 */
    @TableField("mark")
    private String profileMark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
