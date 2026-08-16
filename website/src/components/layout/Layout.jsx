import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import gsap from 'gsap';
import Logo from '../Logo';
import FarmerBottomNav from './FarmerBottomNav';
import { 
  Home, 
  Store, 
  HeartPulse, 
  Package, 
  Users, 
  ShoppingCart, 
  UserCircle,
  Settings,
  LogOut,
  Wallet,
  ShieldCheck,
  LayoutDashboard,
  MessageCircle
} from 'lucide-react';
import SettingsPopup from '../shared/SettingsPopup';
import LogoutConfirmDialog from '../shared/LogoutConfirmDialog';
import FloatingAIAssistant from '../ai/FloatingAIAssistant';
import AIChatPanel from '../ai/AIChatPanel';

const Layout = ({ children }) => {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [isMobile, setIsMobile] = useState(window.innerWidth <= 768);
  const [isAiChatOpen, setIsAiChatOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isLogoutConfirmOpen, setIsLogoutConfirmOpen] = useState(false);
  const layoutRef = useRef(null);

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth <= 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    // Subtle GSAP fade/slide for page content changes
    if (layoutRef.current) {
      gsap.fromTo(layoutRef.current, 
        { opacity: 0, y: 10 }, 
        { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' }
      );
    }
  }, [location.pathname]);

  // Determine Nav Items based on Role (Mimicking exact Kotlin NavBottom classes)
  const getNavItems = () => {
    const role = user?.role || 'user';
    const baseNav = [];
    const currentBase = role === 'ngo' ? '/home/user' : `/home/${role}`;

    // Home is implicitly the first tab for most, but let's strictly map them:
    if (role === 'admin') {
      baseNav.push({ path: currentBase, icon: <LayoutDashboard size={22} />, label: 'Dashboard' });
      baseNav.push({ path: '/admin/verify', icon: <ShieldCheck size={22} />, label: 'Verify' });
    } else if (role === 'farmer') {
      baseNav.push({ path: currentBase, icon: <Home size={22} />, label: 'Home' });
      baseNav.push({ path: '/deals', icon: <MessageCircle size={22} />, label: 'Deals' });
      baseNav.push({ path: '/track', icon: <Package size={22} />, label: 'Track' });
    } else if (role === 'driver') {
      baseNav.push({ path: currentBase, icon: <Home size={22} />, label: 'Home' });
      baseNav.push({ path: '/driver/deliveries', icon: <Package size={22} />, label: 'Deliveries' });
      baseNav.push({ path: '/driver/wallet', icon: <Wallet size={22} />, label: 'Wallet' });
    } else {
      // User / NGO
      baseNav.push({ path: currentBase, icon: <Home size={22} />, label: 'Home' });
      baseNav.push({ path: '/deals', icon: <MessageCircle size={22} />, label: 'Deals' });
      baseNav.push({ path: '/track', icon: <Package size={22} />, label: 'Track' });
    }

    // Always add Profile at the end
    baseNav.push({ path: `/profile/${role}`, icon: <UserCircle size={22} />, label: 'Profile' });

    return baseNav;
  };

  const navItems = getNavItems();

  const Sidebar = () => (
    <aside style={{ width: '260px', height: '100vh', backgroundColor: '#fff', borderRight: '1px solid #eee', position: 'fixed', top: 0, left: 0, display: 'flex', flexDirection: 'column', padding: '24px 0', zIndex: 100 }}>
      <div style={{ padding: '0 24px', marginBottom: '40px', display: 'flex', alignItems: 'center', gap: '12px' }}>
        <Logo size={40} />
        <span style={{ fontSize: '24px', fontWeight: 800, color: 'var(--terracotta-primary)' }}>GroWise</span>
      </div>
      
      <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px', padding: '0 16px' }}>
        {navItems.map((item, idx) => {
          const isActive = location.pathname === item.path;
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
        <button onClick={() => setIsSettingsOpen(prev => !prev)} style={{ width: '100%', padding: '12px', background: '#f5f5f5', color: '#666', border: 'none', borderRadius: '8px', fontWeight: 600, cursor: 'pointer', display: 'flex', justifyContent: 'center', gap: '8px', alignItems: 'center' }}>
          <Settings size={18} /> Settings
        </button>
        <SettingsPopup 
          isOpen={isSettingsOpen} 
          onClose={() => setIsSettingsOpen(false)} 
          onLogoutClick={() => setIsLogoutConfirmOpen(true)}
        />
      </div>
    </aside>
  );

  const BottomNav = () => (
    <nav style={{ position: 'fixed', bottom: 0, left: 0, width: '100%', backgroundColor: '#fff', borderTop: '1px solid #eee', display: 'flex', justifyContent: 'space-around', padding: '12px 0', paddingBottom: 'calc(12px + env(safe-area-inset-bottom))', zIndex: 1000 }}>
      {navItems.map((item, idx) => {
        const isActive = location.pathname === item.path;
        return (
          <Link 
            key={idx} 
            to={item.path}
            style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px', textDecoration: 'none',
              color: isActive ? 'var(--terracotta-primary)' : 'var(--text-muted)',
            }}
          >
            <div style={{ filter: isActive ? 'none' : 'grayscale(100%) opacity(0.6)' }}>
              {item.icon}
            </div>
            <span style={{ fontSize: '11px', fontWeight: isActive ? 700 : 500 }}>{item.label}</span>
          </Link>
        )
      })}
    </nav>
  );

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#fdfbfb' }}>
      {!isMobile && <Sidebar />}
      
      <main ref={layoutRef} style={{ flex: 1, marginLeft: isMobile ? 0 : '260px', padding: isMobile ? '24px 16px 120px' : '40px', maxWidth: '1200px' }}>
        {children}
      </main>

      {isMobile && user?.role === 'farmer' && <FarmerBottomNav onOpenAiChat={() => setIsAiChatOpen(true)} />}
      {isMobile && user?.role !== 'farmer' && <BottomNav />}

      {(user?.role === 'farmer' || user?.role === 'user' || user?.role === 'ngo') && (
        <>
          <FloatingAIAssistant onClick={() => setIsAiChatOpen(true)} />
          <AIChatPanel isOpen={isAiChatOpen} onClose={() => setIsAiChatOpen(false)} user={user} isMobile={isMobile} />
        </>
      )}

      <LogoutConfirmDialog
        isOpen={isLogoutConfirmOpen}
        onClose={() => setIsLogoutConfirmOpen(false)}
        onConfirm={logout}
      />
    </div>
  );
};

export default Layout;
