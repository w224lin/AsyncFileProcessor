package com.afp.async;

import com.afp.chunker.FileChunker;
import com.afp.model.dto.ChunkProgressEvent;
import com.afp.model.entity.FileChunk;
import com.afp.model.entity.FileRecord;
import com.afp.model.enums.ChunkStatus;
import com.afp.model.enums.FileStatus;
import com.afp.repository.FileChunkRepository;
import com.afp.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 文件异步处理器 — 独立类，避免 @Async 自调用失效
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAsyncProcessor {

    private static final int TOTAL_CHUNKS = 10;

    private final FileChunker fileChunker;
    private final ChunkProcessTask chunkProcessTask;
    private final FileRecordRepository fileRecordRepository;
    private final FileChunkRepository fileChunkRepository;
    private final SseEmitterRegistry sseRegistry;

    @Qualifier("chunkExecutor")
    private final ThreadPoolTaskExecutor chunkExecutor;

    /**
     * 异步处理：切片 → 并行处理各切片 → 更新文件状态
     * 独立 Bean 的方法，@Async 能被 Spring AOP 正确拦截
     */
    @Async("chunkExecutor")
    public void processAsync(String fileId, byte[] fileBytes) {
        log.info("开始异步处理: fileId={}", fileId);

        // 1. 切片
        List<byte[]> chunks = fileChunker.split(fileBytes, TOTAL_CHUNKS);
        log.info("切片完成: fileId={}, chunkCount={}", fileId, chunks.size());

        // 2. 提交 10 个切片任务到线程池并行处理
        CompletableFuture<Void>[] futures = new CompletableFuture[TOTAL_CHUNKS];
        for (int i = 0; i < TOTAL_CHUNKS; i++) {
            final int chunkIndex = i;
            // 使用 runAsync 传入 chunkExecutor，真正并行
            futures[i] = CompletableFuture.runAsync(
                    () -> chunkProcessTask.process(fileId, chunkIndex, chunks.get(chunkIndex), TOTAL_CHUNKS),
                    chunkExecutor
            );
        }

        // 3. 等待全部完成
        CompletableFuture.allOf(futures).join();
        log.info("所有切片处理完成: fileId={}", fileId);

        // 4. 统计更新
        FileRecord record = fileRecordRepository.findByFileId(fileId)
                .orElseThrow(() -> new RuntimeException("文件记录不存在: " + fileId));

        List<FileChunk> chunkList = fileChunkRepository.findByFileId(fileId);

        long completedCount = chunkList.stream()
                .filter(c -> c.getStatus() == ChunkStatus.COMPLETED)
                .count();
        long failedCount = chunkList.stream()
                .filter(c -> c.getStatus() == ChunkStatus.FAILED)
                .count();

        FileStatus finalStatus = failedCount == 0 ? FileStatus.COMPLETED : FileStatus.PARTIAL_FAILED;

        record.setCompletedChunks((int) completedCount);
        record.setFailedChunks((int) failedCount);
        record.setStatus(finalStatus);
        fileRecordRepository.save(record);

        // 5. SSE 推送文件完成事件
        String fileMd5 = record.getFileMd5();
        boolean verified = failedCount == 0;

        ChunkProgressEvent event = ChunkProgressEvent.builder()
                .fileId(fileId)
                .status(finalStatus.name())
                .totalChunks(TOTAL_CHUNKS)
                .completedChunks((int) completedCount)
                .failedChunks((int) failedCount)
                .fileMd5(fileMd5)
                .verified(verified)
                .message(failedCount == 0 ? "所有切片处理完成" : "处理完成，" + failedCount + " 个切片失败")
                .build();
        sseRegistry.sendCompleteAndClose(fileId, event);
        log.info("文件处理全部完成: fileId={}, status={}, md5={}, verified={}", fileId, finalStatus, fileMd5, verified);
    }
}
