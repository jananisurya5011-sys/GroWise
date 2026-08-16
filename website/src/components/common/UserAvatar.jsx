import React from 'react';

const UserAvatar = ({ src, alt = "Avatar", size = 48, style = {}, onError }) => {
  const finalSrc = src ? (src.startsWith('http') ? src : `${import.meta.env.VITE_API_URL || 'http://localhost:5000'}${src}`) : '/app_logo.svg';

  const handleError = (e) => {
    e.target.onerror = null;
    e.target.src = '/app_logo.svg';
    if (onError) onError(e);
  };

  return (
    <div style={{ 
      width: size, 
      height: size, 
      borderRadius: '50%', 
      overflow: 'hidden', 
      backgroundColor: '#f3f4f6',
      flexShrink: 0,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      border: '1px solid #e5e7eb',
      ...style 
    }}>
      <img 
        src={finalSrc} 
        alt={alt} 
        style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
        onError={handleError}
      />
    </div>
  );
};

export default UserAvatar;
