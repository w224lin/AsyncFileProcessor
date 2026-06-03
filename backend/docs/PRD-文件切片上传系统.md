# 文件切片上传系统 — 需求产品文档 (PRD)

> 版本：v1.0 | 日期：2026-06-02 | 状态：待审批

---

## 1. 产品概述

### 1.1 背景

构建一个后端文件切片上传系统，前端传入用户 ID 和文件，后端负责将文件切分为 10 个固定切片，异步写入磁盘和数据库，并通过 SSE 实时向前端推送每个切片的完成进度。

### 1.2 目标

- 后端接收文件后毫秒级响应，不阻塞请求线程
- 文件固定切分为 10 片，在专用线程池中并行处理
- 每完成一片，前端通过 SSE 实时收到进度通知
- 任一切片失败不影响其他切片继续处理

---

## 2. 功能列表

| 编号 | 功能 | 优先级 | 说明 |
|---|---|---|---|
| F1 | 文件上传接口 | P0 | POST 接收文件，生成 fileId，立即返回 |
| F2 | 固定 10 切片 | P0 | 后端将文件均分为 10 片（最后一片可能略大） |
| F3 | 异步并行处理 | P0 | 10 个切片在专用线程池中并行处理 |
| F4 | 切片写磁盘 | P0 | 每片独立写入本地文件系统 |
| F5 | 切片元数据入库 | P0 | 每片信息写入 MySQL file_chunk 表 |
| F6 | SSE 进度推送 | P0 | 每完成一片，推送事件给前端 |
| F7 | 状态查询接口 | P1 | 前端可主动查询文件处理进度（SSE 断线兜底） |
| F8 | 异常处理与补偿 | P0 | 单片失败不阻塞其他片，失败信息通过 SSE 通知 |
| F9 | 线程池配置 | P0 | 专用线程池，核心 4 线程，最大 8 线程 |

---

## 3. 用户流程

### 3.1 主线流程 — 文件上传并跟踪进度

```
前端                              后端
 │                                 │
 │  POST /api/files/upload         │
 │  (userId, file)                 │
 │ ──────────────────────────────> │
 │                                 │ ① 生成 fileId（UUID）
 │                                 │ ② FileRecord 入库（status=PROCESSING）
 │  200 OK                         │ ③ 提交异步任务到线程池
 │  { fileId, sseEndpoint }        │
 │ <────────────────────────────── │ ④ 主线程结束
 │                                 │
 │                                 │ ─ 异步线程池开始 ─
 │  GET /api/files/{fileId}/progress (SSE)
 │ ──────────────────────────────> │
 │                                 │ ⑤ 文件切分 10 片
 │                                 │ ⑥ 并行处理 10 片:
 │                                 │    · 写磁盘
 │                                 │    · 写 DB
 │                                 │    · SSE 推送
 │                                 │
 │  event: chunk-completed         │
 │  { chunkIndex: 0, ... }         │
 │ <────────────────────────────── │ 切片 0 完成
 │                                 │
 │  event: chunk-completed         │
 │  { chunkIndex: 1, ... }         │
 │ <────────────────────────────── │ 切片 1 完成
 │                                 │
 │  ... (共 10 个 chunk 事件) ...  │
 │                                 │
 │  event: file-completed          │
 │  { status: COMPLETED }          │
 │ <────────────────────────────── │ 全部完成
 │                                 │
 ▼                                 ▼
```

### 3.2 异常流程 — 单切片失败

```
前端                              后端
 │                                 │
 │  SSE 连接已建立                  │
 │                                 │
 │  event: chunk-completed (0)     │
 │ <────────────────────────────── │
 │  event: chunk-completed (1)     │
 │ <────────────────────────────── │
 │                                 │ 切片 2 写磁盘失败！
 │  event: chunk-error             │  ├─ chunk 2 status=FAILED
 │  { chunkIndex: 2, FAILED }      │  ├─ 记录 error_msg
 │ <────────────────────────────── │  └─ 其余切片继续处理
 │                                 │
 │  event: chunk-completed (3)     │
 │ <────────────────────────────── │ 切片 3 正常完成
 │  ...                            │
 │                                 │
 │  event: file-completed          │
 │  { status: PARTIAL_FAILED }     │
 │ <────────────────────────────── │ FileRecord → PARTIAL_FAILED
 │                                 │
 ▼                                 ▼
```

### 3.3 兜底流程 — SSE 断线后查询

```
前端                              后端
 │                                 │
 │  SSE 连接意外断开                │
 │                                 │
 │  GET /api/files/{fileId}/status │
 │ ──────────────────────────────> │
 │                                 │ 查询 FileRecord + FileChunks
 │  200 OK                         │
 │  { completedChunks: 3, ... }    │
 │ <────────────────────────────── │
 │                                 │
 │  计算进度 = 3/10 = 30%          │
 │  重新连接 SSE 继续接收剩余事件    │
 │                                 │
 ▼                                 ▼
```

---

## 4. 技术要点

### 4.1 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 框架 | Spring Boot | 4.0.0 |
| 语言 | Java | 17 |
| 构建 | Maven | pom.xml 已有 |
| 数据库 | MySQL | 8.x |
| 异步 | Spring @Async + ThreadPoolTaskExecutor | Spring Boot 内置 |
| SSE | Spring SseEmitter | Spring MVC 内置 |
| 文件存储 | 本地文件系统 | — |

### 4.2 新增 Maven 依赖

```xml
<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- MySQL Connector -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 4.3 包结构与模块职责

参见设计方案中的目录树，核心模块：

- **controller** — HTTP 接入层（POST 上传 + GET SSE + GET 状态查询）
- **service** — 业务编排层（接口 + 实现分离）
- **chunker** — 切片模块（`FixedCountChunker`：总大小 / 10，最后一片收尾）
- **storage** — 存储模块（`LocalFileStorage`：按 `{basePath}/{fileId}/chunk_{index}.bin` 存储）
- **async** — 异步模块（线程池配置 + 单片处理任务 + SSE 注册中心）
- **model** — 数据模型（entity / dto / enums 三类分离）
- **repository** — 数据访问层（Spring Data JPA）
- **common** — 公共模块（统一响应、异常处理）

### 4.4 切片算法

```java
// FixedCountChunker
int totalSize = fileBytes.length;
int baseChunkSize = totalSize / 10;          // 每片基础大小
int remainder = totalSize % 10;               // 余数附加到最后一片

for (int i = 0; i < 10; i++) {
    int start = i * baseChunkSize;
    int end = (i == 9) ? totalSize : start + baseChunkSize;
    // 最后一片吸收余数
    chunks[i] = Arrays.copyOfRange(fileBytes, start, end);
}
```

### 4.5 异步线程池配置

```java
// AsyncConfig
@Bean("chunkExecutor")
public ThreadPoolTaskExecutor chunkExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);           // 核心 4 线程
    executor.setMaxPoolSize(8);            // 最大 8 线程
    executor.setQueueCapacity(50);         // 队列容量
    executor.setThreadNamePrefix("chunk-");
    executor.setRejectedExecutionHandler(
        new CallerRunsPolicy()             // 队列满时由调用线程执行
    );
    executor.initialize();
    return executor;
}
```

### 4.6 SSE 注册中心

```java
// SseEmitterRegistry
// 线程安全的 ConcurrentHashMap
// key = fileId, value = SseEmitter

register(fileId)      → new SseEmitter(30 * 60 * 1000L) // 30分钟超时
send(fileId, event)   → emitter.send(SseEmitter.event().name("chunk-completed").data(event))
remove(fileId)        → emitter.complete()
```

### 4.7 存储路径规范

```
{store.base-path}/
  └── {fileId}/
      ├── chunk_0.bin
      ├── chunk_1.bin
      ├── ...
      └── chunk_9.bin
```

### 4.8 状态机

```
文件状态（FileStatus）：
  PROCESSING ──→ COMPLETED
     │
     └──→ PARTIAL_FAILED（有完成有失败）
     │
     └──→ FAILED（全部失败）

切片状态（ChunkStatus）：
  PENDING ──→ COMPLETED
     │
     └──→ FAILED
```

---

## 5. 接口规格

### 5.1 文件上传

```
POST /api/files/upload
Content-Type: multipart/form-data

Request:
  userId: String        必填
  file:   MultipartFile 必填

Response 200:
{
  "code": 200,
  "message": "文件已接收，异步处理中",
  "data": {
    "fileId": "550e8400-e29b-41d4-a716-446655440000",
    "totalChunks": 10,
    "sseEndpoint": "/api/files/550e8400-e29b-41d4-a716-446655440000/progress",
    "statusEndpoint": "/api/files/550e8400-e29b-41d4-a716-446655440000/status"
  }
}

Response 400（参数缺失）:
{
  "code": 400,
  "message": "userId 不能为空",
  "data": null
}
```

### 5.2 SSE 进度推送

```
GET /api/files/{fileId}/progress
Accept: text/event-stream

事件类型 1 — 切片完成:
event: chunk-completed
data: {"chunkIndex": 0, "chunkId": "...", "status": "COMPLETED", "totalChunks": 10}

事件类型 2 — 切片失败:
event: chunk-error
data: {"chunkIndex": 3, "chunkId": "...", "status": "FAILED", "message": "磁盘写入失败"}

事件类型 3 — 全部完成:
event: file-completed
data: {"fileId": "...", "status": "COMPLETED", "totalChunks": 10, "completedChunks": 10,
       "failedChunks": 0, "message": "所有切片处理完成"}

事件类型 4 — 连接心跳 (每 15 秒):
event: heartbeat
data: {"timestamp": "2026-06-02T10:30:00"}
```

### 5.3 状态查询

```
GET /api/files/{fileId}/status

Response 200:
{
  "code": 200,
  "data": {
    "fileId": "550e8400-...",
    "fileName": "report.pdf",
    "fileSize": 1048576,
    "status": "PROCESSING",
    "totalChunks": 10,
    "completedChunks": 3,
    "failedChunks": 0,
    "chunks": [
      {"chunkIndex": 0, "chunkId": "...", "chunkSize": 104857, "status": "COMPLETED"},
      {"chunkIndex": 1, "chunkId": "...", "chunkSize": 104857, "status": "COMPLETED"},
      {"chunkIndex": 2, "chunkId": "...", "chunkSize": 104857, "status": "COMPLETED"},
      {"chunkIndex": 3, "chunkId": "...", "chunkSize": 0, "status": "PENDING"},
      ...
    ]
  }
}
```

---

## 6. 数据库规格

### 6.1 file_record 表

```sql
CREATE TABLE file_record (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id          VARCHAR(64)  NOT NULL UNIQUE COMMENT '业务文件ID（UUID）',
    user_id          VARCHAR(64)  NOT NULL COMMENT '用户ID',
    file_name        VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size        BIGINT       NOT NULL COMMENT '文件大小(byte)',
    total_chunks     INT          NOT NULL DEFAULT 10 COMMENT '切片总数',
    completed_chunks INT          NOT NULL DEFAULT 0 COMMENT '已完成切片数',
    failed_chunks    INT          NOT NULL DEFAULT 0 COMMENT '失败切片数',
    store_path       VARCHAR(500) COMMENT '磁盘存储根目录',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT '状态',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.2 file_chunk 表

```sql
CREATE TABLE file_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_id    VARCHAR(64)  NOT NULL UNIQUE COMMENT '切片业务ID',
    file_id     VARCHAR(64)  NOT NULL COMMENT '关联文件ID',
    chunk_index INT          NOT NULL COMMENT '切片序号(0-9)',
    chunk_size  BIGINT       NOT NULL DEFAULT 0 COMMENT '切片大小(byte)',
    store_path  VARCHAR(500) COMMENT '该切片磁盘路径',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    error_msg   VARCHAR(500) COMMENT '失败原因',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_file_id (file_id),
    UNIQUE KEY uk_file_chunk (file_id, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 7. 验收标准

| 编号 | 验收项 | 标准 | 验证方式 |
|---|---|---|---|
| A1 | 接口快速响应 | POST /upload 在 200ms 内返回（不含文件传输时间） | 压测 |
| A2 | 固定切片数 | 任意大小文件均切为 10 片 | 检查 file_chunk 表 |
| A3 | 切片大小均匀 | 前 9 片大小相等，最后一片 ≤ 前 9 片 + 余数 | 验证切片字节数 |
| A4 | 并行处理 | 10 片在独立的线程池中执行，非串行 | 日志时间戳对比 |
| A5 | SSE 实时推送 | 每片完成后 100ms 内前端收到事件 | 集成测试 |
| A6 | 单片失败隔离 | 某片写磁盘失败，其他 9 片正常完成 | 模拟磁盘满 |
| A7 | 文件状态正确 | 全部成功 → COMPLETED；部分失败 → PARTIAL_FAILED | 集成测试 |
| A8 | 状态查询可用 | GET /status 返回准确进度 | 集成测试 |
| A9 | SSE 超时回收 | 前端断线后 30 分钟 SseEmitter 自动清理 | 单元测试 |
| A10 | 空文件处理 | 空文件返回明确错误，不触发异步处理 | 边界测试 |
| A11 | 超大文件处理 | 1GB 文件正常切片，不 OOM | 流式读取 |

---

## 8. 风险与约束

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 超大文件 OOM | 服务崩溃 | 流式切片，不将整个文件加载到内存 |
| 磁盘空间不足 | 切片写入失败 | 每片独立 try-catch，失败不阻塞其他片 |
| SSE 连接泄漏 | 内存泄漏 | SseEmitter 30 分钟超时 + callback 自动清理 |
| MySQL 写入瓶颈 | 10 片并发写 DB 可能冲突 | 使用唯一约束 + 行锁，切片 record 提前创建 |
| 线程池耗尽 | 任务堆积 | CallerRunsPolicy + 队列容量限制 |

---

## 9. 后续迭代规划

## 9. 迭代记录

| 迭代 | 内容 | 状态 | 完成日期 |
|---|---|---|---|
| V1.0 | 后端项目骨架搭建（23 个文件，编译通过） | ✅ 已完成 | 2026-06-02 |
| V1.1 | 前端对接 + 完整联调 | ✅ 已完成 | 2026-06-02 |
| V1.2 | 文件完整性校验（MD5）+ 秒传去重 | ✅ 已完成 | 2026-06-03 |
| V1.3 | 切片断点续传（失败切片可重试） | 📋 规划中 | — |
| V2.0 | 存储后端可插拔（S3/OSS） | 📋 规划中 | — |
| V2.1 | 文件合并与下载接口 | 📋 规划中 | — |

### V1.2 新增功能

| 编号 | 功能 | 说明 |
|---|---|---|
| F10 | 文件级 MD5 | 上传时计算整个文件的 MD5，写入 file_record，响应中返回 |
| F11 | 切片级 MD5 | 每切片独立计算 MD5，写入 file_chunk，SSE 事件中携带 |
| F12 | 校验结果前端可见 | 上传响应 + SSE 完成事件 + 状态查询 均返回 MD5 信息 |
| F13 | 独立校验接口 | GET /api/files/{fileId}/verify，服务端重算 MD5 并比对 |
| F14 | 前端校验展示 | 完成弹窗显示 MD5 + 校验通过/失败 + 手动触发服务端校验 |
| F15 | MD5 秒传去重 | 同一 MD5 文件不重复切片入库，直接返回已有 fileId |

### V1.2 技术实现

- **MD5 算法**：`java.security.MessageDigest`（JDK 内置，零额外依赖）
- **校验接口**：从磁盘重新读取所有切片 → 拼接 → 计算合并 MD5 → 与原始 fileMd5 比对
- **去重机制**：`FileRecordRepository.findByFileMd5()` 查询已有记录，命中则跳过切片处理
- **前端体验**：完成弹窗显示 MD5 值和校验结果徽章，支持一键触发服务端重新校验

---

> **当前状态：V1.2 开发完成，前后端均已编译通过**
