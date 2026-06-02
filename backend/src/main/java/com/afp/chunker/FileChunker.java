package com.afp.chunker;

import java.util.List;

/**
 * 文件切片器接口
 */
public interface FileChunker {

    /**
     * 将文件切分为多个切片
     * @param fileBytes 文件字节数组
     * @param count     切片数量
     * @return 切片列表，每个元素为一个切片的字节数组
     */
    List<byte[]> split(byte[] fileBytes, int count);
}