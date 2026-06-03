/**
 * 文件上传表单组件 — 美观版
 */
export default function FileUploadForm({
  userId,
  file,
  status,
  progress,
  onUserIdChange,
  onFileChange,
  onUpload,
}) {
  const isDisabled = status === 'uploading' || status === 'processing';
  const isIdle = status === 'idle';
  const canUpload = isIdle && userId.trim() && file;

  function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  function getFileIcon(name) {
    if (!name) return '📄';
    const ext = name.split('.').pop().toLowerCase();
    if (['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp'].includes(ext)) return '🖼️';
    if (['mp4', 'mov', 'avi', 'mkv'].includes(ext)) return '🎬';
    if (['mp3', 'wav', 'flac'].includes(ext)) return '🎵';
    if (['pdf'].includes(ext)) return '📕';
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '🗜️';
    if (['doc', 'docx'].includes(ext)) return '📝';
    if (['xls', 'xlsx'].includes(ext)) return '📊';
    return '📄';
  }

  return (
    <div className="upload-form">
      {/* 用户 ID */}
      <div className="form-group">
        <label htmlFor="userId">
          <span className="label-icon">👤</span> 用户 ID
        </label>
        <input
          id="userId"
          type="text"
          placeholder="请输入您的用户 ID"
          value={userId}
          onChange={(e) => onUserIdChange(e.target.value)}
          disabled={isDisabled}
        />
      </div>

      {/* 文件选择 */}
      <div className="form-group">
        <label>
          <span className="label-icon">📁</span> 选择文件
        </label>
        <div
          className={`file-drop-zone ${isDisabled ? 'disabled' : ''} ${file ? 'has-file' : ''}`}
        >
          <input
            id="fileInput"
            type="file"
            className="file-input-hidden"
            onChange={(e) => onFileChange(e.target.files[0] || null)}
            disabled={isDisabled}
          />
          {file ? (
            <div className="file-selected">
              <span className="file-icon">{getFileIcon(file.name)}</span>
              <div className="file-meta">
                <div className="file-name">{file.name}</div>
                <div className="file-size">{formatFileSize(file.size)}</div>
              </div>
            </div>
          ) : (
            <div className="file-placeholder">
              <span className="upload-icon">☁️</span>
              <span className="main-text">点击选择文件或拖拽到此处</span>
              <span className="hint-text">支持任意格式，最大 2GB</span>
            </div>
          )}
        </div>
      </div>

      {/* 上传按钮 */}
      <button
        className={`upload-btn ${status}`}
        onClick={onUpload}
        disabled={
          status === 'uploading' ||
          status === 'processing' ||
          !canUpload
        }
      >
        {status === 'uploading' && <span className="btn-spinner" />}
        {status === 'idle' && <span className="btn-icon">🚀</span>}
        {status === 'uploading' && '上传中...'}
        {status === 'processing' && `处理中 ${progress}/10`}
        {status === 'completed' && '✅ 重新上传'}
        {status === 'error' && '🔄 重试上传'}
        {status === 'idle' && '开始上传'}
      </button>
    </div>
  );
}
