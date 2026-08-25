import React from 'react';
import toast from 'react-hot-toast';

export const confirmDialog = (title, message = "Are you sure?") => {
  return new Promise((resolve) => {
    toast.custom(
      (t) => (
        <div 
          style={{
            background: 'rgba(0,0,0,0.5)',
            position: 'fixed',
            top: 0, left: 0, right: 0, bottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 99999,
            opacity: t.visible ? 1 : 0,
            transition: 'opacity 0.2s ease-in-out'
          }}
        >
          <div style={{
            background: '#fff',
            padding: '24px',
            borderRadius: '16px',
            boxShadow: '0 24px 48px rgba(0,0,0,0.1)',
            maxWidth: '400px',
            width: '90%',
            fontFamily: 'system-ui, sans-serif'
          }}>
            <h3 style={{ margin: '0 0 12px 0', fontSize: '20px', fontWeight: 800, color: '#111' }}>{title}</h3>
            <p style={{ margin: '0 0 24px 0', fontSize: '15px', color: '#555', lineHeight: 1.5 }}>{message}</p>
            
            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <button 
                onClick={() => {
                  toast.dismiss(t.id);
                  resolve(false);
                }}
                style={{
                  padding: '10px 20px',
                  background: '#f1f5f9',
                  color: '#475569',
                  border: 'none',
                  borderRadius: '8px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Cancel
              </button>
              <button 
                onClick={() => {
                  toast.dismiss(t.id);
                  resolve(true);
                }}
                style={{
                  padding: '10px 20px',
                  background: '#ef4444',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '8px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      ),
      { duration: Infinity }
    );
  });
};
