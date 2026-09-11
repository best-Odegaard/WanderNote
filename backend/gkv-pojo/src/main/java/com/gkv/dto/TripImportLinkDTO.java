package com.gkv.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TripImportLinkDTO {
    @NotBlank(message = "链接不能为空")
    private String sourceUrl;

    private Long userId;
}
