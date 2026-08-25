import React, { useState } from 'react';
import { Image as ImageIcon, Trash2 } from 'lucide-react';
import { getDiagnosisImageUrl } from '../../utils/diagnosisImage';
import { confirmDialog } from '../common/ConfirmationDialog';

const DiagnosisHistoryCard = ({ item, onClick, onDelete }) => {
  const [isDeleting, setIsDeleting] = useState(false);
  // item can be either Offline or AI diagnosis
  const isAI = item.diagnosisType === 'ai';
  
  const imgUrl = getDiagnosisImageUrl(item.imagePath);

  const handleDelete = async (e) => {
    e.stopPropagation();
    const isConfirmed = await confirmDialog("Delete Diagnosis", "Are you sure you want to delete this diagnosis?");
    if (isConfirmed) {
      setIsDeleting(true);
      await onDelete(item);
      setIsDeleting(false);
    }
  };

  return (
    <div 
      style={{ 
        minWidth: '260px', 
        maxWidth: '260px', 
        backgroundColor: '#fdfbfb', 
        borderRadius: '16px', 
        padding: '16px', 
        border: '1px solid #eee', 
        boxShadow: '0 4px 12px rgba(0,0,0,0.04)',
        display: 'flex',
        flexDirection: 'column',
        opacity: isDeleting ? 0.5 : 1,
        transition: 'opacity 0.2s'
      }}
    >
      <div style={{ width: '100%', height: '140px', backgroundColor: '#eee', borderRadius: '12px', marginBottom: '12px', overflow: 'hidden', position: 'relative' }}>
        {imgUrl ? (
          <img src={imgUrl} alt="Crop" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        ) : (
          <ImageIcon size={24} color="#ccc" style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }} />
        )}
        <div style={{ position: 'absolute', top: '8px', right: '8px', backgroundColor: 'rgba(0,0,0,0.7)', color: '#fff', fontSize: '10px', padding: '4px 8px', borderRadius: '12px', fontWeight: 700 }}>
          {new Date(item.date || item.createdAt || item.timestamp).toLocaleDateString()}
        </div>
        <div style={{ position: 'absolute', top: '8px', left: '8px', backgroundColor: isAI ? '#8b5cf6' : '#10b981', color: '#fff', fontSize: '10px', padding: '4px 8px', borderRadius: '12px', fontWeight: 700 }}>
          {isAI ? 'AI Vision' : 'Offline ML'}
        </div>
      </div>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <h4 style={{ margin: '0 0 4px 0', fontSize: '16px', fontWeight: 800, color: 'var(--terracotta-primary)', flex: 1 }}>
          {item.disease || 'Unknown'}
        </h4>
        {onDelete && (
          <button 
            onClick={handleDelete}
            disabled={isDeleting}
            style={{ 
              background: 'none', border: 'none', cursor: isDeleting ? 'not-allowed' : 'pointer', 
              padding: '4px', display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: '#ef4444', opacity: 0.7, transition: 'opacity 0.2s'
            }}
            onMouseEnter={(e) => e.currentTarget.style.opacity = 1}
            onMouseLeave={(e) => e.currentTarget.style.opacity = 0.7}
            title="Delete Diagnosis"
          >
            <Trash2 size={16} />
          </button>
        )}
      </div>
      
      {item.cropName && (
        <p style={{ margin: '0 0 8px 0', fontSize: '12px', color: '#666', fontWeight: 600 }}>
          Crop: {item.cropName}
        </p>
      )}

      {item.confidence !== undefined && (
        <p style={{ margin: '0 0 8px 0', fontSize: '12px', color: '#666', fontWeight: 600 }}>
          Confidence: {(item.confidence * 100).toFixed(1)}%
        </p>
      )}
      
      <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', marginTop: 'auto' }}>
        <button 
          onClick={() => onClick(item)}
          style={{ flex: 1, padding: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', border: '1px solid #eee', backgroundColor: '#fff', borderRadius: '8px', fontSize: '12px', fontWeight: 600, color: '#333', cursor: 'pointer', transition: 'background-color 0.2s' }}
          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f9f9f9'}
          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
        >
          View Full Diagnosis
        </button>
      </div>
      
      <p style={{ margin: 0, fontSize: '13px', color: '#555', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
        {item.details?.summary || item.details?.cause || item.remedy || 'Maintain standard care.'}
      </p>
    </div>
  );
};

export default DiagnosisHistoryCard;
