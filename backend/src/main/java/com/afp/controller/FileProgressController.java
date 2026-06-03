package com.afp.controller;

import com.afp.async.SseEmitterRegistry;
import com.afp.common.Result;
import com.afp.model.dto.VerifyResponse;
import com.afp.model.entity.FileChunk;
import com.afp.model.entity.FileRecord;
import com.afp.repository.FileChunkRepository;
import com.afp.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
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
        data.put("fileMd5", record.getFileMd5());
        data.put("status", record.getStatus().name());
        data.put("totalChunks", record.getTotalChunks());
        data.put("completedChunks", record.getCompletedChunks());
        data.put("failedChunks", record.getFailedChunks());
        data.put("chunks", chunks.stream().map(c -> {
            Map<String, Object> chunkInfo = new LinkedHashMap<>();
            chunkInfo.put("chunkIndex", c.getChunkIndex());
            chunkInfo.put("chunkId", c.getChunkId());
            chunkInfo.put("chunkSize", c.getChunkSize());
            chunkInfo.put("chunkMd5", c.getChunkMd5());
            chunkInfo.put("status", c.getStatus().name());
            return chunkInfo;
        }).collect(Collectors.toList()));

        return Result.success(data);
    }

    /**
     * 文件完整性校验接口
     * 从磁盘重新读取所有切片，合并计算 MD5，与原始 fileMd5 比对
     */
    @GetMapping("/{fileId}/verify")
    public Result<VerifyResponse> verify(@PathVariable String fileId) {
        FileRecord record = fileRecordRepository.findByFileId(fileId)
                .orElse(null);
        if (record == null) {
            return Result.error(404, "文件不存在: " + fileId);
        }

        String originalMd5 = record.getFileMd5();
        List<FileChunk> chunks = chunkRepository.findByFileIdOrderByChunkIndexAsc(fileId);

        List<Integer> failedChunks = new ArrayList<>();
        String reCalcMd5;

        try {
            // 读取所有切片并合并计算 MD5
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (FileChunk chunk : chunks) {
                try {
                    byte[] chunkData = Files.readAllBytes(Path.of(chunk.getStorePath()));
                    md.update(chunkData);
                } catch (IOException e) {
                    log.warn("切片文件读取失败: chunkIndex={}, path={}", chunk.getChunkIndex(), chunk.getStorePath());
                    failedChunks.add(chunk.getChunkIndex());
                }
            }
            reCalcMd5 = HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 算法不可用", e);
            return Result.error(500, "MD5 校验算法不可用");
        }

        boolean verified = originalMd5 != null && originalMd5.equals(reCalcMd5) && failedChunks.isEmpty();

        String message;
        if (verified) {
            message = "文件完整性校验通过";
        } else if (!failedChunks.isEmpty()) {
            message = "文件完整性校验失败：" + failedChunks.size() + " 个切片无法读取";
        } else {
            message = "文件完整性校验失败：MD5 不匹配";
        }

        VerifyResponse response = VerifyResponse.builder()
                .fileId(fileId)
                .verified(verified)
                .fileMd5(originalMd5)
                .reCalcMd5(reCalcMd5)
                .failedChunks(failedChunks.isEmpty() ? null : failedChunks)
                .message(message)
                .build();

        log.info("文件校验完成: fileId={}, verified={}", fileId, verified);
        return Result.success(response);
    }
}
