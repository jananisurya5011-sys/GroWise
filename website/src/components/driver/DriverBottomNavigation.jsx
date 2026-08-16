import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Wallet, UserCircle } from 'lucide-react';

const DriverBottomNavigation = () => {
  const location = useLocation();

  const navItems = [
    { path: '/home/driver', icon: <Home size={24} strokeWidth={2.5} />, label: 'Home' },
    { path: '/driver/wallet', icon: <Wallet size={24} strokeWidth={2.5} />, label: 'Wallet' },
    { path: '/profile/driver', icon: <UserCircle size={24} strokeWidth={2.5} />, label: 'Profile' }
  ];

  return (
    <nav style={{
      position: 'fixed',
      bottom: 0,
      left: 0,
      width: '100%',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      backdropFilter: 'blur(10px)',
      WebkitBackdropFilter: 'blur(10px)',
      borderTop: '1px solid rgba(0, 0, 0, 0.05)',
      display: 'flex',
      justifyContent: 'space-around',
      padding: '12px 0',
      paddingBottom: 'calc(12px + env(safe-area-inset-bottom))',
      zIndex: 1000,
      boxShadow: '0 -4px 20px rgba(0,0,0,0.03)'
    }}>
      {navItems.map((item, idx) => {
        const isActive = location.pathname.startsWith(item.path.split('/')[1] === 'home' ? '/home/driver' : item.path);
        return (
          <Link 
            key={idx} 
            to={item.path}
            style={{
              display: 'flex', 
              flexDirection: 'column', 
              alignItems: 'center', 
              gap: '6px', 
              textDecoration: 'none',
              color: isActive ? 'var(--terracotta-primary)' : '#9ca3af',
              transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
              transform: isActive ? 'translateY(-2px)' : 'translateY(0)'
            }}
          >
            <div style={{ 
              position: 'relative',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              {item.icon}
              {isActive && (
                <div style={{
                  position: 'absolute',
                  bottom: '-12px',
                  width: '4px',
                  height: '4px',
                  borderRadius: '50%',
                  backgroundColor: 'var(--terracotta-primary)',
                  boxShadow: '0 0 8px var(--terracotta-primary)'
                }} />
              )}
            </div>
            <span style={{ 
              fontSize: '11px', 
              fontWeight: isActive ? 800 : 600,
              letterSpacing: '0.3px'
            }}>
              {item.label}
            </span>
          </Link>
        )
      })}
    </nav>
  );
};

export default DriverBottomNavigation;
