package com.afp.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 固定数量切片器 — 将文件均匀切分为 N 片
 */
@Component
public class FixedCountChunker implements FileChunker {

    @Override
    public List<byte[]> split(byte[] fileBytes, int count) {
        int totalSize = fileBytes.length;
        int baseChunkSize = totalSize / count;
        List<byte[]> chunks = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int start = i * baseChunkSize;
            int end = (i == count - 1) ? totalSize : start + baseChunkSize;
            chunks.add(Arrays.copyOfRange(fileBytes, start, end));
        }

        return chunks;
    }
}