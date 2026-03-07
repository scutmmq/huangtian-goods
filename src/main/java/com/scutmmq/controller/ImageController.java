package com.scutmmq.controller;

import com.scutmmq.entity.Result;
import com.scutmmq.utils.AliyunOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private  final AliyunOSSOperator aliyunOSSOperator;

    @PostMapping("/upload")
    public Result uploadImage(@RequestParam(value = "file")MultipartFile file) throws Exception {

        final String upload = aliyunOSSOperator.upload(file.getBytes(), Objects.requireNonNull(file.getOriginalFilename()));
        log.info("上传了图片:{}",upload);
        return  Result.success(upload);
    }
}
