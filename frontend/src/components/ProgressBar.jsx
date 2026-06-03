/**
 * 进度条组件 — 美观版
 */
export default function ProgressBar({ progress, totalChunks = 10, status }) {
  if (status !== 'processing' && status !== 'completed') {
    return null;
  }

  const percent = Math.round((progress / totalChunks) * 100);
  const isComplete = progress >= totalChunks && status === 'completed';

  return (
    <div className="progress-panel">
      <div className="progress-header">
        <div className="progress-status">
          <span className="pulse" />
          {isComplete ? '处理完成' : '切片处理中'}
        </div>
        <span className="progress-percent">{percent}%</span>
      </div>

      <div className="progress-track">
        <div
          className={`progress-fill ${isComplete ? 'complete' : ''}`}
          style={{ width: `${percent}%` }}
        />
      </div>

      <div className="progress-detail">
        <span>已完成 {progress} / {totalChunks} 片</span>
        <span>{totalChunks - progress} 片剩余</span>
      </div>
    </div>
  );
}
