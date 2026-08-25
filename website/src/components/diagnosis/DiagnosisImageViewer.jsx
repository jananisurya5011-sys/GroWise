import React from 'react';
import { X } from 'lucide-react';

const DiagnosisImageViewer = ({ isOpen, onClose, imageUrl, altText = "Diagnosis Image" }) => {
  if (!isOpen || !imageUrl) return null;

  return (
    <div style={{ 
      position: 'fixed', 
      top: 0, left: 0, 
      width: '100vw', height: '100vh', 
      backgroundColor: 'rgba(0,0,0,0.8)', 
      zIndex: 10000, 
      display: 'flex', 
      alignItems: 'center', 
      justifyContent: 'center',
      padding: '24px'
    }}>
      <div style={{ position: 'relative', maxWidth: '90vw', maxHeight: '90vh' }}>
        <button 
          onClick={onClose} 
          style={{ 
            position: 'absolute', 
            top: '-20px', right: '-20px', 
            background: '#ef4444', 
            border: 'none', 
            borderRadius: '50%', 
            width: '40px', height: '40px', 
            display: 'flex', alignItems: 'center', justifyContent: 'center', 
            cursor: 'pointer', 
            boxShadow: '0 4px 12px rgba(239, 68, 68, 0.4)', 
            zIndex: 10 
          }}
        >
          <X size={24} color="#fff" />
        </button>
        <img 
          src={imageUrl} 
          alt={altText}
          style={{ 
            maxWidth: '100%', 
            maxHeight: '90vh', 
            objectFit: 'contain',
            borderRadius: '16px',
            boxShadow: '0 24px 48px rgba(0,0,0,0.5)'
          }} 
        />
      </div>
    </div>
  );
};

export default DiagnosisImageViewer;
