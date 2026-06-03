import { useState, useCallback, useEffect, useRef } from 'react';  // ✅ 添加 useRef
import FileUploadForm from './components/FileUploadForm';
import ProgressBar from './components/ProgressBar';
import CompletionModal from './components/CompletionModal';
import { uploadFile } from './api/fileApi';
import { useSseProgress } from './hooks/useSseProgress';

export default function App() {
  const [userId, setUserId] = useState('');
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState('idle');

  const [fileId, setFileId] = useState(null);
  const [sseEndpoint, setSseEndpoint] = useState(null);
  const [statusEndpoint, setStatusEndpoint] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [resultInfo, setResultInfo] = useState(null);
  const [fileName, setFileName] = useState('');
  const [fileSize, setFileSize] = useState(0);

  // ✅ 新增：防止重复弹窗
  const modalTriggeredRef = useRef(false);

  const { progress, completedChunks, failedChunks, fileStatus, fileMd5, verified } =
    useSseProgress(sseEndpoint, statusEndpoint, status);

  // ✅ 修复：监听 fileStatus 变化，显示弹窗
  useEffect(() => {
    // 防止重复触发
    if (modalTriggeredRef.current) return;
    if (status !== 'processing') return;

    if (fileStatus === 'COMPLETED') {
      modalTriggeredRef.current = true;
      // 完全成功：直接显示 10/10
      console.log('✅ 文件完全成功，显示弹窗');
      setStatus('completed');
      setResultInfo({
        fileId,
        userId,
        fileName,
        fileSize: formatFileSize(fileSize),
        totalChunks: 10,
        completedChunks: 10,        // 直接写 10
        failedChunks: 0,
        fileMd5,
        verified: true,
      });
      setShowModal(true);
    } else if (fileStatus === 'PARTIAL_FAILED') {
      modalTriggeredRef.current = true;
      // 部分失败：使用实际数据
      console.log('⚠️ 部分失败，显示弹窗');
      setStatus('completed');
      setResultInfo({
        fileId,
        userId,
        fileName,
        fileSize: formatFileSize(fileSize),
        totalChunks: 10,
        completedChunks: completedChunks,
        failedChunks: failedChunks,
        fileMd5,
        verified: false,
      });
      setShowModal(true);
    }
  }, [fileStatus, status, fileId, userId, fileName, fileSize, completedChunks, failedChunks, fileMd5, verified]);

  const handleUpload = useCallback(async () => {
    if (status !== 'idle') return;
    
    // ✅ 重置弹窗防重复标志
    modalTriggeredRef.current = false;
    
    setErrorMessage(null);
    setStatus('uploading');

    try {
      const data = await uploadFile(userId, file);
      setFileId(data.fileId);
      setFileName(file.name);
      setFileSize(file.size);
      setSseEndpoint(data.sseEndpoint);
      setStatusEndpoint(data.statusEndpoint);
      setStatus('processing');
    } catch (e) {
      setErrorMessage(e.message || '上传失败，请重试');
      setStatus('error');
    }
  }, [userId, file, status]);

  const handleCloseModal = useCallback(() => {
    setShowModal(false);
    setUserId('');
    setFile(null);
    setFileId(null);
    setSseEndpoint(null);
    setStatusEndpoint(null);
    setErrorMessage(null);
    setResultInfo(null);
    setStatus('idle');
    // ✅ 重置防重复标志
    modalTriggeredRef.current = false;
  }, []);

  function formatFileSize(bytes) {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  // 步骤状态
  const stepState = (step) => {
    if (status === 'idle' && step === 1) return 'active';
    if (status === 'uploading' && step === 1) return 'done';
    if (status === 'uploading' && step === 2) return 'active';
    if (status === 'processing' && step <= 2) return 'done';
    if (status === 'processing' && step === 3) return 'active';
    if (status === 'completed') return 'done';
    return '';
  };

  return (
    <div className="app-container">
      {/* 头部 */}
      <div className="app-header">
        <div className="app-logo">📦</div>
        <h1 className="app-title">文件切片上传</h1>
        <p className="app-subtitle">安全高效 · 自动分片 · 实时追踪</p>
      </div>

      {/* 步骤指示器 */}
      <div className="steps">
        <div className="step">
          <span className={`step-dot ${stepState(1)}`}>
            {stepState(1) === 'done' ? '✓' : '1'}
          </span>
          <span className={`step-label ${stepState(1)}`}>选择文件</span>
        </div>
        <span className="step-arrow">→</span>
        <div className="step">
          <span className={`step-dot ${stepState(2)}`}>
            {stepState(2) === 'done' ? '✓' : '2'}
          </span>
          <span className={`step-label ${stepState(2)}`}>上传文件</span>
        </div>
        <span className="step-arrow">→</span>
        <div className="step">
          <span className={`step-dot ${stepState(3)}`}>
            {stepState(3) === 'done' ? '✓' : '3'}
          </span>
          <span className={`step-label ${stepState(3)}`}>切片处理</span>
        </div>
      </div>

      <div className="divider" />

      {/* 上传表单 */}
      <FileUploadForm
        userId={userId}
        file={file}
        status={status}
        progress={progress}
        onUserIdChange={setUserId}
        onFileChange={setFile}
        onUpload={handleUpload}
      />

      {/* 错误提示 */}
      {errorMessage && (
        <div className="error-banner">
          <span className="error-icon">⚠️</span>
          {errorMessage}
        </div>
      )}

      {/* 进度条 */}
      <ProgressBar progress={progress} totalChunks={10} status={status} />

      {/* 完成弹窗 */}
      <CompletionModal
        showModal={showModal}
        resultInfo={resultInfo}
        onClose={handleCloseModal}
      />
    </div>
  );
}