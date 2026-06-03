/**
 * 完成弹窗组件 — 美观版（V1.2 新增 MD5 和校验结果）
 */
import { useState, useCallback } from 'react';
import { fetchVerify } from '../api/fileApi';

export default function CompletionModal({ showModal, resultInfo, onClose }) {
  const [verifyResult, setVerifyResult] = useState(null);
  const [verifying, setVerifying] = useState(false);

  const handleVerify = useCallback(async () => {
    setVerifying(true);
    setVerifyResult(null);
    try {
      const data = await fetchVerify(resultInfo.fileId);
      setVerifyResult(data);
    } catch (e) {
      setVerifyResult({ verified: false, message: e.message || '校验请求失败' });
    } finally {
      setVerifying(false);
    }
  }, [resultInfo?.fileId]);

  if (!showModal || !resultInfo) return null;

  const hasFailures = resultInfo.failedChunks > 0;
  const allSuccess = resultInfo.failedChunks === 0;
  const percent = Math.round(
    (resultInfo.completedChunks / resultInfo.totalChunks) * 100
  );

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        {/* 顶部横幅 */}
        <div className={`modal-banner ${allSuccess ? 'success' : 'partial'}`}>
          <div className="modal-icon">{allSuccess ? '✅' : '⚠️'}</div>
          <h2>
            {allSuccess
              ? '文件上传完成'
              : `完成（${resultInfo.failedChunks} 片失败）`}
          </h2>
        </div>

        {/* 信息区 */}
        <div className="modal-body">
          <div className="modal-info-grid">
            <div className="info-item">
              <span className="info-label">👤 用户</span>
              <span className="info-value">{resultInfo.userId}</span>
            </div>
            <div className="info-item">
              <span className="info-label">📄 文件</span>
              <span className="info-value">{resultInfo.fileName}</span>
            </div>
            <div className="info-item">
              <span className="info-label">📦 大小</span>
              <span className="info-value">{resultInfo.fileSize}</span>
            </div>
            <div className="info-item">
              <span className="info-label">🧩 切片</span>
              <span className="info-value">
                <span className="success">{resultInfo.completedChunks}</span>
                {' / '}
                {resultInfo.totalChunks}
              </span>
            </div>
            {hasFailures && (
              <div className="info-item">
                <span className="info-label">❌ 失败</span>
                <span className="info-value danger">
                  {resultInfo.failedChunks}
                </span>
              </div>
            )}
            <div className="info-item full-width">
              <span className="info-label">🔑 文件 ID</span>
              <span className="info-value file-id">{resultInfo.fileId}</span>
            </div>
          </div>

          {/* MD5 校验信息 */}
          {resultInfo.fileMd5 && (
            <div className="modal-md5-section">
              <div className="md5-row">
                <span className="md5-label">🔐 MD5</span>
                <span className="md5-value" title={resultInfo.fileMd5}>
                  {resultInfo.fileMd5}
                </span>
              </div>
              <div className="md5-row">
                <span className="md5-label">
                  {resultInfo.verified ? '✅ 校验' : '⚠️ 校验'}
                </span>
                <span
                  className={`md5-verify-badge ${resultInfo.verified ? 'pass' : 'fail'}`}
                >
                  {resultInfo.verified ? '通过' : '未通过'}
                </span>
              </div>

              {/* 手动校验按钮 */}
              {!verifyResult && (
                <button
                  className="verify-btn"
                  onClick={handleVerify}
                  disabled={verifying}
                >
                  {verifying ? '⏳ 校验中...' : '🔍 服务端重新校验'}
                </button>
              )}

              {/* 手动校验结果 */}
              {verifyResult && (
                <div
                  className={`verify-result ${verifyResult.verified ? 'pass' : 'fail'}`}
                >
                  <div className="verify-result-header">
                    {verifyResult.verified ? '✅' : '❌'}{' '}
                    {verifyResult.message}
                  </div>
                  {verifyResult.reCalcMd5 && (
                    <>
                      <div className="verify-result-row">
                        原始 MD5: {verifyResult.fileMd5}
                      </div>
                      <div className="verify-result-row">
                        重算 MD5: {verifyResult.reCalcMd5}
                      </div>
                    </>
                  )}
                  {verifyResult.failedChunks && (
                    <div className="verify-result-row">
                      失败切片: {verifyResult.failedChunks.join(', ')}
                    </div>
                  )}
                  <button
                    className="verify-btn"
                    onClick={handleVerify}
                    disabled={verifying}
                  >
                    {verifying ? '⏳ 重新校验...' : '🔄 再次校验'}
                  </button>
                </div>
              )}
            </div>
          )}

          {/* 进度条 */}
          <div className="modal-progress-section">
            <div className="progress-track">
              <div
                className="progress-fill"
                style={{
                  width: `${percent}%`,
                  background: hasFailures
                    ? 'linear-gradient(90deg, #FF4D4F, #FF7875)'
                    : 'linear-gradient(90deg, #52C41A, #73D13D)',
                }}
              />
            </div>
          </div>
        </div>

        {/* 按钮区 */}
        <div className="modal-actions">
          <button className="modal-btn modal-btn-secondary" onClick={onClose}>
            重新上传
          </button>
          <button className="modal-btn modal-btn-primary" onClick={onClose}>
            好的
          </button>
        </div>
      </div>
    </div>
  );
}
