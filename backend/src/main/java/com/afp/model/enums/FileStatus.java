package com.afp.model.enums;

/**
 * 文件整体状态枚举
 */
public enum FileStatus {
    PROCESSING,        // 处理中
    COMPLETED,         // 全部完成
    PARTIAL_FAILED,    // 部分失败
    FAILED             // 全部失败
}
