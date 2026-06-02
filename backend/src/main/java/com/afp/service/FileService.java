package com.afp.service;

import com.afp.model.dto.UploadResponse;

/**
 * 文件服务接口
 */
public interface FileService {

    /**
     * 接收文件并启动异步切片处理
     * @param userId   用户ID
     * @param fileName 原始文件名
     * @param fileBytes 文件字节数组
     * @return 上传响应（含 fileId、SSE 端点等）
     */
    UploadResponse upload(String userId, String fileName, byte[] fileBytes);
}
