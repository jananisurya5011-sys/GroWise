import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import Logo from '../components/Logo';

const Splash = () => {
  const navigate = useNavigate();

  useEffect(() => {
    const timer = setTimeout(() => {
      navigate('/login');
    }, 2000); // Exactly 2 seconds

    return () => clearTimeout(timer);
  }, [navigate]);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.8 }}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        width: '100vw',
      }}
    >
      <Logo size={110} />
      <h1 style={{ marginTop: '24px', color: 'var(--terracotta-primary)', fontSize: '2.5rem' }}>GroWise</h1>
      <p style={{ marginTop: '8px', color: 'var(--text-muted)', fontSize: '1.1rem', fontWeight: 500 }}>
        Cultivating Intelligence, Harvesting Tomorrow.
      </p>
    </motion.div>
  );
};

export default Splash;
