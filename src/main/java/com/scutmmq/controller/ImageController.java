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

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final AliyunOSSOperator aliyunOSSOperator;

    @PostMapping("/upload")
    public Result uploadImage(@RequestParam(value = "file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error("文件缺少扩展名");
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return Result.error("不支持的文件类型，仅允许上传 jpg、jpeg、png、webp、gif 图片");
        }

        byte[] bytes = file.getBytes();
        if (!isValidImageHeader(bytes, ext)) {
            return Result.error("文件内容与图片格式不匹配（非法文件头）");
        }

        final String upload = aliyunOSSOperator.upload(bytes, originalFilename);
        log.info("上传了图片:{}", upload);
        return Result.success(upload);
    }

    private boolean isValidImageHeader(byte[] bytes, String ext) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            // JPEG: FF D8 FF
            return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            return (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
        }
        if ("gif".equals(ext)) {
            // GIF: GIF87a or GIF89a
            return bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8';
        }
        if ("webp".equals(ext)) {
            // WEBP: RIFF....WEBP
            return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        }
        return false;
    }
}

