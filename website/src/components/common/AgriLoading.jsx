import React from 'react';

const AgriLoading = ({ size = 48, color = 'var(--terracotta-primary)', className = '' }) => {
  return (
    <svg 
      width={size} 
      height={size} 
      viewBox="0 0 24 24" 
      fill="none" 
      xmlns="http://www.w3.org/2000/svg"
      className={`animate-spin ${className}`}
      style={{ animation: 'spin 1.5s linear infinite' }}
    >
      <style>
        {`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}
      </style>
      <path 
        d="M12,2C6.48,2 2,6.48 2,12C2,17.52 6.48,22 12,22C17.52,22 22,17.52 22,12C22,6.48 17.52,2 12,2ZM12,18C11.45,18 11,17.55 11,17C11,15.2 12.2,13.6 13.8,12.4C12.4,12.2 10.9,12.7 9.8,13.8C9.4,14.2 8.8,14.2 8.4,13.8C8,13.4 8,12.8 8.4,12.4C10.1,10.7 12.6,10.1 14.8,10.7C14.4,8.5 13,6.6 11,5.6C10.5,5.35 10.3,4.75 10.55,4.25C10.8,3.75 11.4,3.55 11.9,3.8C14.7,5.2 16.5,7.95 16.9,11C18.6,12.5 19.6,14.7 19.6,17C19.6,17.55 19.15,18 18.6,18L12,18Z" 
        fill={color} 
      />
    </svg>
  );
};

export default AgriLoading;
