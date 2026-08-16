import React, { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, MapPin, Cloud, Wind, Droplets, ThermometerSun } from 'lucide-react';
import gsap from 'gsap';

const WeatherDetails = () => {
  const { state } = useLocation();
  const navigate = useNavigate();
  const containerRef = useRef(null);

  useEffect(() => {
    if (containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' }
      );
    }
  }, []);

  if (!state || !state.weatherData) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#666' }}>
        No weather data available. 
        <br/><br/>
        <button onClick={() => navigate(-1)} style={{ padding: '10px 20px', borderRadius: '12px', border: '1px solid #ccc', background: '#fff', cursor: 'pointer' }}>Go Back</button>
      </div>
    );
  }

  const { weatherData, lat, lon } = state;

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', paddingBottom: '80px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
        <button onClick={() => navigate(-1)} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <h1 style={{ margin: 0, fontSize: '20px', fontWeight: 800, color: '#222' }}>Weather Forecast</h1>
      </div>

      <div ref={containerRef} style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
        
        {/* Hero Card */}
        <div style={{ 
          background: 'linear-gradient(135deg, var(--terracotta-primary) 0%, var(--peach-background) 100%)', 
          borderRadius: '24px', padding: '32px', color: '#fff', 
          boxShadow: '0 12px 32px rgba(220, 88, 66, 0.2)', position: 'relative', overflow: 'hidden' 
        }}>
          <div style={{ position: 'relative', zIndex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', backgroundColor: 'rgba(255,255,255,0.2)', padding: '6px 12px', borderRadius: '16px', display: 'inline-flex' }}>
              <MapPin size={16} color="#fff" />
              <span style={{ fontSize: '14px', fontWeight: 600 }}>{lat ? lat.toFixed(4) : ''}, {lon ? lon.toFixed(4) : ''}</span>
            </div>
            
            <h2 style={{ margin: '0 0 8px 0', fontSize: '36px', fontWeight: 800 }}>{weatherData.location}</h2>
            <p style={{ margin: 0, fontSize: '16px', opacity: 0.9 }}>{weatherData.description?.charAt(0).toUpperCase() + weatherData.description?.slice(1) || 'Clear'}</p>
            
            <div style={{ marginTop: '32px', display: 'flex', alignItems: 'baseline', gap: '12px' }}>
              <span style={{ fontSize: '64px', fontWeight: 800, lineHeight: 1 }}>{weatherData.temp || weatherData.temperature}°C</span>
            </div>
          </div>
          <Cloud size={160} color="rgba(255,255,255,0.1)" style={{ position: 'absolute', right: '-20px', bottom: '-20px' }} />
        </div>

        {/* Metrics Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '16px' }}>
          <div style={{ backgroundColor: '#fff', borderRadius: '20px', padding: '20px', border: '1px solid #eee', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '12px', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
            <div style={{ padding: '12px', backgroundColor: '#f0fdf4', borderRadius: '50%' }}><Wind size={24} color="#10b981" /></div>
            <div style={{ textAlign: 'center' }}>
              <span style={{ display: 'block', fontSize: '12px', color: '#888', fontWeight: 600, textTransform: 'uppercase' }}>Wind Speed</span>
              <span style={{ display: 'block', fontSize: '18px', fontWeight: 800, color: '#222' }}>{weatherData.wind_speed || '12'} km/h</span>
            </div>
          </div>

          <div style={{ backgroundColor: '#fff', borderRadius: '20px', padding: '20px', border: '1px solid #eee', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '12px', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
            <div style={{ padding: '12px', backgroundColor: '#eff6ff', borderRadius: '50%' }}><Droplets size={24} color="#3b82f6" /></div>
            <div style={{ textAlign: 'center' }}>
              <span style={{ display: 'block', fontSize: '12px', color: '#888', fontWeight: 600, textTransform: 'uppercase' }}>Humidity</span>
              <span style={{ display: 'block', fontSize: '18px', fontWeight: 800, color: '#222' }}>{weatherData.humidity || '60'}%</span>
            </div>
          </div>

          <div style={{ backgroundColor: '#fff', borderRadius: '20px', padding: '20px', border: '1px solid #eee', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '12px', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
            <div style={{ padding: '12px', backgroundColor: '#fef3c7', borderRadius: '50%' }}><ThermometerSun size={24} color="#d97706" /></div>
            <div style={{ textAlign: 'center' }}>
              <span style={{ display: 'block', fontSize: '12px', color: '#888', fontWeight: 600, textTransform: 'uppercase' }}>Feels Like</span>
              <span style={{ display: 'block', fontSize: '18px', fontWeight: 800, color: '#222' }}>{weatherData.feels_like || weatherData.feelsLike || weatherData.temp || weatherData.temperature}°C</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
export default WeatherDetails;
