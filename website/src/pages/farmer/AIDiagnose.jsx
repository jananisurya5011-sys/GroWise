import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { ArrowLeft, AlertTriangle } from 'lucide-react';

import DiagnosisUpload from '../../components/diagnosis/DiagnosisUpload';
import DiagnosisLoader from '../../components/diagnosis/DiagnosisLoader';
import DiagnosisResultCard from '../../components/diagnosis/DiagnosisResultCard';
import LanguageSelector from '../../components/diagnosis/LanguageSelector';
import notify from '../../services/NotificationService';


const FarmerAIDiagnose = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [result, setResult] = useState(null);
  const [errorAlert, setErrorAlert] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedLanguage, setSelectedLanguage] = useState('en');

  const handleFileSelect = (file) => {
    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
    setResult(null);
    setErrorAlert(null);
  };

  const handleDiagnose = async () => {
    if (!selectedFile) return;
    
    setIsAnalyzing(true);
    setErrorAlert(null);
    setResult(null);
    
    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('language', selectedLanguage);

    try {
      // Backend expects file and language, returns rich Gemini JSON
      const { data } = await apiClient.post('/api/crop-doctor/ai-diagnose', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      
      if (data.success) {
        setResult(data.data || data); // Handle backend returning { success: true, data: {...} } or flattening it
      } else {
        setErrorAlert(data.error || 'Failed to analyze the image.');
      }
    } catch (error) {
      console.error(error);
      setErrorAlert('Network error: Failed to reach the Gemini Vision engine.');
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleSave = async () => {
    if (!result || !user?.email || !selectedFile) return;
    setIsSaving(true);
    
    const formData = new FormData();
    formData.append('email', user.email);
    formData.append('disease', result.disease || 'Unknown');
    formData.append('confidence', result.confidence || 0);
    formData.append('diagnosisType', 'ai');
    formData.append('language', selectedLanguage);
    formData.append('file', selectedFile); 
    
    // Store the complete rich Gemini JSON for history rendering
    const details = {
      ...result,
      modelVersion: 'gemini-3.5-flash',
      timestamp: Date.now(),
      requestVersion: 'v1.0',
      responseVersion: 'v1.0'
    };
    formData.append('details', JSON.stringify(details));

    try {
      const { data } = await apiClient.post('/api/crop-doctor/save-diagnosis', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      
      if (data.success) {
        notify.success("Saved to your Diagnostic History!");
        navigate('/home/farmer');
      } else {
        notify.error("Failed to save.");
      }
    } catch (error) {
      console.error(error);
      notify.error("Network error: failed to save diagnosis.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', fontFamily: 'system-ui, sans-serif' }}>
      {/* Premium Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '32px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <button onClick={() => navigate('/home/farmer')} style={{ background: '#fff', border: '1px solid #eee', width: '44px', height: '44px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
            <ArrowLeft size={20} color="#333" />
          </button>
          <div>
            <h1 style={{ margin: 0, fontSize: '26px', fontWeight: 800, color: '#8b5cf6', letterSpacing: '-0.5px' }}>AI Crop Doctor</h1>
            <p style={{ margin: 0, fontSize: '14px', color: '#666', fontWeight: 500 }}>Advanced cloud diagnosis powered by Gemini Vision.</p>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
        
        {/* Scanner Column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          
          <DiagnosisUpload 
            onFileSelect={handleFileSelect}
            previewUrl={previewUrl}
            isAnalyzing={isAnalyzing}
          />

          <LanguageSelector 
            selectedLanguage={selectedLanguage}
            onLanguageChange={setSelectedLanguage}
            disabled={isAnalyzing}
          />
          
          <DiagnosisLoader 
            isAnalyzing={isAnalyzing}
            onClick={handleDiagnose}
            disabled={!selectedFile}
            text="Diagnose with Gemini Vision"
            analyzingText="Analyzing Image..."
          />

          {errorAlert && (
            <div style={{ backgroundColor: '#fef2f2', borderRadius: '16px', padding: '16px', display: 'flex', alignItems: 'center', gap: '16px', border: '1px solid #fecaca' }}>
              <AlertTriangle size={28} color="#dc2626" />
              <p style={{ margin: 0, color: '#dc2626', fontSize: '14px', fontWeight: 700 }}>{errorAlert}</p>
            </div>
          )}
        </div>

        {/* Results Column */}
        <DiagnosisResultCard 
          result={result}
          diagnosisType="ai"
          onSave={handleSave}
          isSaving={isSaving}
        />
        
      </div>
    </div>
  );
};

export default FarmerAIDiagnose;
