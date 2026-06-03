package com.afp.async;

import com.afp.model.dto.ChunkProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 连接注册中心 — 管理前端长连接
 * 支持事件缓存：前端连接晚到时可补发事件
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 分钟超时
    private static final int MAX_CACHED_EVENTS = 50;        // 最多缓存 50 个事件

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    // ✅ 新增：事件缓存（按 fileId 存储）
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Object>> eventCache = new ConcurrentHashMap<>();

    /**
     * 注册新的 SSE 连接
     */
    public SseEmitter register(String fileId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.put(fileId, emitter);
        
        log.info("SSE 连接建立: fileId={}", fileId);
        
        // ✅ 关键：连接建立后，立即推送之前缓存的事件
        List<Object> cachedEvents = eventCache.get(fileId);
        if (cachedEvents != null && !cachedEvents.isEmpty()) {
            log.info("SSE 补发 {} 个缓存事件: fileId={}", cachedEvents.size(), fileId);
            for (Object event : cachedEvents) {
                sendEvent(emitter, fileId, event);
            }
            // 补发完成后清除缓存（避免重复补发）
            eventCache.remove(fileId);
        }

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
        // ✅ 先缓存事件（即使没有连接，也存起来）
        cacheEvent(fileId, event);
        
        SseEmitter emitter = emitters.get(fileId);
        if (emitter != null) {
            sendEvent(emitter, fileId, event);
        } else {
            log.debug("SSE 客户端未连接，事件已缓存: fileId={}, chunkIndex={}", fileId, event.getChunkIndex());
        }
    }

    /**
     * 发送文件完成事件并关闭连接
     */
    public void sendCompleteAndClose(String fileId, ChunkProgressEvent event) {
        // 缓存完成事件
        cacheEvent(fileId, event);
        
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
        // 完成后清除事件缓存（可选）
        eventCache.remove(fileId);
    }

    /**
     * 发送切片失败事件
     */
    public void sendError(String fileId, ChunkProgressEvent event) {
        // 缓存错误事件
        cacheEvent(fileId, event);
        
        SseEmitter emitter = emitters.get(fileId);
        if (emitter != null) {
            sendEvent(emitter, fileId, event, "chunk-error");
        } else {
            log.debug("SSE 客户端未连接，错误事件已缓存: fileId={}", fileId);
        }
    }
    
    /**
     * 缓存事件
     */
    private void cacheEvent(String fileId, Object event) {
        CopyOnWriteArrayList<Object> cache = eventCache.computeIfAbsent(fileId, k -> new CopyOnWriteArrayList<>());
        cache.add(event);
        
        // 限制缓存大小，避免内存泄漏
        while (cache.size() > MAX_CACHED_EVENTS) {
            cache.remove(0);
        }
    }
    
    /**
     * 推送事件（完成事件，默认用 chunk-completed）
     */
    private void sendEvent(SseEmitter emitter, String fileId, Object event) {
        sendEvent(emitter, fileId, event, "chunk-completed");
    }
    
    /**
     * 推送事件（可指定事件名）
     */
    private void sendEvent(SseEmitter emitter, String fileId, Object event, String eventName) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(event));
        } catch (IOException e) {
            log.warn("SSE 推送失败: fileId={}, event={}", fileId, eventName);
            emitters.remove(fileId);
        }
    }
    
    /**
     * 清除指定文件的事件缓存
     */
    public void clearCache(String fileId) {
        eventCache.remove(fileId);
    }
}