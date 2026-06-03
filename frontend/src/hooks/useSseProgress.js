import { useState, useEffect, useRef, useCallback } from 'react';
import { connectProgressStream, fetchStatus } from '../api/fileApi';

/**
 * SSE 进度监听 Hook
 * @param {string|null} sseEndpoint    SSE 端点
 * @param {string|null} statusEndpoint 兜底状态查询端点
 * @param {string}     status          当前应用状态（仅 processing 时启用 SSE）
 * @returns {{ progress, completedChunks, failedChunks, fileStatus, fileMd5, verified }}
 */
export function useSseProgress(sseEndpoint, statusEndpoint, status) {
  const [progress, setProgress] = useState(0);
  const [completedChunks, setCompletedChunks] = useState(0);
  const [failedChunks, setFailedChunks] = useState(0);
  const [fileStatus, setFileStatus] = useState(null);
  const [fileMd5, setFileMd5] = useState(null);
  const [verified, setVerified] = useState(null);
  const eventSourceRef = useRef(null);
  const fallbackTimerRef = useRef(null);
  const hasSyncedRef = useRef(false);
  const finalStatusFetchedRef = useRef(false);
  const completedRef = useRef(false); // ✅ 新增：标记是否已完成

  // ✅ 1. 先定义兜底轮询函数
  const startFallbackPolling = useCallback(() => {
    if (!statusEndpoint) return;
    if (fallbackTimerRef.current) clearInterval(fallbackTimerRef.current);
    
    console.log('🔄 启动兜底轮询:', statusEndpoint);
    
    fallbackTimerRef.current = setInterval(async () => {
      try {
        const data = await fetchStatus(statusEndpoint);
        console.log('🔄 轮询状态:', data);
        setProgress(data.completedChunks || 0);
        setCompletedChunks(data.completedChunks || 0);
        setFailedChunks(data.failedChunks || 0);
        setFileStatus(data.status);
        setFileMd5(data.fileMd5 || null);

        if (data.status === 'COMPLETED' || data.status === 'PARTIAL_FAILED' || data.status === 'FAILED') {
          if (fallbackTimerRef.current) {
            clearInterval(fallbackTimerRef.current);
            fallbackTimerRef.current = null;
          }
        }
      } catch (e) {
        console.warn('兜底轮询失败:', e);
      }
    }, 2000);
  }, [statusEndpoint]);

  // ✅ 2. 获取最终状态（确保进度完整）
  const fetchFinalStatus = useCallback(async () => {
    if (finalStatusFetchedRef.current) return;
    if (!statusEndpoint) return;
    
    finalStatusFetchedRef.current = true;
    
    try {
      const data = await fetchStatus(statusEndpoint);
      console.log('🏁 最终状态同步:', data);
      const total = data.totalChunks || 10;
      setProgress(total);
      setCompletedChunks(total);
      setFailedChunks(data.failedChunks || 0);
      setFileStatus(data.status);
      setFileMd5(data.fileMd5);
      setVerified(data.verified);
    } catch (e) {
      console.warn('最终状态同步失败:', e);
    }
  }, [statusEndpoint]);

  // ✅ 3. 初始状态同步 + 检查是否已完成
  useEffect(() => {
    if (status === 'processing' && statusEndpoint && !hasSyncedRef.current) {
      hasSyncedRef.current = true;
      fetchStatus(statusEndpoint)
        .then((data) => {
          console.log('📡 初始状态同步:', data);
          
          // ✅ 关键：如果文件已经完成，直接设置状态，不需要后续 SSE
          if (data.status === 'COMPLETED' || data.status === 'PARTIAL_FAILED') {
            console.log('🏁 文件已完成，直接设置状态');
            const total = data.totalChunks || 10;
            setProgress(total);
            setCompletedChunks(total);
            setFailedChunks(data.failedChunks || 0);
            setFileStatus(data.status);
            setFileMd5(data.fileMd5);
            setVerified(data.verified);
            completedRef.current = true;
            return;
          }
          
          // 未完成时，同步当前进度
          if (data.completedChunks !== undefined) {
            setProgress(data.completedChunks);
            setCompletedChunks(data.completedChunks);
            setFailedChunks(data.failedChunks || 0);
            setFileStatus(data.status);
            setFileMd5(data.fileMd5);
            setVerified(data.verified);
          }
        })
        .catch((e) => console.warn('初始状态同步失败:', e));
    }
  }, [status, statusEndpoint]);

  // ✅ 4. SSE 连接（仅在文件未完成时建立）
  useEffect(() => {
    // 如果文件已经完成，不需要建立 SSE 连接
    if (status !== 'processing' || !sseEndpoint || completedRef.current) return;

    console.log('🔌 建立 SSE 连接:', sseEndpoint);
    finalStatusFetchedRef.current = false;

    const es = connectProgressStream(sseEndpoint, {
      onChunkCompleted: (data) => {
        console.log('✅ 收到切片完成:', data);
        setProgress((prev) => prev + 1);
        setCompletedChunks((prev) => prev + 1);
      },
      onChunkError: (data) => {
        console.log('❌ 收到切片错误:', data);
        setFailedChunks((prev) => prev + 1);
      },
      onFileCompleted: (data) => {
        console.log('🎉 收到文件完成:', data);
        const finalCompleted = data.completedChunks || data.totalChunks || 10;
        setProgress(finalCompleted);
        setCompletedChunks(finalCompleted);
        setFailedChunks(data.failedChunks || 0);
        setFileStatus(data.status);
        setFileMd5(data.fileMd5 || null);
        setVerified(data.verified || false);
        completedRef.current = true;
        es.close();
      },
      onError: (error) => {
        console.warn('⚠️ SSE 连接错误:', error);
        if (!completedRef.current) {
          fetchFinalStatus();
          startFallbackPolling();
        }
      },
    });

    eventSourceRef.current = es;

    return () => {
      console.log('🔌 关闭 SSE 连接');
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    };
  }, [sseEndpoint, status, startFallbackPolling, fetchFinalStatus]);

  // ✅ 5. 清理轮询定时器
  useEffect(() => {
    return () => {
      if (fallbackTimerRef.current) {
        clearInterval(fallbackTimerRef.current);
        fallbackTimerRef.current = null;
      }
    };
  }, []);

  return { progress, completedChunks, failedChunks, fileStatus, fileMd5, verified };
}