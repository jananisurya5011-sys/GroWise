import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { LogOut } from 'lucide-react';

const LogoutConfirmDialog = ({ isOpen, onClose, onConfirm }) => {
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
        <div
          style={{
            position: 'fixed',
            top: 0, left: 0, right: 0, bottom: 0,
            zIndex: 10000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'rgba(0, 0, 0, 0.4)',
            backdropFilter: 'blur(8px)',
            WebkitBackdropFilter: 'blur(8px)',
          }}
          onClick={onClose}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.9, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.9, y: 20 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            onClick={(e) => e.stopPropagation()} // Prevent click-outside when clicking inside modal
            style={{
              width: '90%',
              maxWidth: 400,
              background: '#FDF5E6', // Cream
              borderRadius: 24,
              padding: 32,
              border: '1px solid rgba(212, 175, 55, 0.3)', // Golden outline
              boxShadow: '0 24px 48px rgba(124, 61, 18, 0.15)',
              textAlign: 'center',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center'
            }}
          >
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: 'rgba(211, 47, 47, 0.1)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              marginBottom: 20
            }}>
              <LogOut size={32} color="#d32f2f" />
            </div>

            <h2 style={{ margin: '0 0 12px', fontSize: 24, fontWeight: 800, color: '#7C3D12' }}>Confirm Logout</h2>
            <p style={{ margin: '0 0 32px', fontSize: 16, color: '#666', lineHeight: 1.5, fontWeight: 500 }}>
              Are you sure you want to logout? You will need to sign in again to access your account.
            </p>

            <div style={{ display: 'flex', gap: 16, width: '100%' }}>
              <button
                onClick={onClose}
                style={{
                  flex: 1, padding: '14px 24px', borderRadius: 12,
                  background: 'transparent', border: '2px solid rgba(124, 61, 18, 0.2)',
                  color: '#7C3D12', fontWeight: 700, fontSize: 16, cursor: 'pointer',
                  transition: 'all 0.2s'
                }}
                onMouseOver={(e) => e.currentTarget.style.background = 'rgba(124, 61, 18, 0.05)'}
                onMouseOut={(e) => e.currentTarget.style.background = 'transparent'}
              >
                No, cancel
              </button>
              
              <button
                onClick={() => {
                  onConfirm();
                  onClose();
                }}
                style={{
                  flex: 1, padding: '14px 24px', borderRadius: 12,
                  background: '#E07A5F', // Terracotta
                  border: 'none',
                  color: 'white', fontWeight: 700, fontSize: 16, cursor: 'pointer',
                  boxShadow: '0 4px 12px rgba(224, 122, 95, 0.3)',
                  transition: 'all 0.2s'
                }}
                onMouseOver={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)';
                  e.currentTarget.style.boxShadow = '0 6px 16px rgba(224, 122, 95, 0.4)';
                }}
                onMouseOut={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(224, 122, 95, 0.3)';
                }}
              >
                Yes, logout
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};

export default LogoutConfirmDialog;
