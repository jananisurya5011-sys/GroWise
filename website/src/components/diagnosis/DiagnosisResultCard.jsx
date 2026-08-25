import React, { useRef, useEffect } from 'react';
import { Save, AlertCircle, Droplets, Leaf, Shield, CheckCircle2, ChevronRight, Activity, Thermometer } from 'lucide-react';
import gsap from 'gsap';
import DiseaseBadge from './DiseaseBadge';
import ConfidenceBadge from './ConfidenceBadge';

const SectionHeader = ({ icon, title, color = "#22c55e" }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
    <div style={{ 
      width: '32px', height: '32px', borderRadius: '50%', 
      backgroundColor: `${color}15`, display: 'flex', 
      alignItems: 'center', justifyContent: 'center' 
    }}>
      {icon}
    </div>
    <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 800, color: '#333' }}>{title}</h3>
  </div>
);

const ListSection = ({ items, color = "#22c55e" }) => {
  if (!items || items.length === 0) return null;
  return (
    <ul style={{ margin: 0, paddingLeft: '8px', listStyle: 'none' }}>
      {items.map((item, idx) => (
        <li key={idx} style={{ 
          display: 'flex', alignItems: 'flex-start', gap: '8px', 
          marginBottom: '8px', fontSize: '14px', color: '#555', lineHeight: 1.5 
        }}>
          <ChevronRight size={16} color={color} style={{ marginTop: '2px', flexShrink: 0 }} />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
};

const DiagnosisResultCard = ({ 
  result, 
  diagnosisType = 'offline',
  mode = 'view', // 'offline' or 'ai' when diagnosing, 'history' when viewing past records
  onSave, 
  isSaving 
}) => {
  const containerRef = useRef(null);

  useEffect(() => {
    if (containerRef.current) {
      gsap.fromTo(containerRef.current, 
        { opacity: 0, y: 30, scale: 0.98 }, 
        { opacity: 1, y: 0, scale: 1, duration: 0.5, ease: 'power3.out' }
      );
    }
  }, [result]);

  if (!result) return null;

  // Derive isAI either from explicit diagnosisType prop or from the result object itself (if populated from history)
  const isAI = diagnosisType === 'ai' || result.diagnosisType === 'ai';
  const isHealthy = isAI ? (result.healthStatus?.toLowerCase() === 'healthy') : (result.disease === 'Healthy');
  const isHistoryMode = mode === 'history';

  return (
    <div 
      ref={containerRef}
      style={{
        backgroundColor: '#fff',
        borderRadius: '24px',
        border: '1px solid #eee',
        boxShadow: '0 12px 48px rgba(0,0,0,0.04)',
        overflow: 'hidden',
        marginTop: '24px'
      }}
    >
      {/* Header Section */}
      <div style={{ padding: '24px', borderBottom: '1px solid #eee', backgroundColor: isHealthy ? '#f0fdf4' : '#fff' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            {isAI && result.cropName && (
              <p style={{ margin: '0 0 4px 0', fontSize: '13px', color: '#666', fontWeight: 600, letterSpacing: '0.5px', textTransform: 'uppercase' }}>
                {result.cropName} {result.species ? `(${result.species})` : ''}
              </p>
            )}
            <DiseaseBadge disease={result.disease} isHealthy={isHealthy} />
            <ConfidenceBadge confidence={result.confidence} isOffline={!isAI} />
          </div>
          
          {!isHistoryMode && (
            <button 
              onClick={onSave}
              disabled={isSaving}
              style={{
                padding: '12px 24px',
                backgroundColor: '#3b82f6',
                color: '#fff',
                border: 'none',
                borderRadius: '12px',
                fontSize: '14px',
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                cursor: isSaving ? 'not-allowed' : 'pointer',
                opacity: isSaving ? 0.7 : 1,
                transition: 'background-color 0.2s',
                boxShadow: '0 4px 12px rgba(59, 130, 246, 0.3)'
              }}
            >
              <Save size={18} />
              {isSaving ? 'Saving...' : 'Save Diagnosis'}
            </button>
          )}
        </div>
      </div>

      {/* Body Section */}
      <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        
        {/* OFFLINE VIEW */}
        {!isAI && result.remedy && (
          <div style={{ backgroundColor: '#fdfbfb', padding: '20px', borderRadius: '16px', border: '1px solid #eee' }}>
            <SectionHeader icon={<CheckCircle2 size={18} color="#10b981" />} title="Recommended Action" color="#10b981" />
            <p style={{ margin: 0, fontSize: '15px', color: '#444', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
              {result.remedy}
            </p>
          </div>
        )}

        {/* AI VIEW */}
        {isAI && (
          <>
            {/* Symptoms & Cause */}
            {(!isHealthy || result.symptoms?.length > 0) && (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '24px' }}>
                {result.symptoms && result.symptoms.length > 0 && (
                  <div style={{ backgroundColor: '#fef2f2', padding: '20px', borderRadius: '16px', border: '1px solid #fee2e2' }}>
                    <SectionHeader icon={<Activity size={18} color="#ef4444" />} title="Symptoms Observed" color="#ef4444" />
                    <ListSection items={result.symptoms} color="#ef4444" />
                  </div>
                )}
                {result.cause && (
                  <div style={{ backgroundColor: '#fff7ed', padding: '20px', borderRadius: '16px', border: '1px solid #ffedd5' }}>
                    <SectionHeader icon={<AlertCircle size={18} color="#f97316" />} title="Primary Cause" color="#f97316" />
                    <p style={{ margin: 0, fontSize: '14px', color: '#555', lineHeight: 1.5 }}>{result.cause}</p>
                    {result.severity && (
                      <div style={{ marginTop: '12px', display: 'inline-block', padding: '4px 10px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #fdba74', fontSize: '12px', fontWeight: 700, color: '#ea580c' }}>
                        Severity: {result.severity}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* Treatments */}
            {!isHealthy && (result.organicTreatment?.length > 0 || result.chemicalTreatment?.length > 0 || result.treatment?.length > 0) && (
              <div style={{ backgroundColor: '#f0fdf4', padding: '20px', borderRadius: '16px', border: '1px solid #dcfce7' }}>
                <SectionHeader icon={<Shield size={18} color="#22c55e" />} title="Treatment Plan" color="#22c55e" />
                
                {result.treatment && result.treatment.length > 0 && (
                  <div style={{ marginBottom: '16px' }}>
                    <ListSection items={result.treatment} color="#22c55e" />
                  </div>
                )}

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '16px' }}>
                  {result.organicTreatment && result.organicTreatment.length > 0 && (
                    <div style={{ backgroundColor: '#fff', padding: '16px', borderRadius: '12px', border: '1px solid #eee' }}>
                      <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 700, color: '#16a34a' }}>Organic Approach</h4>
                      <ListSection items={result.organicTreatment} color="#16a34a" />
                    </div>
                  )}
                  {result.chemicalTreatment && result.chemicalTreatment.length > 0 && (
                    <div style={{ backgroundColor: '#fff', padding: '16px', borderRadius: '12px', border: '1px solid #eee' }}>
                      <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 700, color: '#3b82f6' }}>Chemical Approach</h4>
                      <ListSection items={result.chemicalTreatment} color="#3b82f6" />
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Care Recommendations */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
              {result.watering && (
                <div style={{ backgroundColor: '#f0f9ff', padding: '16px', borderRadius: '16px', border: '1px solid #e0f2fe' }}>
                  <SectionHeader icon={<Droplets size={16} color="#0ea5e9" />} title="Watering" color="#0ea5e9" />
                  <p style={{ margin: 0, fontSize: '13px', color: '#333', lineHeight: 1.5 }}>{result.watering}</p>
                </div>
              )}
              {result.fertilizer && result.fertilizer.length > 0 && (
                <div style={{ backgroundColor: '#fdf4ff', padding: '16px', borderRadius: '16px', border: '1px solid #fae8ff' }}>
                  <SectionHeader icon={<Leaf size={16} color="#d946ef" />} title="Fertilizer" color="#d946ef" />
                  <ListSection items={result.fertilizer} color="#d946ef" />
                </div>
              )}
              {result.prevention && result.prevention.length > 0 && (
                <div style={{ backgroundColor: '#f8fafc', padding: '16px', borderRadius: '16px', border: '1px solid #f1f5f9' }}>
                  <SectionHeader icon={<Thermometer size={16} color="#64748b" />} title="Prevention" color="#64748b" />
                  <ListSection items={result.prevention} color="#64748b" />
                </div>
              )}
            </div>

            {/* Summary / Tips */}
            {(result.summary || (result.growthTips && result.growthTips.length > 0)) && (
              <div style={{ backgroundColor: '#fdfbfb', padding: '20px', borderRadius: '16px', border: '1px solid #eee' }}>
                <SectionHeader icon={<CheckCircle2 size={18} color="#8b5cf6" />} title="Expert Summary & Tips" color="#8b5cf6" />
                {result.summary && (
                  <p style={{ margin: '0 0 12px 0', fontSize: '14px', color: '#444', lineHeight: 1.6 }}>
                    {result.summary}
                  </p>
                )}
                {result.growthTips && result.growthTips.length > 0 && (
                  <ListSection items={result.growthTips} color="#8b5cf6" />
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default DiagnosisResultCard;
