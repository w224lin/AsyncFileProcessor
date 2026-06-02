package com.afp.controller;

import com.afp.async.SseEmitterRegistry;
import com.afp.common.Result;
import com.afp.model.entity.FileChunk;
import com.afp.model.entity.FileRecord;
import com.afp.repository.FileChunkRepository;
import com.afp.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件进度控制器 — SSE 推送 + 状态查询
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileProgressController {

    private final SseEmitterRegistry sseRegistry;
    private final FileRecordRepository fileRecordRepository;
    private final FileChunkRepository chunkRepository;

    /**
     * SSE 进度推送接口
     */
    @GetMapping(value = "/{fileId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String fileId) {
        log.info("SSE 连接建立: fileId={}", fileId);
        return sseRegistry.register(fileId);
    }

    /**
     * 状态查询接口（SSE 断线兜底）
     */
    @GetMapping("/{fileId}/status")
    public Result<Map<String, Object>> getStatus(@PathVariable String fileId) {
        FileRecord record = fileRecordRepository.findByFileId(fileId)
                .orElse(null);
        if (record == null) {
            return Result.error(404, "文件不存在: " + fileId);
        }

        List<FileChunk> chunks = chunkRepository.findByFileId(fileId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileId", record.getFileId());
        data.put("fileName", record.getFileName());
        data.put("fileSize", record.getFileSize());
        data.put("status", record.getStatus().name());
        data.put("totalChunks", record.getTotalChunks());
        data.put("completedChunks", record.getCompletedChunks());
        data.put("failedChunks", record.getFailedChunks());
        data.put("chunks", chunks.stream().map(c -> {
            Map<String, Object> chunkInfo = new LinkedHashMap<>();
            chunkInfo.put("chunkIndex", c.getChunkIndex());
            chunkInfo.put("chunkId", c.getChunkId());
            chunkInfo.put("chunkSize", c.getChunkSize());
            chunkInfo.put("status", c.getStatus().name());
            return chunkInfo;
        }).collect(Collectors.toList()));

        return Result.success(data);
    }
}
