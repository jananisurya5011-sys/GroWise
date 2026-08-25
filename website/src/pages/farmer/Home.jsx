// C:\Projects\GroWise_Fullstack\website\src\pages\farmer\Home.jsx
import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import gsap from 'gsap';
import { CloudRain, Sun, Navigation, Stethoscope, Package, Tractor, Sprout, Image as ImageIcon, ChevronRight, X, Truck } from 'lucide-react';
import DiagnosisHistoryCard from '../../components/diagnosis/DiagnosisHistoryCard';
import DiagnosisResultCard from '../../components/diagnosis/DiagnosisResultCard';
import notify from '../../services/NotificationService';


const PrimaryCard = ({ title, subtitle, icon, color, onClick }) => (
  <div 
    onClick={onClick}
    style={{
      backgroundColor: '#fff', borderRadius: '20px', padding: '20px', flex: 1, minWidth: '100px',
      border: '1px solid #eee', boxShadow: '0 4px 12px rgba(0,0,0,0.02)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      textAlign: 'center', cursor: 'pointer', transition: 'transform 0.2s ease'
    }}
    onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.03)'}
    onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
  >
    <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: `${color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '12px' }}>
      {icon}
    </div>
    <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 800, color: '#222' }}>{title}</h3>
    <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: '#666', fontWeight: 500 }}>{subtitle}</p>
  </div>
);

const FarmerHome = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [weatherData, setWeatherData] = useState(null);
  const [isLocating, setIsLocating] = useState(false);
  const [diagnosticsHistory, setDiagnosticsHistory] = useState([]);
  
  const [selectedDiagnosis, setSelectedDiagnosis] = useState(null);
  
  const containerRef = useRef(null);

  useEffect(() => {
    if (containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' }
      );
    }
    fetchHistory();
  }, []);

  const fetchHistory = async () => {
    if (!user?.email) return;
    try {
      const { data } = await apiClient.post('/api/crop-doctor/history', { email: user.email });
      if (data.success) setDiagnosticsHistory(data.history || []);
    } catch (error) {
      console.error("History fetch error:", error);
    }
  };

  const handleWeatherClick = () => {
    if (weatherData) {
      navigate('/farmer/weather', { state: { weatherData } });
      return;
    }

    setIsLocating(true);
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(async (position) => {
        try {
          const { latitude, longitude } = position.coords;
          // Proxy to our backend
          const { data } = await apiClient.get(`/api/weather?lat=${latitude}&lon=${longitude}`);
          if (data.success) {
            setWeatherData(data);
            navigate('/farmer/weather', { state: { weatherData: data } });
          } else {
            notify.warning("Weather data unavailable.");
          }
        } catch (error) {
          console.error(error);
          notify.error("Error fetching weather.");
        } finally {
          setIsLocating(false);
        }
      }, (error) => {
        notify.warning("Please enable GPS permissions to fetch live weather data.");
        setIsLocating(false);
      });
    } else {
      notify.info("Geolocation is not supported by this browser.");
      setIsLocating(false);
    }
  };

  const handleCloseModal = () => {
    setSelectedDiagnosis(null);
  };

  return (
    <div ref={containerRef} style={{ maxWidth: '800px', margin: '0 auto' }}>
      
      {/* 1. Dynamic Weather Card */}
      <section 
        onClick={handleWeatherClick}
        style={{
          background: 'linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%)',
          borderRadius: '24px', padding: '24px', color: '#fff',
          boxShadow: '0 10px 24px rgba(59, 130, 246, 0.3)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          cursor: 'pointer', marginBottom: '32px'
        }}
      >
        <div>
          <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 800 }}>
            {isLocating ? 'Locating...' : (weatherData ? weatherData.location : 'Check Local Weather')}
          </h2>
          <p style={{ margin: '4px 0 0 0', fontSize: '14px', opacity: 0.9, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Navigation size={14} /> Tap to enable GPS forecast
          </p>
        </div>
        <div style={{ width: '56px', height: '56px', borderRadius: '50%', backgroundColor: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {isLocating ? <CloudRain size={28} /> : <Sun size={28} />}
        </div>
      </section>

      {/* 2. Primary Action Cards Grid */}
      <section style={{ display: 'flex', gap: '16px', marginBottom: '32px', flexWrap: 'wrap' }}>
        <PrimaryCard 
          title="Diagnose" subtitle="Offline ML" 
          icon={<Stethoscope size={24} color="#10b981" />} color="#10b981" 
          onClick={() => navigate('/farmer/diagnose')} 
        />
        <PrimaryCard 
          title="AI Diagnosis" subtitle="Keras API" 
          icon={<Stethoscope size={24} color="#8b5cf6" />} color="#8b5cf6" 
          onClick={() => navigate('/farmer/ai-diagnose')} 
        />
        <PrimaryCard 
          title="List Product" subtitle="Sell & Track" 
          icon={<Package size={24} color="var(--terracotta-primary)" />} color="var(--terracotta-primary)" 
          onClick={() => navigate('/farmer/inventory')} 
        />
        <PrimaryCard 
          title="Rental Hub" subtitle="Equipments" 
          icon={<Tractor size={24} color="#f59e0b" />} color="#f59e0b" 
          onClick={() => navigate('/farmer/rental')} 
        />
      </section>

      {/* 3. Smart Cultivation (Golden Outline) */}
      <section 
        onClick={() => navigate('/farmer/cultivation')}
        style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '24px', border: '2px solid var(--golden-yellow)', boxShadow: '0 8px 24px rgba(242, 163, 58, 0.1)', marginBottom: '32px', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Sprout size={24} color="var(--terracotta-primary)" />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#222' }}>Smart Cultivation</h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#666' }}>Get an AI-generated timeline for your next crop.</p>
          </div>
        </div>
        <ChevronRight size={24} color="#ccc" />
      </section>

      {/* 3.5 Logistics Pool / Crop Pool Card */}
      <section 
        onClick={() => navigate('/farmer/crop-pool')}
        style={{
          backgroundColor: '#fff', borderRadius: '24px', padding: '24px', 
          border: '2px solid var(--golden-yellow)', boxShadow: '0 8px 24px rgba(242, 163, 58, 0.1)', 
          marginBottom: '32px', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          cursor: 'pointer', transition: 'transform 0.2s ease, box-shadow 0.2s ease'
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'scale(1.01)';
          e.currentTarget.style.boxShadow = '0 12px 32px rgba(242, 163, 58, 0.2)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'scale(1)';
          e.currentTarget.style.boxShadow = '0 8px 24px rgba(242, 163, 58, 0.1)';
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Truck size={24} color="var(--terracotta-primary)" />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#222' }}>Logistics Pool</h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#666' }}>Share transportation costs by co-loading with nearby farmers.</p>
          </div>
        </div>
        <ChevronRight size={24} color="#ccc" />
      </section>

      {/* 4. Saved Diagnostics History */}
      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '24px', border: '2px solid var(--golden-yellow)', boxShadow: '0 8px 24px rgba(242, 163, 58, 0.1)', marginBottom: '32px' }}>
        <h3 style={{ fontSize: '18px', fontWeight: 800, color: '#111', margin: '0 0 16px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Stethoscope size={20} color="var(--terracotta-primary)" /> Recent Diagnostics
        </h3>
        <div style={{ display: 'flex', gap: '16px', overflowX: 'auto', paddingBottom: '16px', msOverflowStyle: 'none', scrollbarWidth: 'none' }}>
          {diagnosticsHistory.length === 0 ? (
            <div style={{ width: '100%', padding: '32px', textAlign: 'center', backgroundColor: '#f9f9f9', borderRadius: '16px', border: '1px dashed #ccc', color: '#888' }}>
              <ImageIcon size={32} style={{ opacity: 0.5, margin: '0 auto 8px auto', display: 'block' }} />
              No saved diagnostics yet.
            </div>
          ) : (
            diagnosticsHistory.map((item, idx) => (
              <DiagnosisHistoryCard 
                key={item.id || idx}
                item={item}
                onClick={setSelectedDiagnosis}
                onDelete={async (deletedItem) => {
                  if (!deletedItem.id) return;
                  try {
                    const res = await apiClient.delete(`/api/crop-doctor/history/${deletedItem.id}?email=${encodeURIComponent(user.email)}`);
                    if (res.data.success) {
                      setDiagnosticsHistory(prev => prev.filter(h => h.id !== deletedItem.id));
                    } else {
                      notify.error("Failed to delete diagnosis.");
                    }
                  } catch (e) {
                    notify.error("Error deleting diagnosis.");
                  }
                }}
              />
            ))
          )}
        </div>
      </section>

      {/* 5. Diagnosis Full View Modal */}
      {selectedDiagnosis && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px', overflowY: 'auto' }}>
          <div style={{ position: 'relative', width: '100%', maxWidth: '800px', margin: 'auto' }}>
            <button 
              onClick={handleCloseModal} 
              style={{ position: 'absolute', top: '16px', right: '16px', background: '#ef4444', border: 'none', borderRadius: '50%', width: '44px', height: '44px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 4px 12px rgba(239, 68, 68, 0.4)', zIndex: 10 }}
            >
              <X size={28} color="#fff" />
            </button>
            <DiagnosisResultCard 
              result={selectedDiagnosis.diagnosisType === 'ai' ? selectedDiagnosis.details : selectedDiagnosis} 
              diagnosisType={selectedDiagnosis.diagnosisType}
              mode="history"
            />
            {selectedDiagnosis.imagePath && (
              <div style={{ width: '100%', height: '300px', borderRadius: '24px', overflow: 'hidden', marginTop: '24px', backgroundColor: '#eee', boxShadow: '0 12px 48px rgba(0,0,0,0.04)', border: '1px solid #eee' }}>
                 <img src={`/api/crop-doctor/serve-image/${selectedDiagnosis.imagePath.split(/[/\\]/).pop()}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="Crop Issue" />
              </div>
            )}
          </div>
        </div>
      )}

    </div>
  );
};

export default FarmerHome;
