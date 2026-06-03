# V1.2 文件完整性校验 — 迭代需求文档

> 版本：v1.2 | 日期：2026-06-03 | 状态：待确认
> 基于 PRD v1.0 第 9 节迭代规划

---

## 1. 迭代目标

在 V1.1 前后端联调完成的基础上，增加文件完整性校验能力：
- 后端计算文件级和切片级 MD5
- 前端可感知校验结果
- 提供独立的文件校验接口

---

## 2. 功能列表

| 编号 | 功能 | 优先级 | 说明 |
|---|---|---|---|
| F10 | 文件级 MD5 | P0 | 上传时计算整个文件的 MD5，写入 file_record，响应中返回 |
| F11 | 切片级 MD5 | P0 | 每切片独立计算 MD5，写入 file_chunk，SSE 事件中携带 |
| F12 | 校验结果前端可见 | P0 | 上传响应 + SSE 完成事件 + 状态查询 均返回 MD5 信息 |
| F13 | 独立校验接口 | P0 | GET /api/files/{fileId}/verify，服务端重算 MD5 并比对，返回 pass/fail |
| F14 | 前端校验展示 | P0 | 完成弹窗显示文件 MD5 和校验通过/失败状态 |

---

## 3. 用户流程

### 3.1 主线 — 上传并校验

```
前端                              后端
 │                                 │
 │  POST /api/files/upload         │
 │  (userId, file)                 │
 │ ──────────────────────────────> │
 │                                 │ ① 计算文件 MD5（fileMd5）
 │  200 OK                         │ ② 响应中增加 fileMd5
 │  { fileId, fileMd5, ... }       │
 │ <────────────────────────────── │
 │                                 │
 │  GET /api/files/{fileId}/progress (SSE)
 │ ──────────────────────────────> │
 │                                 │
 │  event: chunk-completed         │
 │  { chunkIndex:0, chunkMd5:...}  │  每片完成后携带 chunkMd5
 │ <────────────────────────────── │
 │  ... (共 10 个) ...             │
 │                                 │
 │  event: file-completed          │
 │  { fileMd5, chunkMd5s: [...],   │
 │    verified: true }             │  服务端自动校验
 │ <────────────────────────────── │
 │                                 │
 │  前端可选：主动调 verify 接口    │
 │  GET /api/files/{fileId}/verify │
 │ ──────────────────────────────> │
 │                                 │ ① 合并所有切片重算 MD5
 │  { verified: true/false,        │ ② 与原始 fileMd5 比对
 │    fileMd5, reCalcMd5 }         │
 │ <────────────────────────────── │
 │                                 │
 ▼                                 ▼
```

### 3.2 校验失败场景

```
前端                              后端
 │                                 │
 │  GET /api/files/{fileId}/verify │
 │ ──────────────────────────────> │
 │                                 │  合并切片 → 计算 MD5
 │                                 │  发现与 fileMd5 不一致！
 │  200 OK                         │
 │  { verified: false,             │
 │    fileMd5: "abc...",           │
 │    reCalcMd5: "def...",         │
 │    failedChunks: [3, 7] }       │
 │ <────────────────────────────── │
 │                                 │
 │  前端弹窗显示：                  │
 │  ⚠️ 文件完整性校验失败！         │
 │  原始 MD5: abc...               │
 │  当前 MD5: def...               │
 │  失败切片: 3, 7                 │
 │                                 │
 ▼                                 ▼
```

---

## 4. 数据库变更

### 4.1 file_record 表新增字段

```sql
ALTER TABLE file_record
    ADD COLUMN file_md5 VARCHAR(32) COMMENT '文件整体 MD5 值' AFTER file_size;
```

### 4.2 file_chunk 表新增字段

```sql
ALTER TABLE file_chunk
    ADD COLUMN chunk_md5 VARCHAR(32) COMMENT '切片 MD5 值' AFTER chunk_size;
```

---

## 5. 接口变更

### 5.1 上传响应（变更）

```json
POST /api/files/upload
Response 200:
{
  "code": 200,
  "message": "文件已接收，异步处理中",
  "data": {
    "fileId": "550e8400-...",
    "totalChunks": 10,
    "fileMd5": "d41d8cd98f00b204e9800998ecf8427e",       // 新增
    "sseEndpoint": "/api/files/.../progress",
    "statusEndpoint": "/api/files/.../status"
  }
}
```

### 5.2 SSE 切片完成事件（变更）

```
event: chunk-completed
data: {
  "chunkIndex": 0,
  "chunkId": "...",
  "status": "COMPLETED",
  "totalChunks": 10,
  "chunkMd5": "abc123..."                                 // 新增
}
```

### 5.3 SSE 文件完成事件（变更）

```
event: file-completed
data: {
  "fileId": "...",
  "status": "COMPLETED",
  "totalChunks": 10,
  "completedChunks": 10,
  "failedChunks": 0,
  "message": "所有切片处理完成",
  "fileMd5": "d41d8cd...",                                 // 新增
  "verified": true                                         // 新增
}
```

### 5.4 状态查询响应（变更）

```json
GET /api/files/{fileId}/status
Response 200:
{
  "code": 200,
  "data": {
    "fileId": "...",
    "fileMd5": "d41d8cd...",                               // 新增
    ...
    "chunks": [
      { "chunkIndex": 0, "chunkMd5": "...", ... }          // 每片新增 chunkMd5
    ]
  }
}
```

### 5.5 新增：文件完整性校验接口

```
GET /api/files/{fileId}/verify

Response 200（校验通过）:
{
  "code": 200,
  "data": {
    "fileId": "550e8400-...",
    "verified": true,
    "fileMd5": "d41d8cd98f00b204e9800998ecf8427e",
    "reCalcMd5": "d41d8cd98f00b204e9800998ecf8427e",
    "message": "文件完整性校验通过"
  }
}

Response 200（校验失败）:
{
  "code": 200,
  "data": {
    "fileId": "550e8400-...",
    "verified": false,
    "fileMd5": "d41d8cd98f00b204e9800998ecf8427e",
    "reCalcMd5": "e99a18c428cb38d5f260853678922e03",
    "failedChunks": [3, 7],
    "message": "文件完整性校验失败：2 个切片数据损坏"
  }
}
```

---

## 6. 技术实现要点

### 6.1 MD5 计算

- 使用 Java `java.security.MessageDigest`（JDK 内置，无需额外依赖）
- 算法：`MD5`，输出 32 位十六进制字符串
- 计算时机：
  - **文件级**：`FileServiceImpl.upload()` 中，接收到 `fileBytes` 后立即计算
  - **切片级**：`ChunkProcessTask.process()` 中，拿到 `chunkData` 后立即计算

### 6.2 verify 接口逻辑

```
1. 从 file_chunk 表中查所有切片（按 chunk_index 排序）
2. 逐片从磁盘读取原始字节数据
3. 拼接所有切片 → 计算合并后的 MD5
4. 与 file_record.file_md5 比对
5. 返回 verified: true/false
```

### 6.3 影响范围

| 层级 | 文件 | 变更类型 |
|---|---|---|
| model/entity | FileRecord.java | 新增 fileMd5 字段 |
| model/entity | FileChunk.java | 新增 chunkMd5 字段 |
| model/dto | UploadResponse.java | 新增 fileMd5 字段 |
| model/dto | ChunkProgressEvent.java | 新增 chunkMd5 / fileMd5 / verified 字段 |
| service | FileService.java | upload 返回包含 MD5 |
| service/impl | FileServiceImpl.java | 计算文件 MD5 |
| async | ChunkProcessTask.java | 计算切片 MD5 |
| async | FileAsyncProcessor.java | 完成时校验 MD5 |
| controller | FileProgressController.java | 新增 verify 端点 + 更新 status 响应 |
| 前端 | fileApi.js | 新增 fetchVerify() |
| 前端 | useSseProgress.js | 解析 MD5 相关字段 |
| 前端 | App.jsx / CompletionModal.jsx | 展示校验结果 |
| DB | file_record / file_chunk | 新增列 |

---

## 7. 验收标准

| 编号 | 验收项 | 标准 |
|---|---|---|
| A12 | 上传返回 MD5 | POST /upload 响应中包含 fileMd5，为有效 32 位 MD5 |
| A13 | 切片携带 MD5 | 每个 chunk-completed SSE 事件包含 chunkMd5 |
| A14 | 完成事件含校验结果 | file-completed 事件中 verified 字段正确反映校验状态 |
| A15 | 校验接口通过 | 正常文件调用 verify → verified: true |
| A16 | 校验接口失败 | 人为修改切片文件后 → verified: false + failedChunks 非空 |
| A17 | 前端展示 | 完成弹窗显示文件 MD5 + 校验通过/失败标识 |
| A18 | 状态查询含 MD5 | GET /status 返回 fileMd5 和每切片的 chunkMd5 |

---

> **审批状态：待用户确认**
> 确认后进入 V1.2 开发阶段
