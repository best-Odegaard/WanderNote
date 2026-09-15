package com.gkv.vo;

import com.gkv.dto.ProfileTagDTO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户列表项（含登录密码，供运营查看）
 */
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    /** 登录密码（明文，供运营查看） */
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
    private Integer points;
    private Integer status;
    private LocalDateTime createTime;

    /* ---------- AI 画像字段（无画像的用户，标签为空集合、其余为空） ---------- */

    /** 受控偏好标签及权重，按权重倒序 */
    private List<ProfileTagDTO> profileTags;
    /** AI 画像摘要 */
    private String profileSummary;
    /** 累计对话轮数 */
    private Integer profileChatRounds;
    /** 画像更新时间 */
    private LocalDateTime profileUpdateTime;
    /** 最后更新来源：rule/model/manual */
    private String profileSource;
    /** 运营备注 */
    private String profileRemark;
    /** 运营标记 */
    private String profileMark;
}
