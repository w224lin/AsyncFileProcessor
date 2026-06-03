# 前端文件上传界面 — 需求产品文档 (PRD)

> 版本：v1.0 | 日期：2026-06-03 | 状态：待审批
> 关联文档：[后端 PRD](../backend/docs/PRD-文件切片上传系统.md)

---

## 1. 产品概述

### 1.1 背景

基于后端的文件切片上传系统（固定 10 片 + SSE 实时进度推送），开发前端 React 页面。用户填写用户 ID、选择文件后上传，通过 SSE 接收后端推送的切片完成事件，实时展示进度条，全部完成后弹窗通知。

### 1.2 目标

- 用户可输入用户 ID + 选择文件，一键上传
- 上传后立即看到进度条（0% → 100%，每片 +10%）
- 全部切片处理完成后弹窗展示结果
- 防止重复提交（多层级防护）
- 纯 React 组件，无第三方 UI 库依赖

---

## 2. 功能列表

| 编号 | 功能 | 优先级 | 说明 |
|---|---|---|---|
| F1 | 文件选择表单 | P0 | userId 输入框 + 文件选择器 |
| F2 | 文件上传 | P0 | POST /api/files/upload，FormData 提交 |
| F3 | SSE 进度监听 | P0 | EventSource 连接，接收 chunk-completed 事件 |
| F4 | 进度条展示 | P0 | 每片 +10%，视觉平滑过渡 |
| F5 | 完成弹窗 | P0 | 进度 100% 后弹出，展示文件 ID 和统计信息 |
| F6 | 防重复提交 | P0 | 按钮状态禁用 + 文案变化 + 状态机防护 |
| F7 | SSE 断线兜底 | P1 | 重连机制 + 状态查询接口 fallback |
| F8 | 错误处理与提示 | P1 | 网络错误、后端异常的友好提示 |

---

## 3. 用户流程

### 3.1 主线流程

```
┌──────────────────────────────────────────────────────────┐
│ 页面初始状态                                             │
│                                                          │
│  用户ID: [_______________]                               │
│  文件:   [选择文件] 未选择文件                             │
│                                                          │
│         [上传]  ← 蓝色可点击                              │
│                                                          │
└──────────────────────────────────────────────────────────┘
                          │ 用户输入 userId + 选择文件
                          │ 点击「上传」
                          ▼
┌──────────────────────────────────────────────────────────┐
│ 文件传输中 (status = 'uploading')                        │
│                                                          │
│  用户ID: [user123________]                               │
│  文件:   [选择文件] report.pdf (5.2 MB)                   │
│                                                          │
│         [上传中...]  ← 灰色禁用                           │
│                                                          │
│  (此时 FormData 正在发送，大文件可能耗时几秒)               │
└──────────────────────────────────────────────────────────┘
                          │ 后端返回 200 OK
                          ▼
┌──────────────────────────────────────────────────────────┐
│ 切片处理中 (status = 'processing')                       │
│                                                          │
│  用户ID: user123                                         │
│  文件:   report.pdf                                      │
│                                                          │
│  ████████░░░░░░░░░░░░░░░░░░ 40%  切片 4/10               │
│                                                          │
│         [处理中 4/10...]  ← 灰色禁用                      │
│                                                          │
│  (每收到一个 chunk-completed，进度条 +10%)                 │
└──────────────────────────────────────────────────────────┘
                          │ 持续接收 SSE 事件...
                          │ chunk-completed × 10
                          ▼
┌──────────────────────────────────────────────────────────┐
│ 全部完成 — 弹窗 (status = 'completed')                    │
│                                                          │
│  ┌────────────────────────────────────┐                  │
│  │       ✅ 文件上传完成               │                  │
│  │                                    │                  │
│  │  用户ID:       user123             │                  │
│  │  文件ID:       a1b2c3d4-e5f6-...   │                  │
│  │  文件名:       report.pdf          │                  │
│  │  文件大小:     5.2 MB              │                  │
│  │  切片总数:     10                  │                  │
│  │  完成切片:     10                  │                  │
│  │  失败切片:     0                   │                  │
│  │                                    │                  │
│  │  ████████████████████████ 100%     │                  │
│  │                                    │                  │
│  │            [关闭]                  │                  │
│  └────────────────────────────────────┘                  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 3.2 防重复提交流程

```
用户点击「上传」
  │
  ├─ 检查: status === 'idle' ?
  │    YES → 继续
  │    NO  → 按钮 disabled，点击无效（事件被忽略）
  │
  └─ 上传中用户操作：
       · 按钮灰色，不可点击
       · userId 输入框 disabled
       · 文件选择器 disabled
       · 浏览器刷新 → 状态重置，需重新上传
```

### 3.3 异常流程

```
场景 1: POST /upload 网络错误
  status → 'error'
  按钮恢复 → '重试上传'
  显示红色提示: "上传失败，请检查网络后重试"

场景 2: 后端返回 4xx/5xx
  status → 'error'
  显示错误信息: 后端返回的 message

场景 3: SSE 连接中断
  自动尝试 EventSource 重连（浏览器原生行为）
  重连失败 → 每 5 秒调用 GET /status 查询进度

场景 4: 部分切片失败
  SSE 收到 chunk-error 事件
  进度条不增加（不把失败的算入进度）
  弹窗展示时有 failedChunks > 0
```

---

## 4. 组件架构

### 4.1 组件树

```
App.jsx
 ├─ FileUploadForm
 │    ├─ userId 输入框
 │    ├─ 文件选择器
 │    └─ 上传按钮 (状态驱动)
 │
 ├─ ProgressBar (status='processing' 时显示)
 │    ├─ 进度条 track
 │    ├─ 进度条 fill (宽度 = progress * 10%)
 │    └─ 百分比文字
 │
 └─ CompletionModal (showModal=true 时显示)
      ├─ 遮罩层
      ├─ 结果信息
      ├─ 进度条 (100%)
      └─ 关闭按钮
```

### 4.2 数据流

```
App.jsx — 状态持有者
  │
  ├─ state: userId (string)
  ├─ state: file (File | null)
  ├─ state: fileId (string | null)
  ├─ state: status ('idle' | 'uploading' | 'processing' | 'completed' | 'error')
  ├─ state: progress (0 ~ 10)
  ├─ state: failedChunks (0 ~ 10)
  ├─ state: showModal (boolean)
  ├─ state: errorMessage (string | null)
  ├─ state: resultInfo (object | null)   ← 弹窗展示用
  │
  ├─▶ FileUploadForm
  │     props: userId, file, status, onUserIdChange, onFileChange, onUpload
  │     （纯展示 + 事件回调，无内部状态）
  │
  ├─▶ ProgressBar
  │     props: progress (0~10), totalChunks=10, status
  │     （纯展示组件）
  │
  └─▶ CompletionModal
        props: showModal, resultInfo, onClose
        （纯展示组件）
```

### 4.3 状态机

```
          ┌─────────┐
          │  idle   │ ← 初始状态 / 用户点「关闭」后
          └────┬────┘
               │ 点击上传 (校验通过)
               ▼
          ┌───────────┐
          │ uploading │ ← 正在 POST FormData
          └─────┬─────┘
                │ POST 成功 / POST 失败
                ▼           ▼
     ┌────────────┐  ┌─────────┐
     │ processing │  │  error  │──→ 可点「重试上传」回到 idle
     └──────┬─────┘  └─────────┘
            │ 收到 file-completed
            ▼
     ┌─────────────┐
     │  completed  │ ← 弹窗显示
     └──────┬──────┘
            │ 点击「关闭」
            ▼
          idle (可再次上传)
```

---

## 5. 目录与文件结构

```
frontend/src/
├── api/
│   └── fileApi.js              # API 封装
├── components/
│   ├── FileUploadForm.jsx      # 上传表单组件
│   ├── ProgressBar.jsx         # 进度条组件
│   └── CompletionModal.jsx     # 完成弹窗组件
├── hooks/
│   └── useSseProgress.js       # SSE 进度监听 Hook
├── App.jsx                     # 主页面（状态编排）
├── App.css                     # 所有组件样式
├── main.jsx                    # 入口（不变）
└── index.css                   # 全局样式（不变）

vite.config.js                  # 添加 API 代理
```

### 文件删除清单

| 文件 | 原因 |
|---|---|
| `src/assets/react.svg` | Vite 模板图片，不再使用 |
| `src/assets/hero.png` | Vite 模板图片，不再使用 |
| `public/favicon.svg` | 可保留，改为自定义图标 |

---

## 6. 接口对接规格

### 6.1 Vite 代理配置

```javascript
// vite.config.js
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

前端调 `/api/files/upload` → Vite 代理 → `http://localhost:8080/api/files/upload`

### 6.2 API 封装接口

```javascript
// fileApi.js

/**
 * 上传文件
 * @param {string} userId
 * @param {File} file
 * @returns {Promise<{fileId, totalChunks, sseEndpoint, statusEndpoint}>}
 */
export async function uploadFile(userId, file) { ... }

/**
 * 创建 SSE 进度连接
 * @param {string} sseEndpoint  例如 "/api/files/xxx/progress"
 * @param {object} callbacks    { onChunkCompleted, onChunkError, onFileCompleted, onError }
 * @returns {EventSource}       调用方可 .close() 断开
 */
export function connectProgressStream(sseEndpoint, callbacks) { ... }

/**
 * 兜底状态查询
 * @param {string} statusEndpoint  例如 "/api/files/xxx/status"
 * @returns {Promise<object>}      后端状态响应
 */
export async function fetchStatus(statusEndpoint) { ... }
```

### 6.3 Hook 接口

```javascript
// useSseProgress.js

/**
 * SSE 进度监听 Hook
 * @param {string|null} sseEndpoint  SSE 端点，null 时不连接
 * @returns {{ progress: number, completedChunks: number, failedChunks: number, fileStatus: string }}
 */
export function useSseProgress(sseEndpoint) { ... }
```

---

## 7. 组件规格

### 7.1 FileUploadForm

```
Props:
  userId: string           — 当前输入的用户 ID
  file: File | null        — 当前选择的文件
  status: string           — 当前状态
  onUserIdChange: fn       — userId 变化回调
  onFileChange: fn         — 文件变化回调
  onUpload: fn             — 点击上传回调

行为:
  - status !== 'idle' 时：输入框、选择器、按钮全部 disabled
  - 按钮文案随 status 变化：
      idle       → "上传"
      uploading  → "上传中..."
      processing → "处理中 {progress}/10..."
      completed  → "重新上传"
      error      → "重试上传"
  - 文件未选择或 userId 为空 → 按钮 disabled
  - 显示已选文件的名称和大小
```

### 7.2 ProgressBar

```
Props:
  progress: number         — 已完成切片数 (0~10)
  totalChunks: number      — 总切片数 (固定 10)
  status: string           — 当前状态

行为:
  - status !== 'processing' && status !== 'completed' → 不显示
  - 进度条宽度: (progress / totalChunks) * 100%
  - CSS transition: width 0.3s ease
  - 显示文字: "切片 {progress}/{totalChunks}"
  - 显示百分比: "{progress * 10}%"
```

### 7.3 CompletionModal

```
Props:
  showModal: boolean       — 是否显示弹窗
  resultInfo: object|null  — { fileId, userId, fileName, fileSize, totalChunks, completedChunks, failedChunks }
  onClose: fn              — 关闭回调

行为:
  - showModal=false → 不渲染
  - 包含完整结果信息 + 进度条 (100%)
  - 有 failedChunks > 0 → 显示黄色警告
  - 点击遮罩层或关闭按钮 → onClose()
  - 关闭后状态重置为 idle
```

---

## 8. 防重复提交设计

### 8.1 三层防护

| 层级 | 机制 | 作用 |
|---|---|---|
| UI 层 | 按钮 disabled + 输入框 disabled | 用户无法点击 |
| 状态层 | status 状态机 | uploading/processing 状态下忽略上传请求 |
| 文案层 | 按钮文案实时反馈 | 用户一看就知道当前在做什么 |

### 8.2 状态与按钮映射

| status | 按钮文案 | 按钮颜色 | 可点击 | 输入框 |
|---|---|---|---|---|
| idle | 上传 | 蓝色 | ✅ | ✅ |
| uploading | 上传中... | 灰色 | ❌ | ❌ |
| processing | 处理中 N/10... | 灰色 | ❌ | ❌ |
| completed | 重新上传 | 绿色 | ✅ | ✅ |
| error | 重试上传 | 红色 | ✅ | ✅ |

---

## 9. 样式方案

### 9.1 设计原则
- 纯 CSS，不引入 UI 库
- 主色调：蓝色 `#4A90D9`
- 成功色：绿色 `#52C41A`
- 失败色：红色 `#FF4D4F`
- 灰度：`#F0F0F0` / `#D9D9D9` / `#999`
- 阴影和圆角：现代化简洁风格
- 响应式：最小宽度 400px，最大宽度 600px

### 9.2 进度条设计
```
┌────────────────────────────────────────────┐
│ ████████████░░░░░░░░░░░░░░░░░░░░ 40%      │
│ 切片 4/10                                  │
└────────────────────────────────────────────┘

- track: height 12px, border-radius 6px, bg #F0F0F0
- fill:  height 12px, border-radius 6px, bg #4A90D9
- transition: width 0.3s ease
```

---

## 10. 验收标准

| 编号 | 验收项 | 标准 | 验证方式 |
|---|---|---|---|
| A1 | 文件选择 | 点击选择文件，显示文件名和大小 | 手动测试 |
| A2 | 上传请求 | 填写 userId + 选文件后点击上传，POST 成功发出 | Network 面板 |
| A3 | 进度条显示 | 每收到 SSE chunk-completed 事件，进度条 +10% | 目视检查 |
| A4 | 进度 100% 弹窗 | 10 片全完成后弹窗弹出，信息完整 | 手动测试 |
| A5 | 防重复提交 | uploading/processing 状态下按钮不可点击 | 手动快速点击 |
| A6 | 按钮文案更新 | 各状态下按钮文案匹配规格 | 状态逐一验证 |
| A7 | 输入框禁用 | uploading/processing 状态下输入框不可编辑 | 手动测试 |
| A8 | 错误状态恢复 | 上传失败后按钮恢复，可重新上传 | 断网模拟 |
| A9 | 弹窗关闭重置 | 关闭弹窗后状态回到 idle，可再次上传 | 手动测试 |
| A10 | 空校验 | userId 为空或未选文件时按钮 disabled | 手动测试 |

---

## 11. 不做的优化（后续迭代）

| 内容 | 原因 |
|---|---|
| 拖拽上传 | V1.0 只做基础选择上传 |
| 多文件批量 | V1.0 只做单文件 |
| 上传取消 | V1.0 启动后不可取消 |
| 断点续传前端配合 | V1.0 后端也未实现 |
| 响应式移动端适配 | V1.0 仅桌面端 |
| 国际化 | V1.0 仅中文 |

---

> **审批状态：待用户确认**
> 确认后进入开发
