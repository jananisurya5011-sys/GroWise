import React, { useState, useEffect } from 'react';
import { Navigation, Clock, Scale, Truck, Check, X, ShieldAlert } from 'lucide-react';
import { formatCurrency } from '../../utils/constants';

const formatPostedTime = (timestamp) => {
  const date = new Date(timestamp);
  const now = new Date();
  const diffTime = Math.abs(now - date);
  const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
  
  const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  
  if (diffDays === 0 && now.getDate() === date.getDate()) {
    return `Posted Today • ${timeStr}`;
  } else if (diffDays === 1 || (diffDays === 0 && now.getDate() !== date.getDate())) {
    return `Posted Yesterday • ${timeStr}`;
  } else {
    const options = { month: 'short', day: 'numeric' };
    return `Posted ${date.toLocaleDateString('en-US', options)} • ${timeStr}`;
  }
};

const formatWaitTime = (minutes) => {
  if (minutes < 60) return `Waiting ${minutes} mins`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `Waiting ${hours} hr${hours > 1 ? 's' : ''}`;
  const days = Math.floor(hours / 24);
  return `Waiting ${days} day${days > 1 ? 's' : ''}`;
};

const DriverLoadCard = React.memo(({ order, onAccept, onDecline }) => {
  const [timeDiffMinutes, setTimeDiffMinutes] = useState(0);

  useEffect(() => {
    const calcTime = () => {
      const now = Date.now();
      const diff = Math.floor((now - (order.timestamp || now)) / 60000);
      setTimeDiffMinutes(Math.max(0, diff));
    };
    calcTime();
    const interval = setInterval(calcTime, 60000); // Update every minute
    return () => clearInterval(interval);
  }, [order.timestamp]);

  const isHighPriority = timeDiffMinutes >= 20;
  const isPool = order.orderId && order.orderId.includes('POOL');
  const cropName = isPool ? 'Crop Pool' : (order.cropName || 'Crop');
  
  const totalPayment = isPool 
    ? (parseFloat(order.totalPayment || 0) + parseFloat(order.coLoaderPayment || 0))
    : parseFloat(order.transportFare || 0);

  return (
    <div style={{
      backgroundColor: '#ffffff',
      borderRadius: '24px',
      padding: '24px',
      marginBottom: '20px',
      boxShadow: isHighPriority 
        ? '0 12px 32px rgba(239, 68, 68, 0.12)' 
        : '0 8px 24px rgba(0,0,0,0.06)',
      border: isHighPriority ? '2px solid #ef4444' : '1px solid rgba(0,0,0,0.05)',
      transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
      position: 'relative',
      overflow: 'hidden',
      display: 'flex',
      flexDirection: 'column'
    }}>
      {/* Priority Banner */}
      {isHighPriority && (
        <div style={{
          position: 'absolute',
          top: 0, left: 0, width: '100%',
          backgroundColor: '#ef4444',
          color: 'white',
          textAlign: 'center',
          padding: '6px 0',
          fontSize: '12px',
          fontWeight: 800,
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          gap: '6px',
          letterSpacing: '1px',
          textTransform: 'uppercase'
        }}>
          <ShieldAlert size={14} /> HIGH PRIORITY - {formatWaitTime(timeDiffMinutes)}
        </div>
      )}

      <div style={{ marginTop: isHighPriority ? '24px' : '0', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
          <div style={{
            width: '64px', height: '64px',
            borderRadius: '16px',
            backgroundColor: 'var(--peach-background)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'inset 0 2px 4px rgba(230,81,0,0.1)'
          }}>
            {order.cropImage ? (
              <img src={order.cropImage} alt={cropName} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '16px' }} />
            ) : (
              <Truck size={32} color="var(--terracotta-primary)" />
            )}
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 900, color: '#111827', letterSpacing: '-0.5px' }}>{cropName}</h3>
            <div style={{ display: 'flex', gap: '16px', marginTop: '6px' }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', color: '#4b5563', fontWeight: 600 }}>
                <Scale size={16} color="var(--terracotta-primary)" /> {order.weightKg || order.totalWeightKg || 0} kg
              </span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', color: '#4b5563', fontWeight: 600 }}>
                <Navigation size={16} color="var(--terracotta-primary)" /> {(order.distanceToPickup || 0).toFixed(1)} km away
              </span>
            </div>
          </div>
        </div>

        <div style={{ textAlign: 'right' }}>
          <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.5px' }}>Earnings</span>
          <span style={{ display: 'block', fontSize: '28px', fontWeight: 900, color: '#059669', letterSpacing: '-1px' }}>
            {formatCurrency(totalPayment.toFixed(0))}
          </span>
        </div>
      </div>

      {/* Badges */}
      <div style={{ display: 'flex', gap: '10px', marginTop: '20px', flexWrap: 'wrap' }}>
        <span style={{
          backgroundColor: '#f3f4f6', color: '#374151', padding: '6px 12px',
          borderRadius: '24px', fontSize: '13px', fontWeight: 700,
          display: 'flex', alignItems: 'center', gap: '6px',
          border: '1px solid #e5e7eb'
        }}>
          <Truck size={14} /> {order.vehicleType || 'Any'}
        </span>
        <span style={{
          backgroundColor: isHighPriority ? '#fee2e2' : 'var(--peach-background)', 
          color: isHighPriority ? '#dc2626' : 'var(--terracotta-primary)', 
          padding: '6px 12px',
          borderRadius: '24px', fontSize: '13px', fontWeight: 700,
          display: 'flex', alignItems: 'center', gap: '6px'
        }}>
          <Clock size={14} /> {formatPostedTime(order.timestamp)}
        </span>
      </div>

      <div style={{ marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '12px', backgroundColor: '#f9fafb', padding: '16px', borderRadius: '16px' }}>
        {/* Addresses */}
        <div style={{ display: 'flex', gap: '16px', position: 'relative' }}>
          <div style={{ position: 'absolute', left: '9px', top: '24px', bottom: '24px', width: '2px', backgroundColor: '#d1d5db' }}></div>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px', zIndex: 1 }}>
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'var(--golden-yellow)', border: '5px solid #fff', boxShadow: '0 0 0 1px #d1d5db' }} />
            <div style={{ flex: 1 }} />
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'var(--terracotta-primary)', border: '5px solid #fff', boxShadow: '0 0 0 1px #d1d5db' }} />
          </div>
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <span style={{ fontSize: '12px', fontWeight: 800, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{isPool ? 'Host Pickup' : 'Pickup Address'}</span>
              <p style={{ margin: '4px 0 0', fontSize: '15px', color: '#1f2937', fontWeight: 600, lineHeight: 1.5 }}>
                {order.pickupAddress || 'No pickup address'}
              </p>
            </div>
            {isPool && order.coLoaderEmail && (
              <div>
                <span style={{ fontSize: '12px', fontWeight: 800, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Co-loader Pickup</span>
                <p style={{ margin: '4px 0 0', fontSize: '15px', color: '#1f2937', fontWeight: 600, lineHeight: 1.5 }}>
                  {order.coLoaderPickupAddress || 'N/A'}
                </p>
              </div>
            )}
            <div>
              <span style={{ fontSize: '12px', fontWeight: 800, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{isPool ? 'Host Drop' : 'Drop Address'}</span>
              <p style={{ margin: '4px 0 0', fontSize: '15px', color: '#1f2937', fontWeight: 600, lineHeight: 1.5 }}>
                {order.dropAddress || 'No drop address'}
              </p>
            </div>
            {isPool && order.coLoaderEmail && (
              <div>
                <span style={{ fontSize: '12px', fontWeight: 800, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Co-loader Drop</span>
                <p style={{ margin: '4px 0 0', fontSize: '15px', color: '#1f2937', fontWeight: 600, lineHeight: 1.5 }}>
                  {order.coLoaderDropAddress || 'N/A'}
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: '16px', marginTop: '24px' }}>
        <button
          onClick={() => onDecline(order)}
          style={{
            flex: 1, padding: '16px', borderRadius: '16px',
            backgroundColor: '#fff', border: '2px solid #e5e7eb',
            color: '#6b7280', fontSize: '16px', fontWeight: 800,
            cursor: 'pointer', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px',
            transition: 'all 0.2s ease'
          }}
          onMouseOver={(e) => { e.currentTarget.style.borderColor = '#ef4444'; e.currentTarget.style.color = '#ef4444'; e.currentTarget.style.backgroundColor = '#fef2f2'; }}
          onMouseOut={(e) => { e.currentTarget.style.borderColor = '#e5e7eb'; e.currentTarget.style.color = '#6b7280'; e.currentTarget.style.backgroundColor = '#fff'; }}
        >
          <X size={20} strokeWidth={3} /> Decline
        </button>
        <button
          onClick={() => onAccept(order)}
          style={{
            flex: 2, padding: '16px', borderRadius: '16px',
            backgroundColor: 'var(--terracotta-primary)', border: 'none',
            color: '#fff', fontSize: '16px', fontWeight: 900,
            cursor: 'pointer', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px',
            boxShadow: '0 8px 20px rgba(230, 81, 0, 0.25)',
            transition: 'transform 0.2s ease'
          }}
          onMouseOver={(e) => e.currentTarget.style.transform = 'translateY(-2px)'}
          onMouseOut={(e) => e.currentTarget.style.transform = 'translateY(0)'}
        >
          <Check size={20} strokeWidth={3} /> Accept Order
        </button>
      </div>
    </div>
  );
});

export default DriverLoadCard;
