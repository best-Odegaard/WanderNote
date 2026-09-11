package com.gkv.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传参数封装类
 */
@Data // Lombok注解，自动生成getter、setter、toString等
@AllArgsConstructor
@NoArgsConstructor
public class UploadImageDTO {
    /**
     * 要上传的图文件
     */
    @ApiModelProperty("要上传的文件")
    private MultipartFile file;

    /**
     * 文件类型标识（1-每周热点图片；2-轮播图片；3-近期比赛图片，4-学习视频）
     */
    @ApiModelProperty("文件类型标识（3-文件，4-图片）")
    private Integer type;
}