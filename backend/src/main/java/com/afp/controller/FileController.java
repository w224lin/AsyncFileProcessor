package com.afp.controller;

import com.afp.common.Result;
import com.afp.model.dto.UploadResponse;
import com.afp.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 文件上传接口
     */
    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("userId") String userId,
            @RequestParam("file") MultipartFile file) {

        if (userId == null || userId.isBlank()) {
            return Result.error(400, "userId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        try {
            byte[] fileBytes = file.getBytes();
            UploadResponse response = fileService.upload(userId, file.getOriginalFilename(), fileBytes);
            return Result.success("文件已接收，异步处理中", response);
        } catch (IOException e) {
            log.error("读取文件失败", e);
            return Result.error(500, "读取文件失败: " + e.getMessage());
        }
    }
}
