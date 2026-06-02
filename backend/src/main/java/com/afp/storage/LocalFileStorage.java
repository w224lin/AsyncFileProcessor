package com.afp.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件系统存储实现
 */
@Component
public class LocalFileStorage implements FileStorage {

    @Value("${afp.storage.chunk-dir}")
    private String chunkDir;

    @Override
    public String store(String fileId, int chunkIndex, byte[] chunkData) {
        try {
            Path dir = Paths.get(chunkDir, fileId);
            Files.createDirectories(dir);

            String fileName = "chunk_" + chunkIndex + ".bin";
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, chunkData);

            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("切片写入磁盘失败: fileId=" + fileId + ", chunkIndex=" + chunkIndex, e);
        }
    }
}
