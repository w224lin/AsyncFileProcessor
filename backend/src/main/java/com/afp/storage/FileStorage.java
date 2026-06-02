package com.afp.storage;

/**
 * 文件存储接口 — 定义物理存储契约
 */
public interface FileStorage {

    /**
     * 存储单个切片到磁盘
     * @param fileId     文件ID
     * @param chunkIndex 切片序号
     * @param chunkData  切片数据
     * @return 切片存储路径
     */
    String store(String fileId, int chunkIndex, byte[] chunkData);
}