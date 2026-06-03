package com.afp.model.entity;

import com.afp.model.enums.FileStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件记录表实体
 */
@Entity
@Table(name = "file_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false, unique = true, length = 64)
    private String fileId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_md5", length = 32)
    private String fileMd5;

    @Column(name = "total_chunks", nullable = false)
    @Builder.Default
    private Integer totalChunks = 10;

    @Column(name = "completed_chunks", nullable = false)
    @Builder.Default
    private Integer completedChunks = 0;

    @Column(name = "failed_chunks", nullable = false)
    @Builder.Default
    private Integer failedChunks = 0;

    @Column(name = "store_path", length = 500)
    private String storePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FileStatus status = FileStatus.PROCESSING;

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
