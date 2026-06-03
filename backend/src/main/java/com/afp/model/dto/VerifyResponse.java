package com.afp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件完整性校验响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResponse {

    private String fileId;
    private Boolean verified;
    private String fileMd5;
    private String reCalcMd5;
    private List<Integer> failedChunks;
    private String message;
}
