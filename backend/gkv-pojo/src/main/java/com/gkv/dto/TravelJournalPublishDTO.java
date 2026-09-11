package com.gkv.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class TravelJournalPublishDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 100,message = "标题不能超过100个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @NotEmpty(message = "图片不能为空")
    private List<String> imageUrls;

    private List<String> tags;
    private String location;
}
