package com.gkv.controller.user;

import com.gkv.annotation.RateLimit;
import com.gkv.dto.UploadImageDTO;
import com.gkv.result.Result;
import com.gkv.service.impl.UploadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/private/file")
@Api(tags = "文件上传")
@Slf4j
public class CosController {

    /**
     * 上传图片
     * @param file
     * @return
     */

    @Autowired
    private UploadService uploadService;

    @PostMapping("cos/upload")
    @RateLimit(key = "chat:cos:file", timeWindow = 60, maxCount = 10, message = "上传头像过于频繁，请稍后再试")
    @ApiOperation("cos上传文件")
    public Result<String> uploadImage(UploadImageDTO uploadImageDTO) {

            /*@RequestParam("file") MultipartFile file,
            @RequestParam("type") Integer type*/

        String url = uploadService.uploadImage(uploadImageDTO.getFile(),uploadImageDTO.getType());
        log.info("返回地址：【{}】", url);
        return Result.success(url);

    }


}
