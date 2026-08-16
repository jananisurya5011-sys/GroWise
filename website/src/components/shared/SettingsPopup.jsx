import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { KeyRound, LogOut, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const SettingsPopup = ({ isOpen, onClose, onLogoutClick }) => {
  const navigate = useNavigate();

  // Handle ESC key
  React.useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [isOpen, onClose]);

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Transparent overlay for click-outside */}
          <div
            style={{
              position: 'fixed',
              top: 0, left: 0, right: 0, bottom: 0,
              zIndex: 9998
            }}
            onClick={onClose}
          />
          
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 10 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            style={{
              position: 'absolute',
              bottom: 'calc(100% + 12px)',
              left: '50%',
              transform: 'translateX(-50%)',
              width: 240,
              background: 'rgba(253, 245, 230, 0.85)', // Cream with opacity
              backdropFilter: 'blur(16px)',
              WebkitBackdropFilter: 'blur(16px)',
              borderRadius: 16,
              border: '1px solid rgba(212, 175, 55, 0.3)', // Golden outline
              boxShadow: '0 8px 32px rgba(124, 61, 18, 0.1)', // Warm shadow
              overflow: 'hidden',
              zIndex: 9999,
              display: 'flex',
              flexDirection: 'column'
            }}
          >
            <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid rgba(212, 175, 55, 0.15)' }}>
              <span style={{ fontWeight: 800, color: '#7C3D12', fontSize: 16 }}>Settings</span>
              <button 
                onClick={onClose}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', padding: 4, display: 'flex', alignItems: 'center', color: '#7C3D12' }}
              >
                <X size={16} />
              </button>
            </div>
            
            <div style={{ padding: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <button
                onClick={() => {
                  onClose();
                  navigate('/change-password');
                }}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px',
                  background: 'transparent', border: 'none', cursor: 'pointer',
                  color: '#7C3D12', fontWeight: 600, fontSize: 14, borderRadius: 8,
                  transition: 'background 0.2s',
                  textAlign: 'left'
                }}
                onMouseOver={(e) => e.currentTarget.style.background = 'rgba(124, 61, 18, 0.05)'}
                onMouseOut={(e) => e.currentTarget.style.background = 'transparent'}
              >
                <KeyRound size={18} /> Change Password
              </button>
              
              <button
                onClick={() => {
                  onClose();
                  onLogoutClick();
                }}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px',
                  background: 'transparent', border: 'none', cursor: 'pointer',
                  color: '#d32f2f', fontWeight: 600, fontSize: 14, borderRadius: 8,
                  transition: 'background 0.2s',
                  textAlign: 'left'
                }}
                onMouseOver={(e) => e.currentTarget.style.background = 'rgba(211, 47, 47, 0.05)'}
                onMouseOut={(e) => e.currentTarget.style.background = 'transparent'}
              >
                <LogOut size={18} /> Logout
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default SettingsPopup;
