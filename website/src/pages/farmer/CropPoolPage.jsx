import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Truck } from 'lucide-react';
import CropPoolCard from '../../components/common/CropPoolCard';

const CropPoolPage = () => {
  const navigate = useNavigate();

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '32px 24px 80px 24px' }}>
      
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '20px', marginBottom: '40px' }}>
        <button 
          onClick={() => navigate(-1)} 
          style={{ 
            background: '#fff', border: '1px solid #f0f0f0', borderRadius: '50%', width: '48px', height: '48px',
            flexShrink: 0, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 4px 12px rgba(0,0,0,0.04)', transition: 'all 0.2s ease', marginTop: '2px'
          }}
          onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.05)'; e.currentTarget.style.boxShadow = '0 6px 16px rgba(0,0,0,0.08)'; }}
          onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.04)'; }}
        >
          <ArrowLeft size={24} color="#333" />
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: '32px', fontWeight: 900, color: 'var(--terracotta-primary)', display: 'flex', alignItems: 'center', gap: '12px' }}>
            Logistics Pool <Truck size={28} color="var(--terracotta-primary)" opacity={0.8} />
          </h1>
          <p style={{ margin: '6px 0 0 0', color: '#666', fontSize: '15px', fontWeight: 500 }}>
            Share transportation costs by co-loading with nearby farmers.
          </p>
        </div>
      </div>

      <CropPoolCard />
    </div>
  );
};

export default CropPoolPage;
