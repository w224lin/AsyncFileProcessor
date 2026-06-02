package com.afp.async;

import com.afp.model.dto.ChunkProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接注册中心 — 管理前端长连接
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 分钟超时

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 注册新的 SSE 连接
     */
    public SseEmitter register(String fileId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.put(fileId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE 连接正常关闭: fileId={}", fileId);
            emitters.remove(fileId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE 连接超时: fileId={}", fileId);
            emitters.remove(fileId);
        });

        emitter.onError(e -> {
            log.warn("SSE 连接异常: fileId={}", fileId, e);
            emitters.remove(fileId);
        });

        return emitter;
    }

    /**
     * 向指定文件的前端连接推送切片完成事件
     */
    public void send(String fileId, ChunkProgressEvent event) {
        SseEmitter emitter = emitters.get(fileId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("chunk-completed")
                        .data(event));
            } catch (IOException e) {
                log.warn("SSE 推送失败: fileId={}, chunkIndex={}", fileId, event.getChunkIndex());
                emitters.remove(fileId);
            }
        }
    }

    /**
     * 发送文件完成事件并关闭连接
     */
    public void sendCompleteAndClose(String fileId, ChunkProgressEvent event) {
        SseEmitter emitter = emitters.get(fileId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("file-completed")
                        .data(event));
                emitter.complete();
            } catch (IOException e) {
                log.warn("SSE 完成事件推送失败: fileId={}", fileId);
            } finally {
                emitters.remove(fileId);
            }
        }
    }

    /**
     * 发送切片失败事件
     */
    public void sendError(String fileId, ChunkProgressEvent event) {
        SseEmitter emitter = emitters.get(fileId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("chunk-error")
                        .data(event));
            } catch (IOException e) {
                log.warn("SSE 错误事件推送失败: fileId={}", fileId);
            }
        }
    }
}
