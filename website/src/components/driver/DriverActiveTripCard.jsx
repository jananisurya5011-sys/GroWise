import React from 'react';
import { Navigation, ShieldAlert, FileText, XCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const DriverActiveTripCard = ({ activeOrder, onCancelActiveOrder, isDevMode }) => {
  const navigate = useNavigate();

  if (!activeOrder) return null;

  return (
    <div style={{
      backgroundColor: '#ffffff',
      borderRadius: '24px',
      padding: '24px',
      boxShadow: '0 12px 32px rgba(0,0,0,0.08)',
      border: '2px solid var(--terracotta-primary)',
      position: 'relative',
      overflow: 'hidden'
    }}>
      <div style={{
        position: 'absolute',
        top: 0, left: 0, width: '100%',
        backgroundColor: 'var(--terracotta-primary)',
        color: 'white',
        textAlign: 'center',
        padding: '6px 0',
        fontSize: '12px',
        fontWeight: 800,
        letterSpacing: '1px',
        textTransform: 'uppercase'
      }}>
        Active Trip In Progress
      </div>

      <div style={{ marginTop: '24px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{
          width: '64px', height: '64px',
          borderRadius: '20px',
          backgroundColor: 'var(--peach-background)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          marginBottom: '16px'
        }}>
          <Navigation size={32} color="var(--terracotta-primary)" />
        </div>
        
        <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 900, color: '#1a1a1a' }}>
          {activeOrder.cropName || 'Crop Pool'}
        </h3>
        <span style={{ 
          display: 'inline-block', 
          marginTop: '8px', 
          padding: '6px 12px', 
          backgroundColor: '#e8f5e9', 
          color: '#2e7d32', 
          borderRadius: '20px', 
          fontSize: '12px', 
          fontWeight: 700 
        }}>
          {activeOrder.status === 'EN_ROUTE_TO_PICKUP' ? 'Heading to Pickup' : 
           activeOrder.status === 'EN_ROUTE_TO_DROP' ? 'Heading to Destination' : 
           activeOrder.status.replace(/_/g, ' ')}
        </span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '32px' }}>
        <button
          onClick={() => {
            if (activeOrder?.orderId?.startsWith('GW-') || activeOrder?.orderId?.startsWith('DEAL-DON-')) {
              navigate('/driver/standard-delivery');
            } else {
              navigate('/driver/status');
            }
          }}
          style={{
            width: '100%', padding: '16px', borderRadius: '16px',
            backgroundColor: 'var(--terracotta-primary)', border: 'none',
            color: '#fff', fontSize: '16px', fontWeight: 800,
            cursor: 'pointer', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px',
            boxShadow: '0 4px 12px rgba(230, 81, 0, 0.2)'
          }}
        >
          <FileText size={20} /> View Details / Resume
        </button>

        {isDevMode && (
          <button
            onClick={() => onCancelActiveOrder(activeOrder)}
            style={{
              width: '100%', padding: '16px', borderRadius: '16px',
              backgroundColor: '#fff', border: '2px solid #ef4444',
              color: '#ef4444', fontSize: '16px', fontWeight: 800,
              cursor: 'pointer', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px'
            }}
          >
            <XCircle size={20} /> Cancel Trip (Dev Mode)
          </button>
        )}
      </div>

      {isDevMode && (
        <div style={{ 
          marginTop: '20px', 
          padding: '12px', 
          backgroundColor: '#fff5f5', 
          borderRadius: '12px', 
          display: 'flex', 
          gap: '8px', 
          alignItems: 'center',
          color: '#ef4444'
        }}>
          <ShieldAlert size={24} />
          <p style={{ margin: 0, fontSize: '12px', fontWeight: 600 }}>
            Developer Mode is ON. Declining this order will permanently hide it from you and release it to other drivers.
          </p>
        </div>
      )}
    </div>
  );
};

export default DriverActiveTripCard;
