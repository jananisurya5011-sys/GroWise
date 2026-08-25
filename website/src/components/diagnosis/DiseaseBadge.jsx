import React from 'react';

const DiseaseBadge = ({ disease, isHealthy }) => {
  const color = isHealthy ? '#059669' : '#d9534f';
  const bg = isHealthy ? '#ecfdf5' : '#fef2f2';

  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ margin: 0, fontSize: '13px', color: '#666', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
        Detected Condition
      </p>
      <div style={{ 
        marginTop: '6px',
        padding: '12px 16px', 
        backgroundColor: bg, 
        borderRadius: '12px', 
        border: `1px solid ${color}30`,
        display: 'inline-block'
      }}>
        <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 800, color }}>
          {disease}
        </h2>
      </div>
    </div>
  );
};

export default DiseaseBadge;
