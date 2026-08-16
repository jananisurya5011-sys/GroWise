import React, { useState, useEffect, useRef } from 'react';
import apiClient from '../utils/apiClient';
import { useAuth } from '../contexts/AuthContext';
import gsap from 'gsap';
import { X, Send, Bot, User, Loader2 } from 'lucide-react';

const AiChatModal = ({ isOpen, onClose }) => {
  const { user } = useAuth();
  const [messages, setMessages] = useState([
    { role: 'model', parts: 'Hello! I am GroWise AI. How can I assist you with your farm today?' }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [language, setLanguage] = useState('English');
  
  const overlayRef = useRef(null);
  const containerRef = useRef(null);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      gsap.fromTo(overlayRef.current, { opacity: 0 }, { opacity: 1, duration: 0.3 });
      gsap.fromTo(containerRef.current, { y: '100%' }, { y: '0%', duration: 0.4, ease: 'power3.out' });
    }
  }, [isOpen]);

  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleClose = () => {
    gsap.to(containerRef.current, { y: '100%', duration: 0.3, ease: 'power3.in' });
    gsap.to(overlayRef.current, { opacity: 0, duration: 0.3, onComplete: onClose });
  };

  const handleSend = async () => {
    if (!input.trim()) return;

    const userMessage = { role: 'user', parts: input };
    const newMessages = [...messages, userMessage];
    setMessages(newMessages);
    setInput('');
    setIsTyping(true);

    try {
      // Map history to standard gemini API roles (model / user)
      const formattedHistory = messages.map(m => ({
        role: m.role,
        parts: m.parts
      }));

      const { data } = await apiClient.post('/api/chat-ai/general-chat', {
        message: input,
        history: formattedHistory,
        language: language,
        role: user?.role || 'farmer'
      });

      if (data.success) {
        setMessages([...newMessages, { role: 'model', parts: data.reply }]);
      } else {
        setMessages([...newMessages, { role: 'model', parts: 'Sorry, I encountered an error.' }]);
      }
    } catch (error) {
      console.error("Chat error", error);
      setMessages([...newMessages, { role: 'model', parts: 'Unable to reach AI right now.' }]);
    } finally {
      setIsTyping(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div 
      ref={overlayRef}
      style={{
        position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
        backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 9999,
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end'
      }}
    >
      {/* Click outside to close */}
      <div style={{ flex: 1 }} onClick={handleClose} />

      <div 
        ref={containerRef}
        style={{
          width: '100%', height: '80vh', maxWidth: '600px', margin: '0 auto',
          backgroundColor: '#fff', borderTopLeftRadius: '24px', borderTopRightRadius: '24px',
          display: 'flex', flexDirection: 'column', boxShadow: '0 -10px 40px rgba(0,0,0,0.2)',
          overflow: 'hidden'
        }}
      >
        {/* Header */}
        <div style={{ padding: '20px', borderBottom: '1px solid #eee', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'var(--peach-background)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: 'var(--golden-yellow)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Bot size={24} color="#fff" />
            </div>
            <div>
              <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: 'var(--terracotta-primary)' }}>GroWise AI</h2>
              <p style={{ margin: 0, fontSize: '12px', color: '#666', fontWeight: 500 }}>Your Smart Farming Assistant</p>
            </div>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <select 
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              style={{ padding: '6px 12px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '13px', outline: 'none' }}
            >
              <option value="English">English</option>
              <option value="Hindi">Hindi</option>
              <option value="Tamil">Tamil</option>
            </select>
            <button onClick={handleClose} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
              <X size={24} color="#555" />
            </button>
          </div>
        </div>

        {/* Chat Body */}
        <div style={{ flex: 1, padding: '20px', overflowY: 'auto', backgroundColor: '#fdfbfb', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {messages.map((msg, idx) => (
            <div key={idx} style={{ display: 'flex', gap: '12px', alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start', maxWidth: '85%' }}>
              {msg.role === 'model' && (
                <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: 'var(--golden-yellow)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Bot size={16} color="#fff" />
                </div>
              )}
              
              <div style={{ 
                padding: '12px 16px', 
                borderRadius: '16px', 
                backgroundColor: msg.role === 'user' ? 'var(--terracotta-primary)' : '#fff',
                color: msg.role === 'user' ? '#fff' : '#333',
                border: msg.role === 'model' ? '1px solid #eee' : 'none',
                boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
                fontSize: '15px',
                lineHeight: 1.5,
                borderTopRightRadius: msg.role === 'user' ? '4px' : '16px',
                borderTopLeftRadius: msg.role === 'model' ? '4px' : '16px'
              }}>
                {msg.parts}
              </div>
            </div>
          ))}
          
          {isTyping && (
            <div style={{ display: 'flex', gap: '12px', alignSelf: 'flex-start' }}>
              <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: 'var(--golden-yellow)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Bot size={16} color="#fff" />
              </div>
              <div style={{ padding: '12px 16px', borderRadius: '16px', backgroundColor: '#fff', border: '1px solid #eee', display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Loader2 size={16} color="#888" style={{ animation: 'spin 1s linear infinite' }} />
                <span style={{ fontSize: '13px', color: '#888' }}>AI is thinking...</span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        <div style={{ padding: '16px 20px', borderTop: '1px solid #eee', backgroundColor: '#fff', display: 'flex', gap: '12px', alignItems: 'center' }}>
          <input 
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Ask about crops, diseases, or markets..."
            style={{
              flex: 1, padding: '14px 20px', borderRadius: '24px', border: '1px solid #ddd', backgroundColor: '#f9f9f9', fontSize: '15px', outline: 'none'
            }}
          />
          <button 
            onClick={handleSend}
            disabled={!input.trim() || isTyping}
            style={{ 
              width: '48px', height: '48px', borderRadius: '50%', backgroundColor: input.trim() ? 'var(--terracotta-primary)' : '#ccc', 
              display: 'flex', alignItems: 'center', justifyContent: 'center', border: 'none', cursor: input.trim() ? 'pointer' : 'not-allowed',
              transition: 'background-color 0.2s'
            }}
          >
            <Send size={20} color="#fff" style={{ marginLeft: '-2px' }} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default AiChatModal;
