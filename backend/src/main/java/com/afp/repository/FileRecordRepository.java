package com.afp.repository;

import com.afp.model.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文件记录数据访问层
 */
@Repository
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    /**
     * 根据业务文件ID查找
     */
    Optional<FileRecord> findByFileId(String fileId);

    // ✅ 新增：根据 MD5 查询文件（用于秒传去重）
    Optional<FileRecord> findByFileMd5(String fileMd5);

    /**
     * 根据用户ID查找所有文件
     */
    java.util.List<FileRecord> findByUserId(String userId);
}
