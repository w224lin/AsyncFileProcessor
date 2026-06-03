# 📦 文件切片上传系统 (Async File Processor)

> 安全高效 · 自动分片 · 实时追踪 · MD5 完整性校验

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.0-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-8-646CFF)](https://vite.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1)](https://www.mysql.com/)

---

## 项目简介

基于 Spring Boot + React 的全栈文件切片上传系统。前端提交文件后，后端将文件切分为 10 个固定切片，在专用线程池中并行处理（写磁盘 + 写数据库），并通过 **SSE（Server-Sent Events）** 实时向前端推送每个切片的处理进度。

**V1.2 新增**：文件级 + 切片级 MD5 完整性校验、独立校验接口、MD5 秒传去重。

---

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                      前端 (React 19)                     │
│  Vite 8 · CSS3 · EventSource (SSE) · Fetch API          │
│  FileUploadForm → ProgressBar → CompletionModal          │
└─────────────────────┬───────────────────────────────────┘
                      │ HTTP + SSE
                      ▼
┌─────────────────────────────────────────────────────────┐
│                  后端 (Spring Boot 4.0.0)                │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐    │
│  │Controller│  │ Service  │  │    Async Module     │    │
│  │          │  │          │  │                     │    │
│  │ Upload   │─▶│ FileSvc  │─▶│ AsyncConfig(线程池) │    │
│  │ SSE      │  │   ·MD5   │  │ FileAsyncProcessor  │    │
│  │ Status   │  │   ·去重  │  │ ChunkProcessTask×10 │    │
│  │ Verify   │  │          │  │ SseEmitterRegistry  │    │
│  └──────────┘  └──────────┘  └────────────────────┘    │
│                      │                  │                │
│                      ▼                  ▼                │
│               ┌──────────┐    ┌──────────────┐          │
│               │  MySQL   │    │  本地文件系统  │          │
│               │ file_rec │    │  chunks/      │          │
│               │ file_chk │    │   {fileId}/   │          │
│               └──────────┘    └──────────────┘          │
└─────────────────────────────────────────────────────────┘
```

---

## 项目亮点

### 🧩 固定切片 + 真正并行

- 文件固定切分为 **10 片**，`FixedCountChunker` 保证切片大小均匀
- 使用 `CompletableFuture.runAsync()` + 专用 `ThreadPoolTaskExecutor`（核心 4 线程，最大 8 线程）实现 **10 个切片真正并行处理**
- 每片独立 try-catch，**单片失败不阻塞其他片**

### 🔌 独立 Bean 避 @Async 坑

```java
// FileServiceImpl（非代理调用）
asyncProcessor.processAsync(fileId, fileBytes);  // ✅ 跨 Bean 调用，@Async 生效

// FileAsyncProcessor（独立 Component）
@Async("chunkExecutor")
public void processAsync(String fileId, byte[] fileBytes) { ... }  // ✅ AOP 正确拦截
```

经典 Spring `@Async` 自调用失效问题的标准解法。

### 📡 SSE 实时进度推送

- 前端通过 `EventSource` 建立长连接
- 每完成一个切片 → `event: chunk-completed`
- 切片失败 → `event: chunk-error`
- 全部完成 → `event: file-completed`
- SSE 断线时自动切换 **兜底轮询**（每 2 秒查询状态接口）
- `SseEmitterRegistry`（`ConcurrentHashMap`）管理所有 SSE 连接，30 分钟超时自动清理

### 🔐 MD5 完整性校验 + 秒传

```
上传 → 计算文件 MD5 → 查库去重
  ├─ 命中 → 秒传，直接返回已有 fileId（不重复切片）
  └─ 未命中 → 切片处理，每片独立计算 chunkMd5
              → 全部完成后 SSE 推送 verified 状态
              → 前端可点击「服务端重新校验」调用 /verify 接口
```

- **文件级 MD5**：`FileServiceImpl` 上传时计算，写入 `file_record`
- **切片级 MD5**：`ChunkProcessTask` 处理后计算，写入 `file_chunk`
- **独立校验接口** `GET /api/files/{fileId}/verify`：从磁盘重读所有切片 → 拼接 → 计算合并 MD5 → 与原始值比对，返回 `pass/fail`
- **MD5 去重秒传**：同一文件多次上传只处理一次

---

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 4.0.0 |
| 语言 | Java | 17 |
| 构建 | Maven | — |
| 数据库 | MySQL | 8.x |
| ORM | Spring Data JPA (Hibernate) | — |
| 异步 | Spring @Async + ThreadPoolTaskExecutor | Spring 内置 |
| SSE | Spring SseEmitter | Spring MVC 内置 |
| MD5 | java.security.MessageDigest | JDK 内置 |
| 前端框架 | React | 19 |
| 构建工具 | Vite | 8 |
| 样式 | CSS3（无第三方 UI 库） | — |

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 20+
- MySQL 8.x

### 1. 数据库初始化

```sql
CREATE DATABASE IF NOT EXISTS afp_db DEFAULT CHARSET utf8mb4;

-- Spring Data JPA ddl-auto: update 会自动建表
-- 也可以手动执行 docs 中的建表 SQL
```

### 2. 后端启动

```bash
cd backend

# 修改 application.yaml 中的数据库连接信息
# spring.datasource.url / username / password

mvn spring-boot:run
# 服务运行在 http://localhost:8080
```

### 3. 前端启动

```bash
cd frontend

npm install
npm run dev
# 开发服务器运行在 http://localhost:5173
```

---

## 项目结构

```
project/
├── README.md
├── CLAUDE.md                          # 开发行为规则
├── backend/
│   ├── pom.xml
│   ├── docs/
│   │   └── PRD-文件切片上传系统.md      # 需求产品文档
│   └── src/main/
│       ├── resources/
│       │   └── application.yaml       # 应用配置
│       └── java/com/afp/
│           ├── BackendApplication.java
│           ├── async/                  # 异步模块
│           │   ├── AsyncConfig.java           # 线程池配置
│           │   ├── ChunkProcessTask.java      # 单切片异步任务
│           │   ├── FileAsyncProcessor.java    # 文件异步编排器
│           │   └── SseEmitterRegistry.java    # SSE 连接注册中心
│           ├── chunker/               # 切片模块
│           │   ├── FileChunker.java           # 切片接口
│           │   └── FixedCountChunker.java     # 固定数量切片实现
│           ├── common/                # 公共模块
│           │   ├── BusinessException.java
│           │   ├── GlobalExceptionHandler.java
│           │   └── Result.java               # 统一响应格式
│           ├── controller/            # 控制器
│           │   ├── FileController.java        # 文件上传
│           │   └── FileProgressController.java # SSE + 状态 + 校验
│           ├── model/
│           │   ├── dto/               # 数据传输对象
│           │   │   ├── ChunkProgressEvent.java
│           │   │   ├── UploadResponse.java
│           │   │   └── VerifyResponse.java
│           │   ├── entity/            # 数据库实体
│           │   │   ├── FileChunk.java
│           │   │   └── FileRecord.java
│           │   └── enums/             # 枚举
│           │       ├── ChunkStatus.java
│           │       └── FileStatus.java
│           ├── repository/            # 数据访问层
│           │   ├── FileChunkRepository.java
│           │   └── FileRecordRepository.java
│           ├── service/               # 业务层
│           │   ├── FileService.java
│           │   └── impl/FileServiceImpl.java
│           └── storage/               # 存储模块
│               ├── FileStorage.java
│               └── LocalFileStorage.java
└── frontend/
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.jsx
        ├── App.jsx                   # 主应用（状态管理 + 步骤控制）
        ├── App.css                   # 全局样式
        ├── index.css                 # 基础重置
        ├── api/
        │   └── fileApi.js            # API 封装（上传/SSE/状态/校验）
        ├── hooks/
        │   └── useSseProgress.js     # SSE 进度监听 Hook
        └── components/
            ├── FileUploadForm.jsx    # 上传表单
            ├── ProgressBar.jsx       # 进度条
            └── CompletionModal.jsx   # 完成弹窗（含 MD5 校验）
```

---

## API 接口

### 文件上传

```
POST /api/files/upload
Content-Type: multipart/form-data

参数: userId (String), file (MultipartFile)

响应:
{
  "code": 200,
  "data": {
    "fileId": "uuid",
    "totalChunks": 10,
    "fileMd5": "d41d8cd98f00b204e9800998ecf8427e",   // V1.2 新增
    "sseEndpoint": "/api/files/{fileId}/progress",
    "statusEndpoint": "/api/files/{fileId}/status"
  }
}
```

### SSE 进度推送

```
GET /api/files/{fileId}/progress
Accept: text/event-stream

事件:
  event: chunk-completed  → { chunkIndex, chunkMd5, status }
  event: chunk-error      → { chunkIndex, status, message }
  event: file-completed   → { fileId, status, fileMd5, verified, completedChunks }
```

### 状态查询（SSE 断线兜底）

```
GET /api/files/{fileId}/status

响应: { fileId, fileName, fileMd5, status, completedChunks, failedChunks, chunks: [...] }
```

### 文件完整性校验（V1.2 新增）

```
GET /api/files/{fileId}/verify

响应:
{
  "code": 200,
  "data": {
    "fileId": "...",
    "verified": true,
    "fileMd5": "d41d8cd...",
    "reCalcMd5": "d41d8cd...",
    "failedChunks": null,
    "message": "文件完整性校验通过"
  }
}
```

---

## 数据库表

### file_record（文件记录表）

| 字段 | 类型 | 说明 |
|---|---|---|
| file_id | VARCHAR(64) | 业务文件 ID（UUID） |
| user_id | VARCHAR(64) | 用户 ID |
| file_name | VARCHAR(255) | 原始文件名 |
| file_size | BIGINT | 文件大小 (byte) |
| file_md5 | VARCHAR(32) | 文件 MD5（V1.2） |
| total_chunks | INT | 切片总数 |
| completed_chunks | INT | 已完成数 |
| failed_chunks | INT | 失败数 |
| status | VARCHAR(20) | PROCESSING / COMPLETED / PARTIAL_FAILED |

### file_chunk（切片表）

| 字段 | 类型 | 说明 |
|---|---|---|
| chunk_id | VARCHAR(64) | 切片业务 ID |
| file_id | VARCHAR(64) | 关联文件 ID |
| chunk_index | INT | 切片序号 (0-9) |
| chunk_size | BIGINT | 切片大小 (byte) |
| chunk_md5 | VARCHAR(32) | 切片 MD5（V1.2） |
| store_path | VARCHAR(500) | 磁盘存储路径 |
| status | VARCHAR(20) | PENDING / COMPLETED / FAILED |

---

## 状态机

```
文件状态（FileStatus）：
  PROCESSING ──→ COMPLETED（全部切片成功）
       │
       └──→ PARTIAL_FAILED（部分切片失败）

切片状态（ChunkStatus）：
  PENDING ──→ COMPLETED
       │
       └──→ FAILED
```

---

## 迭代记录

| 版本 | 内容 | 状态 |
|---|---|---|
| V1.0 | 后端骨架搭建（23 个文件，编译通过） | ✅ |
| V1.1 | 前端 React UI + 前后端联调 | ✅ |
| V1.2 | MD5 完整性校验 + 秒传去重 + 前端校验展示 | ✅ |
| V1.3 | 切片断点续传 | 📋 |
| V2.0 | 存储后端可插拔（S3/OSS） | 📋 |

---

## License

MIT
