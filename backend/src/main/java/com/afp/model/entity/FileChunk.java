package com.afp.model.entity;

import com.afp.model.enums.ChunkStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件切片表实体
 */
@Entity
@Table(name = "file_chunk",
        uniqueConstraints = @UniqueConstraint(columnNames = {"file_id", "chunk_index"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chunk_id", nullable = false, unique = true, length = 64)
    private String chunkId;

    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_size", nullable = false)
    @Builder.Default
    private Long chunkSize = 0L;

    @Column(name = "chunk_md5", length = 32)
    private String chunkMd5;

    @Column(name = "store_path", length = 500)
    private String storePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChunkStatus status = ChunkStatus.PENDING;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
