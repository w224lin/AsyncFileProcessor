package com.afp.service.impl;

import com.afp.chunker.FileChunker;
import com.afp.async.ChunkProcessTask;
import com.afp.async.SseEmitterRegistry;
import com.afp.model.dto.ChunkProgressEvent;
import com.afp.model.dto.UploadResponse;
import com.afp.model.entity.FileRecord;
import com.afp.model.enums.FileStatus;
import com.afp.repository.FileRecordRepository;
import com.afp.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 文件服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final int TOTAL_CHUNKS = 10;

    private final FileRecordRepository fileRecordRepository;
    private final FileChunker fileChunker;
    private final ChunkProcessTask chunkProcessTask;
    private final SseEmitterRegistry sseRegistry;

    @Value("${afp.storage.chunk-dir}")
    private String chunkDir;

    @Override
    public UploadResponse upload(String userId, String fileName, byte[] fileBytes) {
        String fileId = UUID.randomUUID().toString();
        long fileSize = fileBytes.length;

        // 1. 创建文件记录（状态 = PROCESSING）
        FileRecord record = FileRecord.builder()
                .fileId(fileId)
                .userId(userId)
                .fileName(fileName)
                .fileSize(fileSize)
                .totalChunks(TOTAL_CHUNKS)
                .status(FileStatus.PROCESSING)
                .storePath(chunkDir + "/" + fileId)
                .build();
        fileRecordRepository.save(record);
        log.info("文件记录已创建: fileId={}, fileName={}, size={}", fileId, fileName, fileSize);

        // 2. 触发异步处理（不阻塞主线程）
        processAsync(fileId, fileBytes);

        // 3. 立即返回
        return UploadResponse.builder()
                .fileId(fileId)
                .totalChunks(TOTAL_CHUNKS)
                .sseEndpoint("/api/files/" + fileId + "/progress")
                .statusEndpoint("/api/files/" + fileId + "/status")
                .build();
    }

    /**
     * 异步处理：切片 → 并行处理各切片 → 更新文件状态
     */
    @Async("chunkExecutor")
    public void processAsync(String fileId, byte[] fileBytes) {
        log.info("开始异步处理: fileId={}", fileId);

        // 1. 切片
        List<byte[]> chunks = fileChunker.split(fileBytes, TOTAL_CHUNKS);
        log.info("切片完成: fileId={}, chunkCount={}", fileId, chunks.size());

        // 2. 提交 10 个切片任务并行处理（@Async 自动提交到 chunkExecutor）
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[TOTAL_CHUNKS];
        for (int i = 0; i < TOTAL_CHUNKS; i++) {
            final int chunkIndex = i;
            futures[i] = chunkProcessTask.process(
                    fileId, chunkIndex, chunks.get(chunkIndex), TOTAL_CHUNKS);
        }

        // 3. 等待全部完成
        CompletableFuture.allOf(futures).join();
        log.info("所有切片处理完成: fileId={}", fileId);

        // 4. 更新文件记录状态并推送完成事件
        FileRecord record = fileRecordRepository.findByFileId(fileId)
                .orElseThrow(() -> new RuntimeException("文件记录不存在: " + fileId));

        // 统计
        long completedCount = futures.length; // 简单 demo，后续迭代细化

        record.setCompletedChunks((int) completedCount);
        record.setStatus(FileStatus.COMPLETED);
        fileRecordRepository.save(record);

        // 5. SSE 推送文件完成事件
        ChunkProgressEvent event = ChunkProgressEvent.builder()
                .fileId(fileId)
                .status(FileStatus.COMPLETED.name())
                .totalChunks(TOTAL_CHUNKS)
                .completedChunks(TOTAL_CHUNKS)
                .failedChunks(0)
                .message("所有切片处理完成")
                .build();
        sseRegistry.sendCompleteAndClose(fileId, event);
        log.info("文件处理全部完成: fileId={}", fileId);
    }
}
