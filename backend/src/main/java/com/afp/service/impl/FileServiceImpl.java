package com.afp.service.impl;

import com.afp.async.FileAsyncProcessor;
import com.afp.model.dto.UploadResponse;
import com.afp.model.entity.FileRecord;
import com.afp.model.enums.FileStatus;
import com.afp.repository.FileRecordRepository;
import com.afp.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件服务实现
 * 支持 MD5 去重 + 秒传功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final int TOTAL_CHUNKS = 10;

    private final FileRecordRepository fileRecordRepository;
    private final FileAsyncProcessor asyncProcessor;

    @Value("${afp.storage.chunk-dir}")
    private String chunkDir;

    @Override
    public UploadResponse upload(String userId, String fileName, byte[] fileBytes) {
        long fileSize = fileBytes.length;
        
        // 1. 计算文件级 MD5
        String fileMd5 = calcMd5(fileBytes);
        log.info("计算文件 MD5: {}", fileMd5);

        // 2. 检查是否已存在相同 MD5 的文件（秒传）
        Optional<FileRecord> existingRecord = fileRecordRepository.findByFileMd5(fileMd5);
        
        if (existingRecord.isPresent()) {
            FileRecord existing = existingRecord.get();
            log.info("✅ 秒传命中！文件已存在: fileId={}, fileName={}, userId={}", 
                    existing.getFileId(), existing.getFileName(), existing.getUserId());
            
            // 即使文件已存在，也记录一下是谁在什么时间上传的（可选）
            // 这里只返回已有文件的信息，不创建新记录
            
            return UploadResponse.builder()
                    .fileId(existing.getFileId())
                    .totalChunks(existing.getTotalChunks())
                    .fileMd5(existing.getFileMd5())
                    .sseEndpoint("/api/files/" + existing.getFileId() + "/progress")
                    .statusEndpoint("/api/files/" + existing.getFileId() + "/status")
                    .build();
        }
        
        // 3. 文件不存在，正常处理
        log.info("📝 新文件，开始处理: fileName={}, size={}, md5={}", fileName, fileSize, fileMd5);
        
        String fileId = UUID.randomUUID().toString();

        // 4. 创建文件记录（状态 = PROCESSING）
        FileRecord record = FileRecord.builder()
                .fileId(fileId)
                .userId(userId)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileMd5(fileMd5)
                .totalChunks(TOTAL_CHUNKS)
                .status(FileStatus.PROCESSING)
                .storePath(chunkDir + "/" + fileId)
                .build();
        fileRecordRepository.save(record);
        log.info("文件记录已创建: fileId={}, fileName={}, size={}, md5={}", fileId, fileName, fileSize, fileMd5);

        // 5. 通过独立 Bean 触发异步处理（解决 @Async 自调用失效问题）
        asyncProcessor.processAsync(fileId, fileBytes);

        // 6. 立即返回
        return UploadResponse.builder()
                .fileId(fileId)
                .totalChunks(TOTAL_CHUNKS)
                .fileMd5(fileMd5)
                .sseEndpoint("/api/files/" + fileId + "/progress")
                .statusEndpoint("/api/files/" + fileId + "/status")
                .build();
    }

    /**
     * 计算字节数组的 MD5 值
     */
    private String calcMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }
}