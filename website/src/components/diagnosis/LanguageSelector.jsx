import React from 'react';
import { Globe } from 'lucide-react';

const LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'ta', name: 'Tamil' },
  { code: 'hi', name: 'Hindi' },
  { code: 'ml', name: 'Malayalam' },
  { code: 'kn', name: 'Kannada' },
  { code: 'te', name: 'Telugu' }
];

const LanguageSelector = ({ selectedLanguage, onLanguageChange, disabled }) => {
  return (
    <div style={{ 
      display: 'flex', 
      alignItems: 'center', 
      gap: '12px', 
      backgroundColor: '#fff', 
      padding: '12px 16px', 
      borderRadius: '16px', 
      border: '1px solid #eee',
      boxShadow: '0 4px 12px rgba(0,0,0,0.02)'
    }}>
      <div style={{ 
        width: '36px', height: '36px', borderRadius: '50%', 
        backgroundColor: '#f0fdf4', display: 'flex', 
        alignItems: 'center', justifyContent: 'center' 
      }}>
        <Globe size={18} color="#22c55e" />
      </div>
      
      <div style={{ flex: 1 }}>
        <p style={{ margin: 0, fontSize: '12px', fontWeight: 600, color: '#666', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
          Diagnosis Language
        </p>
        <select 
          value={selectedLanguage}
          onChange={(e) => onLanguageChange(e.target.value)}
          disabled={disabled}
          style={{
            width: '100%',
            padding: '4px 0',
            border: 'none',
            outline: 'none',
            fontSize: '15px',
            fontWeight: 800,
            color: '#333',
            backgroundColor: 'transparent',
            cursor: disabled ? 'not-allowed' : 'pointer'
          }}
        >
          {LANGUAGES.map(lang => (
            <option key={lang.code} value={lang.code}>{lang.name}</option>
          ))}
        </select>
      </div>
    </div>
  );
};

export default LanguageSelector;
