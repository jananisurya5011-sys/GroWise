import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, CloudRain, Sun, Wind, Droplets } from 'lucide-react';

const FarmerWeather = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const weatherData = location.state?.weatherData || null;

  if (!weatherData) {
    return (
      <div style={{ textAlign: 'center', padding: '40px' }}>
        <h2>No weather data found.</h2>
        <button onClick={() => navigate('/home/farmer')}>Go Back</button>
      </div>
    );
  }

  const isRainy = weatherData.condition?.toLowerCase().includes('rain');

  return (
    <div style={{ maxWidth: '600px', margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/home/farmer')} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 800, color: '#222' }}>Live Forecast</h1>
      </div>

      <div style={{ 
        background: isRainy ? 'linear-gradient(135deg, #4b5563 0%, #1f2937 100%)' : 'linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%)', 
        borderRadius: '32px', padding: '40px', color: '#fff', textAlign: 'center',
        boxShadow: '0 20px 40px rgba(0,0,0,0.1)'
      }}>
        <h2 style={{ margin: '0 0 4px 0', fontSize: '28px', fontWeight: 800 }}>{weatherData.location}</h2>
        {weatherData.address && <p style={{ margin: '0 0 8px 0', fontSize: '13px', opacity: 0.8 }}>{weatherData.address}</p>}
        {weatherData.latitude && weatherData.longitude && (
          <div style={{ fontSize: '11px', opacity: 0.6, marginBottom: '24px', letterSpacing: '1px' }}>
            LAT: {weatherData.latitude} | LON: {weatherData.longitude}
          </div>
        )}
        <p style={{ margin: '0 0 32px 0', fontSize: '18px', opacity: 0.9 }}>{new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}</p>
        
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '24px', marginBottom: '32px' }}>
          {isRainy ? <CloudRain size={80} color="#fff" /> : <Sun size={80} color="#fde047" />}
          <div style={{ fontSize: '72px', fontWeight: 800, lineHeight: 1 }}>{Math.round(weatherData.temp)}&deg;</div>
        </div>
        
        <h3 style={{ margin: '0 0 40px 0', fontSize: '24px', fontWeight: 600, textTransform: 'capitalize' }}>{weatherData.condition}</h3>

        <div style={{ display: 'flex', justifyContent: 'space-around', borderTop: '1px solid rgba(255,255,255,0.2)', paddingTop: '24px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Wind size={24} style={{ marginBottom: '8px', opacity: 0.8 }} />
            <span style={{ fontSize: '14px', fontWeight: 600 }}>12 km/h</span>
            <span style={{ fontSize: '12px', opacity: 0.6 }}>Wind</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Droplets size={24} style={{ marginBottom: '8px', opacity: 0.8 }} />
            <span style={{ fontSize: '14px', fontWeight: 600 }}>65%</span>
            <span style={{ fontSize: '12px', opacity: 0.6 }}>Humidity</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default FarmerWeather;
