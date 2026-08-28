package com.scutmmq.security;

import com.scutmmq.controller.ImageController;
import com.scutmmq.entity.Result;
import com.scutmmq.utils.AliyunOSSOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ImageUploadSecurityTest {

    @Mock
    private AliyunOSSOperator aliyunOSSOperator;

    @InjectMocks
    private ImageController imageController;

    @Test
    void testUploadEmptyFileFails() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        Result result = imageController.uploadImage(emptyFile);
        assertEquals(0, result.getCode());
        assertEquals("上传文件不能为空", result.getMsg());
    }

    @Test
    void testUploadDisallowedExtensionFails() throws Exception {
        byte[] content = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        MockMultipartFile file = new MockMultipartFile("file", "malicious.exe", "application/octet-stream", content);
        Result result = imageController.uploadImage(file);
        assertEquals(0, result.getCode());
        assertTrue(result.getMsg().contains("不支持的文件类型"));
    }

    @Test
    void testUploadDisguisedShellScriptFailsMagicNumberCheck() throws Exception {
        // 文件名是 shell.png，但内容是纯文本 <?php phpinfo(); ?>
        byte[] phpPayload = "<?php phpinfo(); ?>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "shell.png", "image/png", phpPayload);
        Result result = imageController.uploadImage(file);
        assertEquals(0, result.getCode());
        assertTrue(result.getMsg().contains("非法文件头"));
    }

    @Test
    void testUploadMismatchedExtensionAndMagicBytesFails() throws Exception {
        // 文件名是 avatar.jpg，但内容其实是 PNG 头部
        byte[] validPng = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", validPng);

        Result result = imageController.uploadImage(file);
        assertEquals(0, result.getCode());
        assertTrue(result.getMsg().contains("非法文件头"));
    }

    @Test
    void testUploadValidPngSuccess() throws Exception {
        // 标准 PNG header: 89 50 4E 47 0D 0A 1A 0A
        byte[] validPng = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", validPng);

        when(aliyunOSSOperator.upload(any(byte[].class), anyString())).thenReturn("http://oss.example.com/avatar.png");

        Result result = imageController.uploadImage(file);
        assertEquals(1, result.getCode());
        assertEquals("http://oss.example.com/avatar.png", result.getData());
    }

    @Test
    void testUploadValidJpegSuccess() throws Exception {
        // 标准 JPEG header: FF D8 FF E0
        byte[] validJpeg = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01
        };
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", validJpeg);

        when(aliyunOSSOperator.upload(any(byte[].class), anyString())).thenReturn("http://oss.example.com/photo.jpg");

        Result result = imageController.uploadImage(file);
        assertEquals(1, result.getCode());
        assertEquals("http://oss.example.com/photo.jpg", result.getData());
    }

    @Test
    void testUploadOversizedFileFails() throws Exception {
        byte[] bigContent = new byte[6 * 1024 * 1024]; // 6MB > 5MB
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", bigContent);
        Result result = imageController.uploadImage(file);
        assertEquals(0, result.getCode());
        assertEquals("文件大小不能超过 5MB", result.getMsg());
    }
}
