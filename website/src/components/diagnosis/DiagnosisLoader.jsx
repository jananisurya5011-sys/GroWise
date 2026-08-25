import React from 'react';
import { Stethoscope, Loader2 } from 'lucide-react';

const DiagnosisLoader = ({ isAnalyzing, onClick, disabled, text = "Analyze Image", analyzingText = "Analyzing..." }) => {
  return (
    <button 
      onClick={onClick}
      disabled={disabled || isAnalyzing}
      style={{
        width: '100%',
        padding: '16px',
        backgroundColor: disabled ? '#ccc' : 'var(--terracotta-primary)',
        color: '#fff',
        border: 'none',
        borderRadius: '16px',
        fontSize: '16px',
        fontWeight: 800,
        cursor: disabled || isAnalyzing ? 'not-allowed' : 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '12px',
        boxShadow: disabled || isAnalyzing ? 'none' : '0 8px 24px rgba(217, 83, 79, 0.3)',
        transition: 'all 0.3s ease',
        opacity: disabled ? 0.7 : 1
      }}
      onMouseEnter={(e) => {
        if (!disabled && !isAnalyzing) {
          e.currentTarget.style.transform = 'translateY(-2px)';
          e.currentTarget.style.boxShadow = '0 12px 32px rgba(217, 83, 79, 0.4)';
        }
      }}
      onMouseLeave={(e) => {
        if (!disabled && !isAnalyzing) {
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow = '0 8px 24px rgba(217, 83, 79, 0.3)';
        }
      }}
    >
      {isAnalyzing ? (
        <>
          <Loader2 size={24} className="animate-spin" style={{ animation: 'spin 1s linear infinite' }} />
          <span>{analyzingText}</span>
        </>
      ) : (
        <>
          <Stethoscope size={24} />
          <span>{text}</span>
        </>
      )}
      
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </button>
  );
};

export default DiagnosisLoader;
