package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {
    private long id;
    private String username;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String birthday;
    private String token;
}
