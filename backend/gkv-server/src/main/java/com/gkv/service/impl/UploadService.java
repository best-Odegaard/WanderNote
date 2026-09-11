package com.gkv.service.impl;

import com.gkv.config.COSProperties;
import com.gkv.exception.NullSelDataException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
public class UploadService {

    @Autowired
    private COSClient cosClient;
    @Autowired
    private COSProperties cosProperties;

    // 支持的文件类型
    private static final List<String> suffixes = Arrays.asList("image/png", "image/jpeg","video/mp4");


    public String uploadImage(MultipartFile file,Integer typee) {

        try {

            if (file.isEmpty()) {
                log.info("上传失败，文件为空");
                return null;
            }

            // 1)校验文件类型
            String type = file.getContentType(); //获取文件格式
            if (!suffixes.contains(type)) {
                log.error("上传失败，不支持的文件格式：{}", type);
                throw new NullSelDataException("上传失败，不支持的视频格式：" + type);
            }

            if(typee!=5){
                // 1、图片信息校验
                // 2)校验图片内容
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image == null) {
                    log.info("上传失败，文件内容不符合要求");
                    return null;
                }
            }


            String pathUrl="";

            switch (typee){
                case 3://文件
                    pathUrl="AI文旅/文件/";
                    break;
                case 4://图片
                    pathUrl="AI文旅/图片/";
                    break;
                default:// 其他
                    pathUrl="AI文旅/other/";
                    break;
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = ".jpg";
            }

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(type);
            // 指定要上传到 COS 上对象键 此key是文件唯一标识
            // 指定文件上传到 COS 上的路径，即对象键。最终文件会传到存储桶名字中的images文件夹下的fileName名
            String uuid = UUID.randomUUID().toString().replace("-", "");

            String key = pathUrl + uuid + originalFilename;
            PutObjectRequest putObjectRequest = new PutObjectRequest(cosProperties.getBucketName(), key, file.getInputStream(),objectMetadata);

            //使用cosClient调用第三方接口
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
            log.info("COS上传成功：{}", key);

            //拼接返回路径
            return "https://" + cosProperties.getBucketName() + ".cos." + cosProperties.getRegionName() + ".myqcloud.com/" + key;

        }catch (Exception e){
            log.error("上传失败", e);
            return null;
        }
    }
}

