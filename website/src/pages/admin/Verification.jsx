import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import { ShieldAlert, ChevronRight, FileText, User } from 'lucide-react';

const AdminVerification = () => {
  const navigate = useNavigate();
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const containerRef = useRef(null);

  useEffect(() => {
    fetchPendingDrivers();
  }, []);

  useEffect(() => {
    if (!loading && containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, x: -10 }, 
        { opacity: 1, x: 0, duration: 0.4, stagger: 0.05, ease: 'power2.out' }
      );
    }
  }, [loading]);

  const fetchPendingDrivers = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get('/api/admin/pending-drivers');
      if (data.success) {
        setDrivers(data.drivers);
      }
    } catch (error) {
      console.error("Failed to fetch pending drivers", error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <div style={{ width: '48px', height: '48px', borderRadius: '12px', backgroundColor: '#fef3c7', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ShieldAlert size={24} color="#d97706" />
        </div>
        <div>
          <h1 style={{ fontSize: '28px', fontWeight: 800, color: '#111', margin: '0 0 4px 0' }}>Verification Hub</h1>
          <p style={{ color: '#666', fontSize: '14px', margin: 0 }}>Review and approve driver registrations</p>
        </div>
      </header>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--terracotta-primary)', fontWeight: 600 }}>Loading Queue...</div>
      ) : (
        <div ref={containerRef} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {drivers.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#888', backgroundColor: '#fff', borderRadius: '16px', border: '1px dashed #ccc' }}>
              No pending verifications. You're all caught up!
            </div>
          ) : (
            drivers.map((driver, idx) => (
              <div 
                key={idx} 
                onClick={() => navigate(`/admin/verify/${encodeURIComponent(driver.email)}`, { state: { driverData: driver } })}
                style={{ 
                  backgroundColor: '#fff', 
                  padding: '20px 24px', 
                  borderRadius: '16px', 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'space-between', 
                  border: '1px solid #f0f0f0', 
                  boxShadow: '0 2px 8px rgba(0,0,0,0.02)',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  transform: 'scale(1)'
                }}
                onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.01)'; e.currentTarget.style.borderColor = 'var(--peach-background)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.borderColor = '#f0f0f0'; }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <div style={{ width: '56px', height: '56px', borderRadius: '50%', backgroundColor: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '1px solid #e2e8f0', overflow: 'hidden' }}>
                    {driver.profileImageUrl ? (
                      <img src={driver.profileImageUrl} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    ) : (
                      <User size={24} color="#94a3b8" />
                    )}
                  </div>
                  <div>
                    <h3 style={{ margin: '0 0 4px 0', fontSize: '18px', fontWeight: 700, color: '#222' }}>{driver.name || 'Unknown Name'}</h3>
                    <p style={{ margin: 0, fontSize: '13px', color: '#666', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <FileText size={14} color="#94a3b8" />
                      {driver.vehicleType || 'Vehicle Not Logged'}
                    </p>
                  </div>
                </div>
                
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <div style={{ textAlign: 'right' }}>
                    <span style={{ display: 'inline-block', padding: '4px 10px', backgroundColor: '#fffbeb', color: '#d97706', borderRadius: '12px', fontSize: '12px', fontWeight: 700 }}>
                      Action Required
                    </span>
                    <p style={{ margin: '6px 0 0 0', fontSize: '12px', color: '#94a3b8' }}>Submitted Recently</p>
                  </div>
                  <ChevronRight size={20} color="#cbd5e1" />
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default AdminVerification;
