import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Handshake, Truck, UserCircle, Bot } from 'lucide-react';

const FarmerBottomNav = ({ onOpenAiChat }) => {
  const location = useLocation();
  const currentPath = location.pathname;

  const navItems = [
    { path: '/home/farmer', icon: <Home size={24} />, label: 'Home' },
    { path: '/deals', icon: <Handshake size={24} />, label: 'Deals' },
    // Spacer for the center FAB
    { isSpacer: true },
    { path: '/track', icon: <Truck size={24} />, label: 'Track' },
    { path: '/profile/farmer', icon: <UserCircle size={24} />, label: 'Profile' },
  ];

  return (
    <div style={{ position: 'fixed', bottom: 0, left: 0, width: '100%', height: '100px', backgroundColor: 'transparent', zIndex: 1000, display: 'flex', alignItems: 'flex-end' }}>
      
      {/* Background Container (Curved Top) */}
      <div style={{ width: '100%', height: '76px', backgroundColor: '#fff', borderTopLeftRadius: '32px', borderTopRightRadius: '32px', boxShadow: '0 -4px 20px rgba(0,0,0,0.06)', position: 'absolute', bottom: 0, left: 0 }} />

      {/* Nav Items Container */}
      <nav style={{ width: '100%', height: '76px', position: 'relative', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 12px', paddingBottom: 'env(safe-area-inset-bottom)' }}>
        {navItems.map((item, idx) => {
          if (item.isSpacer) {
            return <div key="spacer" style={{ width: '72px' }} />;
          }

          const isActive = currentPath === item.path;
          return (
            <Link 
              key={idx} 
              to={item.path}
              style={{
                flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textDecoration: 'none',
                color: isActive ? 'var(--terracotta-primary)' : 'rgba(102, 102, 102, 0.6)', cursor: 'pointer'
              }}
            >
              <div style={{ filter: isActive ? 'none' : 'grayscale(100%) opacity(0.8)', transition: 'all 0.2s' }}>
                {item.icon}
              </div>
              {isActive ? (
                <div style={{ width: '5px', height: '5px', borderRadius: '50%', backgroundColor: 'var(--terracotta-primary)', marginTop: '4px' }} />
              ) : (
                <span style={{ fontSize: '11px', fontWeight: 600, marginTop: '2px' }}>{item.label}</span>
              )}
            </Link>
          );
        })}
      </nav>

      {/* Center AI FAB */}
      <div 
        onClick={onOpenAiChat}
        style={{
          position: 'absolute',
          top: '-12px',
          left: '50%',
          transform: 'translateX(-50%)',
          width: '64px',
          height: '64px',
          borderRadius: '50%',
          backgroundColor: 'var(--golden-yellow)',
          boxShadow: '0 8px 16px rgba(242, 163, 58, 0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          zIndex: 1001,
          transition: 'transform 0.2s ease',
        }}
        onMouseEnter={(e) => e.currentTarget.style.transform = 'translateX(-50%) scale(1.05)'}
        onMouseLeave={(e) => e.currentTarget.style.transform = 'translateX(-50%) scale(1)'}
      >
        <Bot size={30} color="#fff" />
      </div>
    </div>
  );
};

export default FarmerBottomNav;
