package com.afp.async;

import com.afp.model.dto.ChunkProgressEvent;
import com.afp.model.entity.FileChunk;
import com.afp.model.enums.ChunkStatus;
import com.afp.repository.FileChunkRepository;
import com.afp.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 单切片异步处理任务 — 写磁盘 + 写 DB + SSE 通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkProcessTask {

    private final FileStorage fileStorage;
    private final FileChunkRepository chunkRepository;
    private final SseEmitterRegistry sseRegistry;

    /**
     * 异步处理单个切片
     * @param fileId     文件ID
     * @param chunkIndex 切片序号
     * @param chunkData  切片数据
     * @param totalChunks 切片总数（用于进度计算）
     */
    @Async("chunkExecutor")
    public CompletableFuture<Void> process(String fileId, int chunkIndex, byte[] chunkData, int totalChunks) {
        String chunkId = UUID.randomUUID().toString();
        log.info("开始处理切片: fileId={}, chunkIndex={}, chunkId={}", fileId, chunkIndex, chunkId);

        try {
            // 1. 写磁盘
            String storePath = fileStorage.store(fileId, chunkIndex, chunkData);
            log.info("切片写入磁盘完成: chunkIndex={}, path={}", chunkIndex, storePath);

            // 2. 建 DB 记录
            FileChunk chunk = FileChunk.builder()
                    .chunkId(chunkId)
                    .fileId(fileId)
                    .chunkIndex(chunkIndex)
                    .chunkSize((long) chunkData.length)
                    .storePath(storePath)
                    .status(ChunkStatus.COMPLETED)
                    .build();
            chunkRepository.save(chunk);
            log.info("切片入库完成: chunkIndex={}", chunkIndex);

            // 3. SSE 推送
            ChunkProgressEvent event = ChunkProgressEvent.builder()
                    .fileId(fileId)
                    .chunkId(chunkId)
                    .chunkIndex(chunkIndex)
                    .status(ChunkStatus.COMPLETED.name())
                    .totalChunks(totalChunks)
                    .message("切片 " + chunkIndex + " 处理完成")
                    .build();
            sseRegistry.send(fileId, event);
            log.info("SSE 推送完成: chunkIndex={}", chunkIndex);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("切片处理失败: fileId={}, chunkIndex={}", fileId, chunkIndex, e);

            // 失败时建记录 + 推送错误事件
            try {
                FileChunk chunk = FileChunk.builder()
                        .chunkId(chunkId)
                        .fileId(fileId)
                        .chunkIndex(chunkIndex)
                        .status(ChunkStatus.FAILED)
                        .errorMsg(e.getMessage())
                        .build();
                chunkRepository.save(chunk);
            } catch (Exception dbEx) {
                log.error("失败切片入库也失败: chunkIndex={}", chunkIndex, dbEx);
            }

            ChunkProgressEvent event = ChunkProgressEvent.builder()
                    .fileId(fileId)
                    .chunkId(chunkId)
                    .chunkIndex(chunkIndex)
                    .status(ChunkStatus.FAILED.name())
                    .totalChunks(totalChunks)
                    .message("切片 " + chunkIndex + " 处理失败: " + e.getMessage())
                    .build();
            sseRegistry.sendError(fileId, event);

            return CompletableFuture.completedFuture(null);
        }
    }
}
