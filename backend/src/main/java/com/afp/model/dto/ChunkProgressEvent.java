package com.afp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 进度事件 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkProgressEvent {

    private String fileId;
    private String chunkId;
    private Integer chunkIndex;
    private String chunkMd5;
    private String status;
    private Integer totalChunks;
    private Integer completedChunks;
    private Integer failedChunks;
    private String fileMd5;
    private Boolean verified;
    private String message;
}
