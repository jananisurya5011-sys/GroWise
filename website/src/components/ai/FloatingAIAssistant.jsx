import React from 'react';
import { motion } from 'framer-motion';
import { MessageCircle } from 'lucide-react';

const FloatingAIAssistant = ({ onClick }) => {
  return (
    <motion.button
      initial={{ scale: 0, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
      onClick={onClick}
      style={{
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        width: '60px',
        height: '60px',
        borderRadius: '50%',
        background: '#E07A5F', // Terracotta
        border: '2px solid rgba(212, 175, 55, 0.6)', // Golden glow
        color: 'white',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'pointer',
        boxShadow: '0 8px 24px rgba(224, 122, 95, 0.4), 0 0 15px rgba(212, 175, 55, 0.4)',
        zIndex: 9990,
        outline: 'none',
      }}
      title="GroWise AI Assistant"
    >
      <MessageCircle size={28} />
    </motion.button>
  );
};

export default FloatingAIAssistant;
