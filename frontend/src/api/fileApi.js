/**
 * 上传文件
 * @param {string} userId  用户ID
 * @param {File} file      文件对象
 * @returns {Promise<{fileId, totalChunks, fileMd5, sseEndpoint, statusEndpoint}>}
 */
export async function uploadFile(userId, file) {
  const formData = new FormData();
  formData.append('userId', userId);
  formData.append('file', file);

  const response = await fetch('/api/files/upload', {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`上传失败 (${response.status})`);
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || '上传失败');
  }

  return result.data;
}

/**
 * 创建 SSE 进度连接
 * @param {string} sseEndpoint  SSE 端点，如 "/api/files/xxx/progress"
 * @param {object} callbacks    事件回调
 * @returns {EventSource}
 */
export function connectProgressStream(sseEndpoint, callbacks) {
  const { onChunkCompleted, onChunkError, onFileCompleted, onError } = callbacks;
  const eventSource = new EventSource(sseEndpoint);

  eventSource.addEventListener('chunk-completed', (e) => {
    const data = JSON.parse(e.data);
    onChunkCompleted?.(data);
  });

  eventSource.addEventListener('chunk-error', (e) => {
    const data = JSON.parse(e.data);
    onChunkError?.(data);
  });

  eventSource.addEventListener('file-completed', (e) => {
    const data = JSON.parse(e.data);
    eventSource.close();
    onFileCompleted?.(data);
  });

  eventSource.onerror = (e) => {
    onError?.(e);
  };

  return eventSource;
}

/**
 * 兜底查询文件状态
 * @param {string} statusEndpoint  如 "/api/files/xxx/status"
 * @returns {Promise<object>}
 */
export async function fetchStatus(statusEndpoint) {
  const response = await fetch(statusEndpoint);
  if (!response.ok) {
    throw new Error(`状态查询失败 (${response.status})`);
  }
  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || '状态查询失败');
  }
  return result.data;
}

/**
 * 文件完整性校验
 * @param {string} fileId  文件ID
 * @returns {Promise<{verified, fileMd5, reCalcMd5, failedChunks, message}>}
 */
export async function fetchVerify(fileId) {
  const response = await fetch(`/api/files/${fileId}/verify`);
  if (!response.ok) {
    throw new Error(`校验请求失败 (${response.status})`);
  }
  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || '校验失败');
  }
  return result.data;
}
