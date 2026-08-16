import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getDoc, doc } from 'firebase/firestore';
import { db } from '../../utils/firebase';
import { useAuth } from '../../contexts/AuthContext';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import { motion } from 'framer-motion';
import 'leaflet/dist/leaflet.css';
import apiClient from '../../utils/apiClient';
import { ArrowLeft, Navigation, Clock, Phone, MapPin, User, CheckCircle2, Sprout, Calendar, PackageCheck, Truck, ShieldCheck, Heart } from 'lucide-react';

const greenIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const blueIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const AutoFitBounds = ({ bounds }) => {
  const map = useMap();
  useEffect(() => {
    if (bounds && bounds.length > 0) {
      map.fitBounds(bounds, { padding: [50, 50], animate: true, duration: 1 });
    }
  }, [map, bounds]);
  return null;
};

// Colors based on Golden Edition
const C = {
  primary: '#7C3D12',
  brown: '#4A3B32',
  cream: '#FDFBF7',
  warmWhite: '#FFFFFF',
  green: '#2E7D32',
  goldBorder: 'rgba(212,175,55,0.35)',
  goldShadow: '0 8px 32px rgba(212,175,55,0.1)',
};

const Card = ({ children, style = {} }) => (
  <div style={{
    background: C.warmWhite,
    borderRadius: '24px',
    padding: '24px',
    border: `1px solid ${C.goldBorder}`,
    boxShadow: C.goldShadow,
    ...style
  }}>
    {children}
  </div>
);

const SelfPickupTracker = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  
  const [farmerProfile, setFarmerProfile] = useState(null);
  const [ngoProfile, setNgoProfile] = useState(null);

  const [ngoLocation, setNgoLocation] = useState(null);
  const [routeCoords, setRouteCoords] = useState([]);
  const [distance, setDistance] = useState(null);
  const [eta, setEta] = useState(null);
  
  const [otpInputs, setOtpInputs] = useState(['', '', '', '']);
  const inputRefs = [useRef(), useRef(), useRef(), useRef()];
  
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState('');

  useEffect(() => {
    const fetchOrder = async () => {
      try {
        const docSnap = await getDoc(doc(db, 'orders', orderId));
        if (docSnap.exists()) {
          const data = docSnap.data();
          setOrder(data);
          
          if (data.farmerEmail) {
            const fSnap = await getDoc(doc(db, 'users', data.farmerEmail));
            if (fSnap.exists()) setFarmerProfile(fSnap.data());
          }
          if (data.userEmail) {
            const nSnap = await getDoc(doc(db, 'users', data.userEmail));
            if (nSnap.exists()) setNgoProfile(nSnap.data());
          }
          
          if (data.pickupLat && data.pickupLon && data.dropLat && data.dropLon) {
             setNgoLocation([data.dropLat, data.dropLon]);
             fetchRoute(data.dropLat, data.dropLon, data.pickupLat, data.pickupLon);
          }
        }
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchOrder();
  }, [orderId]);

  useEffect(() => {
    if (!order) return;
    const interval = setInterval(() => {
    }, 5000);
    return () => clearInterval(interval);
  }, [order]);

  const fetchRoute = async (slat, slon, dlat, dlon) => {
    try {
      const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${slon},${slat};${dlon},${dlat}?overview=full&geometries=geojson`);
      const data = await res.json();
      if (data.routes && data.routes[0]) {
        const coords = data.routes[0].geometry.coordinates.map(c => [c[1], c[0]]);
        setRouteCoords(coords);
        setDistance((data.routes[0].distance / 1000).toFixed(1));
        setEta(Math.ceil(data.routes[0].duration / 60));
      }
    } catch (e) { console.error('Route error', e); }
  };

  const handleOtpChange = (index, value) => {
    if (value.length > 1) value = value.slice(-1);
    if (!/^\d*$/.test(value)) return;
    
    const newOtp = [...otpInputs];
    newOtp[index] = value;
    setOtpInputs(newOtp);
    
    if (value && index < 3) {
      inputRefs[index + 1].current.focus();
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !otpInputs[index] && index > 0) {
      inputRefs[index - 1].current.focus();
    }
  };

  const handleVerifyOtp = async () => {
    const code = otpInputs.join('');
    if (code.length < 4) return;
    
    setVerifying(true);
    setVerifyError('');
    try {
      const res = await apiClient.post('/api/orders/verify_self_pickup', {
        orderId: order.orderId,
        otp: code,
        farmerId: user.email
      });
      if (res.data.success) {
        setOrder({ ...order, status: 'COMPLETED' });
      }
    } catch (err) {
      setVerifyError(err.response?.data?.error || 'Invalid OTP');
    }
    setVerifying(false);
  };

  if (loading) {
    return (
      <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto', background: C.cream, minHeight: '100vh' }}>
        <div style={{ height: '60px', background: '#e0e0e0', borderRadius: '12px', marginBottom: '24px', animation: 'pulse 1.5s infinite' }} />
        <div style={{ height: '500px', background: '#e0e0e0', borderRadius: '24px', marginBottom: '24px', animation: 'pulse 1.5s infinite' }} />
        <div style={{ height: '100px', background: '#e0e0e0', borderRadius: '24px', marginBottom: '24px', animation: 'pulse 1.5s infinite' }} />
      </div>
    );
  }
  
  if (!order) return <div style={{ padding: '24px', textAlign: 'center' }}>Order not found</div>;

  const isFarmer = user?.email === order.farmerEmail;
  const isCompleted = order.status === 'COMPLETED' || order.status === 'DELIVERED';
  const farmerLoc = [order.pickupLat, order.pickupLon];
  const bounds = ngoLocation ? [farmerLoc, ngoLocation] : [farmerLoc];

  // Helper for timeline
  const timelineSteps = [
    { label: 'Accepted', icon: <CheckCircle2 size={20} /> },
    { label: 'OTP Verified', icon: <ShieldCheck size={20} /> },
    { label: 'Completed', icon: <CheckCircle2 size={20} /> }
  ];
  
  let currentStepIndex = 0;
  if (isCompleted) currentStepIndex = 2;

  const targetProfile = isFarmer ? ngoProfile : farmerProfile;
  const targetEmail = isFarmer ? order.userEmail : order.farmerEmail;
  const targetRole = isFarmer ? 'NGO' : 'Farmer';
  const avatarUrl = targetProfile?.profilePhoto || targetProfile?.profileImageUrl || null;

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }} style={{ background: C.cream, minHeight: '100vh', padding: '24px', fontFamily: 'system-ui, sans-serif' }}>
      <style>{`
        @keyframes pulse-border {
          0% { box-shadow: 0 0 0 0 rgba(124, 61, 18, 0.4); }
          70% { box-shadow: 0 0 0 10px rgba(124, 61, 18, 0); }
          100% { box-shadow: 0 0 0 0 rgba(124, 61, 18, 0); }
        }
        .step-pulse {
          animation: pulse-border 2s infinite;
        }
        input:focus {
          outline: none;
          border-color: ${C.primary} !important;
        }
      `}</style>
      
      <div style={{ maxWidth: '1400px', margin: '0 auto' }}>
        
        {/* HEADER */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px', flexWrap: 'wrap' }}>
          <button onClick={() => navigate(-1)} style={{ background: 'transparent', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', color: C.brown }}>
            <ArrowLeft size={24} />
          </button>
          
          <img 
            src={order.imageUrl ? (order.imageUrl.startsWith("http") ? order.imageUrl : `http://localhost:5000${order.imageUrl}`) : 'https://placehold.co/100x100/FFF9F5/D85A38?text=Crop'} 
            alt={order.cropName} 
            style={{ width: 80, height: 80, borderRadius: 12, objectFit: 'cover', border: `1px solid ${C.goldBorder}` }} 
            onError={(e) => { e.target.onerror = null; e.target.src = 'https://placehold.co/100x100/FFF9F5/D85A38?text=Crop'; }}
          />

          <div style={{ flex: 1 }}>
            <h1 style={{ margin: '0 0 4px 0', fontSize: '24px', color: C.primary, fontWeight: '900', textTransform: 'capitalize' }}>{order.cropName}</h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '4px', flexWrap: 'wrap' }}>
               <span style={{ color: C.brown, fontWeight: '600' }}>Quantity: <span style={{ color: C.green }}>{order.weightKg} KG</span></span>
               <span style={{ color: '#ccc' }}>•</span>
               <span style={{ color: C.brown, fontWeight: '600' }}>Order ID: {order.orderId}</span>
               <span style={{ color: '#ccc' }}>•</span>
               <span style={{ color: C.primary, fontWeight: 'bold' }}>Status: {order.status === 'PENDING_DRIVER' ? 'Accepted' : order.status === 'PENDING' ? 'Travelling to Farm' : order.status}</span>
            </div>
          </div>
        </div>

        {/* HERO MAP */}
        <div style={{ height: 'max(45vh, 500px)', width: '100%', borderRadius: '24px', overflow: 'hidden', border: `1px solid ${C.goldBorder}`, boxShadow: C.goldShadow, position: 'relative', marginBottom: '24px', zIndex: 1 }}>
          <MapContainer center={farmerLoc} zoom={13} style={{ height: '100%', width: '100%' }} zoomControl={false}>
            <TileLayer url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png" />
            <Marker position={farmerLoc} icon={greenIcon}>
              <Popup>Farmer Location</Popup>
            </Marker>
            {ngoLocation && (
              <Marker position={ngoLocation} icon={blueIcon}>
                <Popup>NGO Location</Popup>
              </Marker>
            )}
            {routeCoords.length > 0 && <Polyline positions={routeCoords} color="#2196F3" weight={5} opacity={0.7} />}
            <AutoFitBounds bounds={bounds} />
          </MapContainer>

          {/* Floating Glassmorphism Overlay */}
          {!isCompleted && (
            <div style={{ 
              position: 'absolute', top: '24px', left: '24px', zIndex: 1000,
              background: 'rgba(255,255,255,0.75)', backdropFilter: 'blur(18px)',
              padding: '16px 24px', borderRadius: '16px', border: `1px solid ${C.goldBorder}`,
              boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: C.green, display: 'inline-block' }} />
                <span style={{ fontWeight: '800', color: C.primary, letterSpacing: '0.5px' }}>LIVE PICKUP</span>
              </div>
              <p style={{ margin: '0 0 12px 0', color: C.brown, fontWeight: '600' }}>NGO is travelling</p>
              <div style={{ display: 'flex', gap: '24px' }}>
                <div>
                  <p style={{ margin: 0, fontSize: '12px', color: '#666' }}>ETA</p>
                  <p style={{ margin: 0, fontWeight: 'bold', color: C.brown }}>{eta ? `${eta} min` : '--'}</p>
                </div>
                <div>
                  <p style={{ margin: 0, fontSize: '12px', color: '#666' }}>Distance</p>
                  <p style={{ margin: 0, fontWeight: 'bold', color: C.brown }}>{distance ? `${distance} km` : '--'}</p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* JOURNEY TIMELINE */}
        <Card style={{ marginBottom: '24px', overflowX: 'auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minWidth: '600px', padding: '0 12px' }}>
            {timelineSteps.map((step, idx) => {
              const isPast = idx < currentStepIndex;
              const isCurrent = idx === currentStepIndex;
              
              let bgColor = C.cream;
              let borderColor = C.goldBorder;
              let iconColor = '#999';
              
              if (isPast || (isCompleted && isCurrent)) {
                bgColor = C.green;
                borderColor = C.green;
                iconColor = '#fff';
              } else if (isCurrent) {
                bgColor = C.warmWhite;
                borderColor = C.primary;
                iconColor = C.primary;
              }

              return (
                <React.Fragment key={idx}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', position: 'relative' }}>
                    <div className={isCurrent && !isCompleted ? 'step-pulse' : ''} style={{
                      width: '48px', height: '48px', borderRadius: '50%', 
                      background: bgColor, border: `2px solid ${borderColor}`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      color: iconColor, transition: 'all 0.3s ease'
                    }}>
                      {step.icon}
                    </div>
                    <span style={{ fontWeight: isCurrent ? 'bold' : '500', color: isCurrent ? C.primary : (isPast ? C.green : '#999'), fontSize: '14px' }}>
                      {step.label}
                    </span>
                  </div>
                  {idx < timelineSteps.length - 1 && (
                    <div style={{ flex: 1, height: '2px', background: isPast ? C.green : '#E0E0E0', margin: '0 16px', position: 'relative', top: '-14px', transition: 'all 0.3s ease' }} />
                  )}
                </React.Fragment>
              );
            })}
          </div>
        </Card>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px', marginBottom: '24px' }}>
          {/* OTP CARD */}
          <Card style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', minHeight: '300px' }}>
            {isCompleted ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
                <div style={{ width: '80px', height: '80px', borderRadius: '50%', background: '#E8F5E9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <CheckCircle2 size={40} color={C.green} />
                </div>
                <h2 style={{ margin: 0, color: C.green, fontSize: '24px' }}>Pickup Completed</h2>
                <p style={{ color: C.brown, margin: 0 }}>The donation handover is fully verified.</p>
              </div>
            ) : !isFarmer ? (
              // NGO View
              <div style={{ width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px', marginBottom: '8px' }}>
                  <ShieldCheck size={28} color={C.primary} />
                  <h2 style={{ margin: 0, color: C.primary, fontSize: '22px' }}>Pickup Verification</h2>
                </div>
                <p style={{ margin: '0 0 16px', fontSize: 12, fontWeight: 700, color: C.brown }}>Order ID: {order.orderId}</p>
                <p style={{ color: C.brown, marginBottom: '32px', fontWeight: '500' }}>Show this OTP to the farmer during pickup.</p>
                <div style={{ display: 'flex', justifyContent: 'center', gap: '20px' }}>
                  {order.pickupOtp.split('').map((digit, i) => (
                    <div key={i} style={{
                      width: '64px', height: '80px', background: C.cream,
                      border: `1px solid ${C.goldBorder}`, borderRadius: '16px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '40px', fontWeight: '900', color: C.primary,
                      boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
                    }}>
                      {digit}
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              // Farmer View
              <div style={{ width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px', marginBottom: '8px' }}>
                  <ShieldCheck size={28} color={C.primary} />
                  <h2 style={{ margin: 0, color: C.primary, fontSize: '22px' }}>Verify NGO Pickup</h2>
                </div>
                <p style={{ margin: '0 0 16px', fontSize: 12, fontWeight: 700, color: C.brown }}>Order ID: {order.orderId}</p>
                <p style={{ color: C.brown, marginBottom: '32px', fontWeight: '500' }}>Enter the 4-digit OTP provided by the NGO.</p>
                <div style={{ display: 'flex', justifyContent: 'center', gap: '16px', marginBottom: '32px' }}>
                  {otpInputs.map((val, i) => (
                    <input
                      key={i}
                      ref={inputRefs[i]}
                      type="text"
                      value={val}
                      onChange={(e) => handleOtpChange(i, e.target.value)}
                      onKeyDown={(e) => handleKeyDown(i, e)}
                      style={{
                        width: '64px', height: '80px', fontSize: '36px', textAlign: 'center',
                        border: `2px solid ${val ? C.primary : '#E0E0E0'}`, borderRadius: '16px',
                        fontWeight: '900', color: C.primary, background: C.warmWhite,
                        boxShadow: '0 4px 12px rgba(0,0,0,0.02)', transition: 'all 0.2s'
                      }}
                    />
                  ))}
                </div>
                {verifyError && <p style={{ color: '#D32F2F', margin: '0 0 16px', fontWeight: 'bold' }}>{verifyError}</p>}
                <button
                  onClick={handleVerifyOtp}
                  disabled={otpInputs.join('').length < 4 || verifying}
                  style={{
                    width: '100%', padding: '20px', borderRadius: '100px',
                    background: otpInputs.join('').length < 4 ? '#E0E0E0' : C.primary,
                    color: 'white', border: 'none', fontSize: '18px', fontWeight: 'bold',
                    cursor: otpInputs.join('').length < 4 ? 'not-allowed' : 'pointer',
                    transition: 'all 0.3s', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px'
                  }}
                >
                  {verifying ? <div className="spinner" style={{ width: '24px', height: '24px', border: '3px solid rgba(255,255,255,0.3)', borderTop: '3px solid white', borderRadius: '50%', animation: 'spin 1s linear infinite' }} /> : 'Verify Pickup'}
                </button>
                <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
              </div>
            )}
          </Card>

          {/* PROFILE CARD */}
          <Card style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', justifyContent: 'center' }}>
            <div style={{ position: 'relative', marginBottom: '24px' }}>
              {avatarUrl ? (
                <img src={avatarUrl} alt="Profile" style={{ width: '100px', height: '100px', borderRadius: '50%', objectFit: 'cover', border: `3px solid ${C.goldBorder}`, padding: '4px' }} />
              ) : (
                <div style={{ width: '100px', height: '100px', borderRadius: '50%', background: C.cream, border: `3px solid ${C.goldBorder}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '36px', fontWeight: 'bold', color: C.primary, padding: '4px' }}>
                  {(targetProfile?.name || targetEmail).charAt(0).toUpperCase()}
                </div>
              )}
              <div style={{ position: 'absolute', bottom: '0', right: '0', background: C.green, color: 'white', borderRadius: '50%', padding: '4px', border: '2px solid white' }}>
                <CheckCircle2 size={16} />
              </div>
            </div>
            
            <h2 style={{ margin: '0 0 4px 0', color: C.brown, fontSize: '24px', fontWeight: '800' }}>{targetProfile?.name || targetEmail}</h2>
            <p style={{ margin: '0 0 24px 0', color: C.primary, fontWeight: 'bold', letterSpacing: '1px', textTransform: 'uppercase', fontSize: '12px' }}>Verified {targetRole}</p>
            
            <div style={{ width: '100%', background: C.cream, borderRadius: '16px', padding: '16px', textAlign: 'left', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Phone size={20} color={C.primary} />
                <span style={{ color: C.brown, fontWeight: '600', fontSize: '16px' }}>{targetProfile?.phone || 'Phone not available'}</span>
              </div>
              {!isFarmer && (
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                  <MapPin size={20} color={C.primary} style={{ marginTop: '2px', flexShrink: 0 }} />
                  <span style={{ color: C.brown, fontWeight: '500', fontSize: '15px', lineHeight: '1.4' }}>{order.pickupAddress}</span>
                </div>
              )}
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Navigation size={20} color={C.primary} />
                <span style={{ color: C.primary, fontWeight: '600', fontSize: '15px' }}>{isFarmer ? (isCompleted ? 'Picked up' : 'Waiting at Farm') : (isCompleted ? 'Received' : 'Travelling')}</span>
              </div>
            </div>
          </Card>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
          {/* PRODUCT CARD */}
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
              <div style={{ width: '100px', height: '100px', background: C.cream, borderRadius: '20px', border: `1px solid ${C.goldBorder}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Sprout size={48} color={C.green} />
              </div>
              <div>
                <h3 style={{ margin: '0 0 8px 0', fontSize: '26px', color: C.brown, fontWeight: '900', textTransform: 'capitalize' }}>{order.cropName}</h3>
                <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: 'linear-gradient(135deg, #4CAF50 0%, #2E7D32 100%)', color: 'white', padding: '6px 12px', borderRadius: '100px', fontWeight: 'bold', fontSize: '13px', border: `1px solid ${C.goldBorder}`, boxShadow: '0 4px 8px rgba(46,125,50,0.3)', marginBottom: '12px' }}>
                  <Heart size={14} fill="white" /> DONATION
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#666', fontSize: '14px', fontWeight: '500' }}>
                  <PackageCheck size={16} /> {order.weightKg} KG
                </div>
              </div>
            </div>
          </Card>

          {/* SUMMARY CARD */}
          <Card>
            <h3 style={{ margin: '0 0 20px 0', color: C.primary, fontSize: '20px', fontWeight: '800' }}>Donation Summary</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px dashed #E0E0E0', paddingBottom: '8px' }}>
                <span style={{ color: '#666', fontWeight: '500' }}>Crop</span>
                <span style={{ color: C.brown, fontWeight: '700', textTransform: 'capitalize' }}>{order.cropName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px dashed #E0E0E0', paddingBottom: '8px' }}>
                <span style={{ color: '#666', fontWeight: '500' }}>Quantity</span>
                <span style={{ color: C.brown, fontWeight: '700' }}>{order.weightKg} KG</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px dashed #E0E0E0', paddingBottom: '8px' }}>
                <span style={{ color: '#666', fontWeight: '500' }}>Pickup Method</span>
                <span style={{ color: C.brown, fontWeight: '700' }}>Self Pickup</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px dashed #E0E0E0', paddingBottom: '8px' }}>
                <span style={{ color: '#666', fontWeight: '500' }}>Order Status</span>
                <span style={{ color: C.primary, fontWeight: '800' }}>{order.status === 'PENDING_DRIVER' ? 'Accepted' : order.status === 'PENDING' ? 'Travelling' : order.status}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#666', fontWeight: '500' }}>Donation Amount</span>
                <span style={{ color: C.green, fontWeight: '900', fontSize: '18px' }}>₹0</span>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </motion.div>
  );
};

export default SelfPickupTracker;
