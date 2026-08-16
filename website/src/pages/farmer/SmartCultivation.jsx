import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import gsap from 'gsap';
import { Sprout, ArrowLeft, CheckCircle2, Circle, Loader2, Calendar } from 'lucide-react';

const FarmerSmartCultivation = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();
  
  const [activeCrops, setActiveCrops] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  
  // Section A State
  const [plantName, setPlantName] = useState(searchParams.get('plant') || '');
  const [isGenerating, setIsGenerating] = useState(false);

  useEffect(() => {
    fetchActiveCrops();
  }, [user]);

  const fetchActiveCrops = async () => {
    if (!user?.email) return;
    setIsLoading(true);
    try {
      const { data } = await apiClient.post('/api/cultivation/fetch-active-crops', { email: user.email });
      if (data.success) {
        setActiveCrops(data.active_crops || []);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGenerate = async () => {
    if (!plantName.trim()) return;
    setIsGenerating(true);
    try {
      const { data } = await apiClient.post('/api/cultivation/generate', {
        crop_name: plantName,
        email: user.email
      });
      if (data.success) {
        setPlantName('');
        fetchActiveCrops(); // Refresh list to show new plan
      } else {
        alert(data.error || "Failed to generate plan.");
      }
    } catch (error) {
      console.error(error);
      alert("Error generating cultivation plan.");
    } finally {
      setIsGenerating(false);
    }
  };

  const markTaskDone = async (cropName, nextDay) => {
    try {
      const { data } = await apiClient.post('/api/cultivation/mark-task-done', {
        email: user.email,
        crop_name: cropName,
        next_day: nextDay
      });
      if (data.success) {
        fetchActiveCrops(); // Refresh list to reflect changes
      }
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/home/farmer')} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 800, color: '#222' }}>Smart Cultivation</h1>
          <p style={{ margin: 0, fontSize: '14px', color: '#666' }}>AI-Powered Crop Timelines</p>
        </div>
      </div>

      {/* Section A: Add New Schedule */}
      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '24px', border: '2px solid var(--golden-yellow)', boxShadow: '0 8px 24px rgba(242, 163, 58, 0.1)', marginBottom: '40px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
          <div style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Sprout size={20} color="var(--terracotta-primary)" />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#222' }}>Generate New Timeline</h3>
            <p style={{ margin: 0, fontSize: '13px', color: '#666' }}>Enter a crop name to get an AI-curated schedule.</p>
          </div>
        </div>
        
        <input 
          type="text"
          placeholder="e.g. Tomatoes, Wheat, Sugarcane"
          value={plantName}
          onChange={(e) => setPlantName(e.target.value)}
          style={{ width: '100%', padding: '16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '16px', marginBottom: '16px', outline: 'none' }}
        />
        
        <button 
          onClick={handleGenerate}
          disabled={isGenerating || !plantName.trim()}
          style={{ width: '100%', padding: '16px', backgroundColor: 'var(--golden-yellow)', color: '#fff', border: 'none', borderRadius: '12px', fontSize: '16px', fontWeight: 800, cursor: isGenerating || !plantName.trim() ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', opacity: !plantName.trim() ? 0.6 : 1 }}
        >
          {isGenerating ? <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> : null}
          {isGenerating ? 'Generating Timeline...' : 'Generate Plan'}
        </button>
      </section>

      {/* Section B: Historical Data / Active Crops */}
      <section>
        <h2 style={{ fontSize: '20px', fontWeight: 800, color: '#222', marginBottom: '24px' }}>Your Active Plans</h2>
        
        {isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '40px' }}>
            <Loader2 size={32} color="var(--terracotta-primary)" style={{ animation: 'spin 1s linear infinite' }} />
          </div>
        ) : activeCrops.length === 0 ? (
          <div style={{ textAlign: 'center', backgroundColor: '#fff', padding: '40px 24px', borderRadius: '24px', border: '1px dashed #ccc' }}>
            <Sprout size={48} color="#ccc" style={{ margin: '0 auto 16px auto', opacity: 0.5 }} />
            <h3 style={{ margin: '0 0 8px 0', color: '#333' }}>No Active Plans</h3>
            <p style={{ margin: 0, color: '#666' }}>Generate a new plan above to get started.</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            {activeCrops.map((crop, idx) => (
              <div key={idx} style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '24px', border: '1px solid #eee', boxShadow: '0 4px 12px rgba(0,0,0,0.03)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', paddingBottom: '16px', borderBottom: '1px solid #eee' }}>
                  <div>
                    <h2 style={{ margin: 0, fontSize: '20px', fontWeight: 800, color: 'var(--terracotta-primary)' }}>{crop.cropName}</h2>
                    <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#666', fontWeight: 600 }}>Status: <span style={{ color: crop.processStatus === 'Active' ? '#10b981' : '#f59e0b' }}>{crop.processStatus}</span></p>
                  </div>
                  <div style={{ backgroundColor: 'var(--peach-background)', padding: '8px 16px', borderRadius: '12px', color: 'var(--terracotta-primary)', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Calendar size={16} /> Day {crop.currentDay}
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {crop.roadmap && crop.roadmap.map((task, tIdx) => {
                    const isPast = task.status === 1;
                    const isCurrent = task.day === crop.currentDay;
                    const isFuture = task.day > crop.currentDay;
                    
                    return (
                      <div key={tIdx} style={{ display: 'flex', gap: '16px', opacity: isFuture ? 0.6 : 1 }}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                          <div style={{ width: '24px', height: '24px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: isPast ? '#10b981' : (isCurrent ? 'var(--golden-yellow)' : '#eee') }}>
                            {isPast ? <CheckCircle2 size={16} color="#fff" /> : <span style={{ color: isCurrent ? '#fff' : '#888', fontSize: '12px', fontWeight: 800 }}>{task.day}</span>}
                          </div>
                          {tIdx < crop.roadmap.length - 1 && <div style={{ width: '2px', flex: 1, backgroundColor: isPast ? '#10b981' : '#eee', margin: '4px 0' }} />}
                        </div>
                        
                        <div style={{ flex: 1, paddingBottom: '16px' }}>
                          <h4 style={{ margin: '0 0 4px 0', fontSize: '16px', fontWeight: 700, color: '#333' }}>{task.title}</h4>
                          <p style={{ margin: '0 0 12px 0', fontSize: '13px', color: '#666', lineHeight: 1.5 }}>{task.description}</p>
                          
                          {isCurrent && crop.processStatus === 'Active' && (
                            <button 
                              onClick={() => {
                                const nextDay = crop.roadmap[tIdx + 1] ? crop.roadmap[tIdx + 1].day : task.day;
                                markTaskDone(crop.cropName, nextDay);
                              }}
                              style={{ padding: '8px 16px', backgroundColor: 'var(--terracotta-primary)', color: '#fff', border: 'none', borderRadius: '8px', fontSize: '13px', fontWeight: 700, cursor: 'pointer' }}
                            >
                              Mark as Done
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

export default FarmerSmartCultivation;
