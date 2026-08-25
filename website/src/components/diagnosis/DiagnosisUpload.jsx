import React, { useRef, useState } from 'react';
import { UploadCloud } from 'lucide-react';

const DiagnosisUpload = ({ onFileSelect, previewUrl, isAnalyzing, disabled }) => {
  const fileInputRef = useRef(null);
  const [isDragging, setIsDragging] = useState(false);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file && onFileSelect) {
      onFileSelect(file);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    if (!disabled && !isAnalyzing) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    if (disabled || isAnalyzing) return;
    
    const file = e.dataTransfer.files[0];
    if (file && file.type.startsWith('image/') && onFileSelect) {
      onFileSelect(file);
    }
  };

  const handleClick = () => {
    if (!disabled && !isAnalyzing) {
      fileInputRef.current?.click();
    }
  };

  return (
    <div 
      onClick={handleClick}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      style={{ 
        width: '100%', 
        aspectRatio: '1', 
        backgroundColor: isDragging ? '#f0fdf4' : '#fdfbf9', 
        borderRadius: '24px', 
        border: `2px dashed ${isDragging ? '#22c55e' : '#e6dcd3'}`, 
        display: 'flex', 
        flexDirection: 'column', 
        alignItems: 'center', 
        justifyContent: 'center', 
        cursor: disabled || isAnalyzing ? 'not-allowed' : 'pointer', 
        overflow: 'hidden', 
        position: 'relative',
        transition: 'all 0.3s ease',
        boxShadow: isDragging ? '0 8px 32px rgba(34, 197, 94, 0.1)' : 'none'
      }}
    >
      <input 
        type="file" 
        accept="image/jpeg, image/png, image/jpg" 
        ref={fileInputRef} 
        onChange={handleFileChange} 
        style={{ display: 'none' }} 
        disabled={disabled || isAnalyzing}
        id="diagnosis-upload-input"
      />
      
      {previewUrl ? (
        <>
          <img 
            id="diagnosis-upload-preview"
            src={previewUrl} 
            alt="Preview" 
            crossOrigin="anonymous" 
            style={{ 
              width: '100%', 
              height: '100%', 
              objectFit: 'contain', // maintain aspect ratio without stretching
              backgroundColor: '#000'
            }} 
          />
          {isAnalyzing && (
            <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', background: 'linear-gradient(180deg, rgba(240,173,78,0) 0%, rgba(240,173,78,0.3) 50%, rgba(240,173,78,0) 100%)', animation: 'scan 2s infinite linear' }}>
              <div style={{ width: '100%', height: '4px', backgroundColor: '#f0ad4e', boxShadow: '0 0 16px #f0ad4e' }}></div>
            </div>
          )}
        </>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '24px', textAlign: 'center' }}>
          <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: '#fff2e6', display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'transform 0.3s ease', transform: isDragging ? 'scale(1.1)' : 'scale(1)' }}>
            <UploadCloud size={40} color="#d9534f" />
          </div>
          <div>
            <h3 style={{ margin: '0 0 8px 0', fontSize: '20px', color: '#333', fontWeight: 800 }}>Upload Plant Image</h3>
            <p style={{ margin: 0, fontSize: '14px', color: '#888', fontWeight: 500, lineHeight: 1.5 }}>
              Drag & Drop<br/>or<br/>
              <span style={{ color: '#d9534f', fontWeight: 700 }}>Click to Browse</span>
            </p>
          </div>
          <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
            <span style={{ fontSize: '11px', padding: '4px 8px', backgroundColor: '#f1f5f9', color: '#64748b', borderRadius: '12px', fontWeight: 600 }}>JPG</span>
            <span style={{ fontSize: '11px', padding: '4px 8px', backgroundColor: '#f1f5f9', color: '#64748b', borderRadius: '12px', fontWeight: 600 }}>PNG</span>
            <span style={{ fontSize: '11px', padding: '4px 8px', backgroundColor: '#f1f5f9', color: '#64748b', borderRadius: '12px', fontWeight: 600 }}>JPEG</span>
          </div>
        </div>
      )}
      
      <style>{`
        @keyframes scan {
          0% { transform: translateY(-100%); }
          100% { transform: translateY(100%); }
        }
      `}</style>
    </div>
  );
};

export default DiagnosisUpload;
