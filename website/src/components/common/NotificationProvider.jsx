import React from 'react';
import { Toaster } from 'react-hot-toast';

const NotificationProvider = () => {
  return (
    <Toaster 
      position="bottom-right"
      toastOptions={{
        duration: 4000,
        style: {
          fontFamily: 'system-ui, sans-serif',
          fontWeight: 600,
          borderRadius: '12px',
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
        }
      }}
    />
  );
};

export default NotificationProvider;
