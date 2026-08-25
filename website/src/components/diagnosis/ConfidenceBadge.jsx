import React from 'react';

const ConfidenceBadge = ({ confidence, isOffline }) => {
  const percentage = (confidence * 100).toFixed(1);
  const color = confidence > 0.8 ? '#16a34a' : (confidence > 0.5 ? '#f59e0b' : '#ef4444');
  const bg = confidence > 0.8 ? '#dcfce7' : (confidence > 0.5 ? '#fef3c7' : '#fee2e2');

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
      <div style={{ 
        padding: '6px 12px', 
        backgroundColor: bg, 
        borderRadius: '12px', 
        display: 'inline-flex',
        alignItems: 'center',
        border: `1px solid ${color}30`
      }}>
        <span style={{ fontSize: '13px', fontWeight: 800, color, letterSpacing: '0.5px' }}>
          {percentage}% Confidence
        </span>
      </div>
      {isOffline && (
        <span style={{ fontSize: '11px', color: '#888', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
          (Local ML)
        </span>
      )}
    </div>
  );
};

export default ConfidenceBadge;
