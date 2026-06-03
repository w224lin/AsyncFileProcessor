package com.afp.repository;

import com.afp.model.entity.FileChunk;
import com.afp.model.enums.ChunkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文件切片数据访问层
 */
@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {

    /**
     * 根据切片业务ID查找
     */
    Optional<FileChunk> findByChunkId(String chunkId);

    /**
     * 查找某文件的所有切片
     */
    List<FileChunk> findByFileId(String fileId);

    long countByFileIdAndStatus(String fileId, ChunkStatus status);

    /**
     * 查找某文件的所有切片，按切片序号升序
     */
    List<FileChunk> findByFileIdOrderByChunkIndexAsc(String fileId);

    /**
     * 更新切片状态
     */
    @Modifying
    @Query("UPDATE FileChunk c SET c.status = :status, c.errorMsg = :errorMsg WHERE c.chunkId = :chunkId")
    void updateChunkStatus(@Param("chunkId") String chunkId,
                           @Param("status") ChunkStatus status,
                           @Param("errorMsg") String errorMsg);
}
