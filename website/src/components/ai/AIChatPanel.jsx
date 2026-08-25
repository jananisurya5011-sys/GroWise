import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Send, Bot } from 'lucide-react';
import apiClient from '../../utils/apiClient';

const AIChatPanel = ({ isOpen, onClose, user, isMobile }) => {
  const [message, setMessage] = useState('');
  const [history, setHistory] = useState([]);
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);
  
  const panelWidth = isMobile ? '100vw' : '420px';

  // Always start with fresh conversation as requested by user
  useEffect(() => {
    if (isOpen) {
      setHistory([]);
      setMessage('');
      setIsTyping(false);
      // Auto focus textarea when opened
      setTimeout(() => textareaRef.current?.focus(), 100);
    }
  }, [isOpen]);

  // Auto-scroll
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [history, isTyping, message]); // Include message to scroll when textarea grows

  const handleSend = async (e) => {
    if (e) e.preventDefault();
    if (!message.trim()) return;

    const newHistory = [...history, { role: 'user', parts: message }];
    setHistory(newHistory);
    setMessage('');
    setIsTyping(true);
    
    // Reset textarea height
    if (textareaRef.current) textareaRef.current.style.height = 'auto';

    try {
      const res = await apiClient.post('/api/chat/general-chat', {
        message,
        history: history.slice(-10), // Send last 10 messages for context
        role: user?.role || 'user'
      });

      if (res.data.success) {
        setHistory(prev => [...prev, { role: 'model', parts: res.data.reply }]);
      } else {
        setHistory(prev => [...prev, { role: 'model', parts: "I'm having trouble connecting right now. Please try again later." }]);
      }
    } catch (err) {
      setHistory(prev => [...prev, { role: 'model', parts: "I'm having trouble connecting right now. Please try again later." }]);
    } finally {
      setIsTyping(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleTextareaChange = (e) => {
    setMessage(e.target.value);
    e.target.style.height = 'auto';
    e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`;
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Overlay for mobile */}
          {isMobile && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onClose}
              style={{
                position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
                background: 'rgba(0,0,0,0.4)', zIndex: 9998
              }}
            />
          )}

          <motion.div
            initial={{ x: '100%', opacity: 0.5 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: '100%', opacity: 0.5 }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            style={{
              position: 'fixed', top: 0, right: 0, bottom: 0, width: panelWidth,
              background: '#FDF5E6', // Cream
              boxShadow: '-12px 0 48px rgba(124, 61, 18, 0.15)',
              zIndex: 9999, display: 'flex', flexDirection: 'column',
              borderLeft: '1px solid rgba(212, 175, 55, 0.3)'
            }}
          >
            {/* HEADER */}
            <div style={{
              padding: '20px 24px', background: 'rgba(255, 255, 255, 0.85)', backdropFilter: 'blur(10px)',
              borderBottom: '1px solid rgba(212, 175, 55, 0.2)',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              boxShadow: '0 4px 12px rgba(124, 61, 18, 0.05)', zIndex: 2
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{ width: 44, height: 44, borderRadius: '50%', background: '#E07A5F', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', border: '2px solid rgba(212, 175, 55, 0.4)' }}>
                  <Bot size={24} />
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: 18, color: '#7C3D12', fontWeight: 800 }}>GroWise AI</h3>
                  <p style={{ margin: 0, fontSize: 13, color: '#E07A5F', fontWeight: 600 }}>Agricultural Assistant</p>
                  <p style={{ margin: 0, fontSize: 12, color: '#888', fontWeight: 500 }}>Always here to help</p>
                </div>
              </div>
              <button 
                onClick={onClose}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', padding: 8, color: '#7C3D12', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
              >
                <X size={24} />
              </button>
            </div>

            {/* CHAT AREA */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
              {history.length === 0 && (
                <div style={{ margin: 'auto 0', padding: '20px', background: '#fff', borderRadius: '20px', border: '1px solid rgba(212, 175, 55, 0.3)', boxShadow: '0 8px 24px rgba(124, 61, 18, 0.08)', textAlign: 'center' }}>
                  <Bot size={48} color="#E07A5F" style={{ marginBottom: 16 }} />
                  <h4 style={{ margin: '0 0 16px', color: '#7C3D12', fontWeight: 800, fontSize: 18 }}>Welcome to GroWise AI</h4>
                  <p style={{ margin: '0 0 16px', fontSize: 14, color: '#666', fontWeight: 500 }}>I can help you with:</p>
                  <ul style={{ textAlign: 'left', margin: 0, padding: '0 0 0 20px', color: '#444', fontSize: 14, lineHeight: 1.8, fontWeight: 500 }}>
                    <li>Crop Diseases</li>
                    <li>Fertilizers</li>
                    <li>Market Prices</li>
                    <li>Soil Health</li>
                    <li>Irrigation</li>
                    <li>Government Schemes</li>
                    <li>Crop Recommendations</li>
                    <li>GroWise Platform Support</li>
                  </ul>
                </div>
              )}

              {history.map((msg, idx) => {
                const isUser = msg.role === 'user';
                return (
                  <div key={idx} style={{
                    display: 'flex', gap: 12, alignItems: 'flex-end',
                    flexDirection: isUser ? 'row-reverse' : 'row'
                  }}>
                    {!isUser && (
                      <div style={{
                        width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
                        background: '#fff', border: '1px solid rgba(212, 175, 55, 0.3)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#E07A5F'
                      }}>
                        <Bot size={18} />
                      </div>
                    )}
                    <div style={{
                      padding: '12px 16px', borderRadius: 20,
                      background: isUser ? '#E07A5F' : '#fff',
                      color: isUser ? '#fff' : '#444',
                      border: isUser ? 'none' : '1px solid rgba(212, 175, 55, 0.2)',
                      boxShadow: '0 4px 12px rgba(124, 61, 18, 0.05)',
                      fontSize: 15, lineHeight: 1.5,
                      borderBottomRightRadius: isUser ? 4 : 20,
                      borderBottomLeftRadius: isUser ? 20 : 4,
                      maxWidth: '85%'
                    }}>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{msg.parts}</div>
                    </div>
                  </div>
                );
              })}

              {isTyping && (
                <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
                  <div style={{
                    width: 32, height: 32, borderRadius: '50%', background: '#fff',
                    border: '1px solid rgba(212, 175, 55, 0.3)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#E07A5F'
                  }}>
                    <Bot size={18} />
                  </div>
                  <div style={{
                    padding: '16px', borderRadius: 20, background: '#fff',
                    border: '1px solid rgba(212, 175, 55, 0.2)',
                    display: 'flex', gap: 6, alignItems: 'center',
                    borderBottomLeftRadius: 4
                  }}>
                    <motion.div animate={{ opacity: [0.4, 1, 0.4] }} transition={{ repeat: Infinity, duration: 1.4 }} style={{ width: 6, height: 6, borderRadius: '50%', background: '#E07A5F' }} />
                    <motion.div animate={{ opacity: [0.4, 1, 0.4] }} transition={{ repeat: Infinity, duration: 1.4, delay: 0.2 }} style={{ width: 6, height: 6, borderRadius: '50%', background: '#E07A5F' }} />
                    <motion.div animate={{ opacity: [0.4, 1, 0.4] }} transition={{ repeat: Infinity, duration: 1.4, delay: 0.4 }} style={{ width: 6, height: 6, borderRadius: '50%', background: '#E07A5F' }} />
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* INPUT AREA */}
            <div style={{
              padding: '16px 20px', background: '#fff', borderTop: '1px solid rgba(212, 175, 55, 0.2)',
              paddingBottom: `calc(16px + env(safe-area-inset-bottom))`
            }}>
              <div style={{
                display: 'flex', alignItems: 'flex-end', gap: 12,
                background: '#FDF5E6', borderRadius: 24, padding: '8px 12px 8px 20px',
                border: '1px solid rgba(212, 175, 55, 0.4)',
                boxShadow: 'inset 0 2px 4px rgba(124, 61, 18, 0.05)'
              }}>
                <textarea
                  ref={textareaRef}
                  value={message}
                  onChange={handleTextareaChange}
                  onKeyDown={handleKeyDown}
                  placeholder="Type your agricultural question..."
                  rows={1}
                  style={{
                    flex: 1, border: 'none', background: 'transparent',
                    outline: 'none', resize: 'none', fontSize: 15,
                    fontFamily: 'inherit', color: '#7C3D12', padding: '10px 0',
                    maxHeight: 120, minHeight: 20
                  }}
                />

                <button
                  onClick={handleSend}
                  disabled={!message.trim() || isTyping}
                  style={{
                    background: message.trim() && !isTyping ? '#E07A5F' : '#e5e7eb',
                    border: 'none', borderRadius: '50%', width: 40, height: 40,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#fff', cursor: message.trim() && !isTyping ? 'pointer' : 'not-allowed',
                    transition: 'all 0.2s', flexShrink: 0, marginBottom: '2px' // Keeps it perfectly aligned at the bottom
                  }}
                >
                  <Send size={18} style={{ marginLeft: 2 }} />
                </button>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default AIChatPanel;
