import React, { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import gsap from 'gsap';
import DriverSidebar from './DriverSidebar';

const DriverLayout = ({ children }) => {
  const location = useLocation();
  const layoutRef = useRef(null);

  useEffect(() => {
    // Subtle GSAP fade/slide for page content changes
    if (layoutRef.current) {
      gsap.fromTo(layoutRef.current, 
        { opacity: 0, y: 10 }, 
        { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' }
      );
    }
  }, [location.pathname]);

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#fdfbfb' }}>
      <DriverSidebar />
      <main 
        ref={layoutRef} 
        style={{ 
          flex: 1, 
          marginLeft: '260px', /* Sidebar width */
          padding: '40px', 
          maxWidth: '1200px',
          width: '100%'
        }}
      >
        {children}
      </main>
    </div>
  );
};

export default DriverLayout;
