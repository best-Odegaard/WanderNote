package com.gkv.controller.admin;

import com.gkv.dto.UploadImageDTO;
import com.gkv.exception.BaseException;
import com.gkv.result.Result;
import com.gkv.service.impl.UploadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端-图片上传。
 *
 * 复用用户端那套 COS 上传服务（UploadService），只是换成 /admin/** 入口：
 * 用户端接口在用户登录拦截器覆盖范围内（需要用户 token），管理端拿不到，
 * 所以后台需要自己的上传入口。挂在 /admin/** 下即由管理端 token 校验保护，
 * 不额外声明 @RequirePerm —— 上传是基础能力，任何已登录管理员都该能用。
 */
@RestController
@RequestMapping("/admin/upload")
@Slf4j
@Api(tags = "管理端-文件上传")
public class AdminUploadController {

    @Autowired
    private UploadService uploadService;

    /** 单张图片上限：与前端一致（nginx 全局 50m、Spring multipart 20MB，都留有余量） */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    @PostMapping("/image")
    @ApiOperation("上传图片，返回可直接访问的 URL（封面图等）")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "type", required = false, defaultValue = "4") Integer type) {
        if (file == null || file.isEmpty()) {
            throw new BaseException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BaseException("图片不能超过 10MB，当前 "
                    + Math.round(file.getSize() / 1024.0 / 1024.0) + "MB，请压缩后再上传");
        }
        String url = uploadService.uploadImage(file, type);
        if (url == null) {
            // UploadService 内部把失败原因吞掉了，这里给不出细节，所以别把话说死：
            // 图片格式不对、以及对象存储（腾讯云 COS）配置/账号异常，都会走到这里。
            // 具体原因在后端日志里（搜「上传失败」，COS 欠费会打印 CosServiceException）。
            log.error("管理端图片上传失败：type={}，fileName={}，size={}",
                    type, file.getOriginalFilename(), file.getSize());
            throw new BaseException("图片上传失败：请确认图片是 png / jpg 且内容正常；"
                    + "若图片没问题，通常是对象存储（COS）配置或账号异常，需查看后端日志");
        }
        log.info("管理端上传图片成功：type={}，url={}", type, url);
        return Result.success(url);
    }
}
