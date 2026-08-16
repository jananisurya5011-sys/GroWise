import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import Logo from '../Logo';
import { Home, Wallet, UserCircle, LogOut, Settings } from 'lucide-react';
import SettingsPopup from '../shared/SettingsPopup';
import LogoutConfirmDialog from '../shared/LogoutConfirmDialog';

const DriverSidebar = () => {
  const { logout } = useAuth();
  const location = useLocation();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isLogoutConfirmOpen, setIsLogoutConfirmOpen] = useState(false);

  const navItems = [
    { path: '/home/driver', icon: <Home size={22} />, label: 'Home' },
    { path: '/driver/wallet', icon: <Wallet size={22} />, label: 'Wallet' },
    { path: '/profile/driver', icon: <UserCircle size={22} />, label: 'Profile' }
  ];

  return (
    <aside style={{ 
      width: '260px', 
      height: '100vh', 
      backgroundColor: '#fff', 
      borderRight: '1px solid #eee', 
      position: 'fixed', 
      top: 0, 
      left: 0, 
      display: 'flex', 
      flexDirection: 'column', 
      padding: '24px 0', 
      zIndex: 100 
    }}>
      <div style={{ padding: '0 24px', marginBottom: '40px', display: 'flex', alignItems: 'center', gap: '12px' }}>
        <Logo size={40} />
        <span style={{ fontSize: '24px', fontWeight: 800, color: 'var(--terracotta-primary)' }}>GroWise</span>
      </div>
      
      <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px', padding: '0 16px' }}>
        {navItems.map((item, idx) => {
          const isActive = location.pathname.startsWith(item.path.split('/')[1] === 'home' ? '/home/driver' : item.path);
          return (
            <Link 
              key={idx} 
              to={item.path}
              style={{
                display: 'flex', alignItems: 'center', gap: '16px', padding: '12px 16px',
                borderRadius: '12px', textDecoration: 'none',
                backgroundColor: isActive ? 'var(--peach-background)' : 'transparent',
                color: isActive ? 'var(--terracotta-primary)' : 'var(--text-muted)',
                fontWeight: isActive ? 700 : 500,
                transition: 'all 0.2s ease'
              }}
            >
              {item.icon}
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div style={{ padding: '0 24px', marginTop: 'auto', position: 'relative' }}>
        <button 
          onClick={() => setIsSettingsOpen(prev => !prev)} 
          style={{ 
            width: '100%', padding: '12px', background: '#f5f5f5', color: '#666', 
            border: 'none', borderRadius: '8px', fontWeight: 600, cursor: 'pointer', 
            display: 'flex', justifyContent: 'center', gap: '8px', alignItems: 'center',
            transition: 'all 0.2s ease'
          }}
          onMouseOver={(e) => { e.currentTarget.style.background = '#e5e7eb'; }}
          onMouseOut={(e) => { e.currentTarget.style.background = '#f5f5f5'; }}
        >
          <Settings size={18} /> Settings
        </button>
        <SettingsPopup 
          isOpen={isSettingsOpen} 
          onClose={() => setIsSettingsOpen(false)} 
          onLogoutClick={() => setIsLogoutConfirmOpen(true)}
        />
      </div>

      <LogoutConfirmDialog
        isOpen={isLogoutConfirmOpen}
        onClose={() => setIsLogoutConfirmOpen(false)}
        onConfirm={logout}
      />
    </aside>
  );
};

export default DriverSidebar;
