import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { ArrowLeft, UploadCloud, Stethoscope, Save, Loader2, Image as ImageIcon } from 'lucide-react';
import axios from 'axios';

const FarmerDiagnose = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [language, setLanguage] = useState('en');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [result, setResult] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const fileInputRef = useRef(null);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
      setResult(null); // Reset previous result
    }
  };

  const handleDiagnose = async () => {
    if (!selectedFile) {
      alert("Please select a crop image first.");
      return;
    }
    
    setIsAnalyzing(true);
    
    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('language', language);
    
    console.log("FormData entries:");
    for (let [key, value] of formData.entries()) {
      console.log(key, value);
    }

    try {
      const token = localStorage.getItem('token');
      // Using a clean axios request explicitly omitting Content-Type 
      // so the browser automatically sets the multipart boundary.
      const response = await axios.post('/api/crop-doctor/diagnose', formData, {
        headers: {
          'Authorization': `Bearer ${token}`
        },
        withCredentials: false
      });
      const data = response.data;
      
      if (data.success) {
        setResult(data);
      } else {
        alert(data.error || "Failed to analyze image.");
      }
    } catch (error) {
      console.error("DIAGNOSE API ERROR:", error, error.response);
      const errorMsg = error.response?.data?.error || error.message || "Unknown Network Error";
      alert(`Network error: ${errorMsg}`);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleSave = async () => {
    if (!result || !user?.email) return;
    setIsSaving(true);
    try {
      const { data } = await apiClient.post('/api/crop-doctor/save-diagnosis', {
        email: user.email,
        disease: result.disease,
        remedy: result.remedy,
        imagePath: result.imagePath,
        mode: 'Online',
        language: language
      });
      if (data.success) {
        alert("Saved to your Diagnostic History!");
        navigate('/home/farmer');
      } else {
        alert("Failed to save.");
      }
    } catch (error) {
      console.error(error);
      alert("Error saving diagnosis.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/home/farmer')} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 800, color: '#222' }}>AI Crop Doctor</h1>
          <p style={{ margin: 0, fontSize: '14px', color: '#666' }}>Upload a photo for instant disease diagnosis.</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        {/* Upload Section */}
        <div style={{ flex: '1 1 300px', backgroundColor: '#fff', borderRadius: '24px', padding: '24px', border: '1px solid #eee', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
          <div 
            onClick={() => fileInputRef.current?.click()}
            style={{ width: '100%', height: '240px', backgroundColor: '#f9f9f9', borderRadius: '16px', border: '2px dashed #ccc', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', overflow: 'hidden', marginBottom: '20px' }}
          >
            {previewUrl ? (
              <img src={previewUrl} alt="Preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              <>
                <UploadCloud size={48} color="#ccc" style={{ marginBottom: '16px' }} />
                <span style={{ fontSize: '15px', color: '#888', fontWeight: 600 }}>Tap to upload leaf photo</span>
              </>
            )}
          </div>
          <input type="file" accept="image/*" ref={fileInputRef} onChange={handleFileChange} style={{ display: 'none' }} />
          
          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#555', marginBottom: '8px' }}>Response Language</label>
            <select 
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px', outline: 'none' }}
            >
              <option value="en">English</option>
              <option value="hi">Hindi</option>
              <option value="ta">Tamil</option>
            </select>
          </div>

          <button 
            onClick={handleDiagnose}
            disabled={!selectedFile || isAnalyzing}
            style={{ width: '100%', padding: '16px', backgroundColor: selectedFile ? '#10b981' : '#ccc', color: '#fff', border: 'none', borderRadius: '12px', fontSize: '16px', fontWeight: 800, cursor: selectedFile ? 'pointer' : 'not-allowed', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
          >
            {isAnalyzing ? <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> : <Stethoscope size={20} />}
            {isAnalyzing ? 'Analyzing...' : 'Diagnose Crop'}
          </button>
        </div>

        {/* Result Section */}
        {result && (
          <div style={{ flex: '1 1 300px', backgroundColor: '#fff', borderRadius: '24px', padding: '24px', border: '2px solid #10b981', boxShadow: '0 8px 24px rgba(16, 185, 129, 0.15)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
              <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: '#ecfdf5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Stethoscope size={24} color="#10b981" />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '14px', color: '#666', fontWeight: 600 }}>Diagnosis Complete</h3>
                <p style={{ margin: 0, fontSize: '20px', fontWeight: 800, color: '#222' }}>{Math.round(result.confidence * 100)}% Match</p>
              </div>
            </div>

            <div style={{ marginBottom: '20px' }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '13px', color: '#888', textTransform: 'uppercase', letterSpacing: '1px' }}>Disease Identified</h4>
              <div style={{ fontSize: '18px', fontWeight: 800, color: '#b91c1c', backgroundColor: '#fef2f2', padding: '12px', borderRadius: '12px' }}>
                {result.disease}
              </div>
            </div>

            <div style={{ marginBottom: '32px' }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '13px', color: '#888', textTransform: 'uppercase', letterSpacing: '1px' }}>Recommended Remedy</h4>
              <div style={{ fontSize: '15px', lineHeight: 1.6, color: '#333', backgroundColor: '#f9f9f9', padding: '16px', borderRadius: '12px', border: '1px solid #eee' }}>
                {result.remedy}
              </div>
            </div>

            <button 
              onClick={handleSave}
              disabled={isSaving}
              style={{ width: '100%', padding: '16px', backgroundColor: 'var(--terracotta-primary)', color: '#fff', border: 'none', borderRadius: '12px', fontSize: '16px', fontWeight: 800, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
            >
              {isSaving ? <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> : <Save size={20} />}
              Save to History
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default FarmerDiagnose;
