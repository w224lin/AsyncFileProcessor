package com.afp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    private String fileId;
    private Integer totalChunks;
    private String fileMd5;
    private String sseEndpoint;
    private String statusEndpoint;
}
