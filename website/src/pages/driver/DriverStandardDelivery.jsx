import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { collection, query, where, onSnapshot, doc, updateDoc, getDoc } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Phone, CheckCircle2, Navigation, Clock, Ban } from 'lucide-react';
import UserAvatar from '../../components/common/UserAvatar';
import AgriLoading from '../../components/common/AgriLoading';
import apiClient from '../../utils/apiClient';
import { ACTIVE_DRIVER_STATUSES } from '../../utils/constants';

// Icons
const leafSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/></svg>`;
const homeSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`;

const createIcon = (color, svgContent) => L.divIcon({
  className: 'custom-icon',
  html: `<div style="position: relative;">
           <div style="background-color: ${color}; width: 36px; height: 36px; border-radius: 50%; border: 3px solid white; box-shadow: 0 4px 6px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white;">
             ${svgContent || ''}
           </div>
         </div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 18]
});

const pickupIcon = createIcon('var(--golden-yellow)', leafSvg);
const dropIcon = createIcon('var(--terracotta-primary)', homeSvg);
const driverIcon = createIcon('#3b82f6', `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/><circle cx="7" cy="17" r="2"/><path d="M9 17h6"/><circle cx="17" cy="17" r="2"/></svg>`);

const MapBounds = ({ driverLoc, targetLoc }) => {
  const map = useMap();
  useEffect(() => {
    if (!map) return;
    const bounds = L.latLngBounds();
    if (driverLoc) bounds.extend([driverLoc.lat, driverLoc.lon]);
    if (targetLoc && targetLoc.lat) bounds.extend([targetLoc.lat, targetLoc.lon]);
    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [50, 50], animate: true });
    }
  }, [map, driverLoc, targetLoc]);
  return null;
};

const DriverStandardDelivery = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeOrder, setActiveOrder] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const [driverLoc, setDriverLoc] = useState(null);
  const [routePolyline, setRoutePolyline] = useState([]);
  const [liveEta, setLiveEta] = useState('-- Mins');

  const [farmerInfo, setFarmerInfo] = useState(null);
  const [userInfo, setUserInfo] = useState(null);

  const [showOtpModal, setShowOtpModal] = useState(false);
  const [otpValues, setOtpValues] = useState(['', '', '', '']);
  const otpRefs = [useRef(null), useRef(null), useRef(null), useRef(null)];
  const [isProcessing, setIsProcessing] = useState(false);
  
  const [timeoutRemaining, setTimeoutRemaining] = useState(null);

  // Sync with Firestore
  useEffect(() => {
    if (!user?.email) return;

    const q = query(collection(db, 'orders'), where('driverEmail', '==', user.email));
    const unsub = onSnapshot(q, async (snap) => {
      let found = null;
      snap.docs.forEach(d => {
        const data = d.data();
        const isStandard = data.orderId?.startsWith('GW-');
        const isDonation = data.orderId?.startsWith('DEAL-DON-');
        if ((isStandard || isDonation) && ACTIVE_DRIVER_STATUSES.includes(data.status)) {
          found = data;
        }
      });

      if (found) {
        setActiveOrder(found);
        
        if (!farmerInfo && found.farmerEmail) {
          const fDoc = await getDoc(doc(db, 'users', found.farmerEmail));
          if (fDoc.exists()) setFarmerInfo(fDoc.data());
        }
        if (!userInfo && found.userEmail) {
          const uDoc = await getDoc(doc(db, 'users', found.userEmail));
          if (uDoc.exists()) setUserInfo(uDoc.data());
        }
      } else {
        navigate('/home/driver');
      }
      setIsLoading(false);
    });

    return () => unsub();
  }, [user, navigate, farmerInfo, userInfo]);

  // GPS Polling
  useEffect(() => {
    let watchId;
    if (navigator.geolocation) {
      watchId = navigator.geolocation.watchPosition(
        (pos) => {
          const loc = { lat: pos.coords.latitude, lon: pos.coords.longitude };
          setDriverLoc(loc);
          if (activeOrder?.orderId) {
            updateDoc(doc(db, 'orders', activeOrder.orderId), {
              driverLat: loc.lat,
              driverLon: loc.lon
            }).catch(() => {});
          }
        },
        (err) => console.warn(err),
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
      );
    }
    return () => { if (watchId) navigator.geolocation.clearWatch(watchId); };
  }, [activeOrder?.orderId]);

  // Route Fetch
  const fetchRoute = useCallback(async (start, end) => {
    if (!start || !end || !start.lat || !end.lat) return;
    try {
      const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${start.lon},${start.lat};${end.lon},${end.lat}?overview=full&geometries=geojson`);
      const data = await res.json();
      if (data.routes && data.routes.length > 0) {
        const route = data.routes[0];
        setRoutePolyline(route.geometry.coordinates.map(c => [c[1], c[0]]));
        setLiveEta(`${Math.round(route.duration / 60)} Mins`);
      }
    } catch (e) {
      console.error("OSRM Error", e);
    }
  }, []);

  useEffect(() => {
    if (driverLoc && activeOrder) {
      const isDrop = activeOrder.status.includes('DROP');
      const target = isDrop ? { lat: activeOrder.dropLat, lon: activeOrder.dropLon } : { lat: activeOrder.pickupLat, lon: activeOrder.pickupLon };
      fetchRoute(driverLoc, target);
    }
  }, [driverLoc, activeOrder?.status, fetchRoute]);

  // Timeout Logic
  useEffect(() => {
    if (activeOrder?.status === 'WAITING_AT_PICKUP' && activeOrder?.pickupArrivalTimestamp) {
      const interval = setInterval(() => {
        const elapsed = Date.now() - activeOrder.pickupArrivalTimestamp;
        const remaining = 600000 - elapsed;
        if (remaining <= 0) {
          setTimeoutRemaining(0);
          handleTimeoutExpire();
          clearInterval(interval);
        } else {
          setTimeoutRemaining(Math.ceil(remaining / 1000));
        }
      }, 1000);
      return () => clearInterval(interval);
    } else {
      setTimeoutRemaining(null);
    }
  }, [activeOrder?.status, activeOrder?.pickupArrivalTimestamp]);

  const handleTimeoutExpire = async () => {
    if (isProcessing) return;
    setIsProcessing(true);
    try {
      const res = await apiClient.post('/api/logistics/cancel_order', {
        orderId: activeOrder.orderId,
        reason: 'TIMEOUT'
      });
      if (res.data.success) {
        alert("Pickup timeout expired. Order cancelled securely.");
        navigate('/home/driver');
      }
    } catch (err) {
      console.error(err);
      alert("Failed to process timeout.");
    }
    setIsProcessing(false);
  };

  const handleArrive = async () => {
    const currentRouteIndex = activeOrder.currentRouteIndex || 0;
    const currentStop = activeOrder.routeSequence[currentRouteIndex];
    const stopId = currentStop?.id;
    
    setIsProcessing(true);
    try {
      await apiClient.post('/api/logistics/arrive-stop', {
        orderId: activeOrder.orderId,
        stopId: stopId
      });
      setOtpValues(['', '', '', '']);
      setShowOtpModal(true);
    } catch (e) {
      alert("Failed to register arrival");
    }
    setIsProcessing(false);
  };

  const handleVerify = async () => {
    const enteredOtp = otpValues.join('');
    if (enteredOtp.length !== 4) return;
    
    setIsProcessing(true);
    const currentRouteIndex = activeOrder.currentRouteIndex || 0;
    const currentStop = activeOrder.routeSequence[currentRouteIndex];
    const stopId = currentStop?.id;

    try {
      const payload = {
        orderId: activeOrder.orderId,
        driverEmail: user?.email,
        isPool: false,
        cropValue: parseFloat(activeOrder.cropValue) || 0,
        transportFare: parseFloat(activeOrder.totalPayment || activeOrder.transportFare) || 0,
        cropName: activeOrder.cropName || 'Crop',
        farmerEmail: activeOrder.farmerEmail,
        stopId: stopId,
        enteredOtp: enteredOtp
        // The backend will compute nextStatus, nextIndex, and isFinalStop autonomously.
      };

      const res = await apiClient.post('/api/logistics/complete-delivery', payload);

      if (res.data.success) {
        setShowOtpModal(false);
        // The backend computes if delivery is fully completed
        if (res.data.message === "Delivery completed successfully.") {
          alert("Delivery completed! Earnings added to wallet.");
          navigate('/home/driver');
        }
      } else {
        alert(res.data.error || "Invalid OTP");
      }
    } catch (e) {
      alert(e.response?.data?.error || "Network Error Verifying OTP");
    }
    setIsProcessing(false);
  };

  if (isLoading || !activeOrder) return <AgriLoading />;

  const currentRouteIndex = activeOrder.currentRouteIndex || 0;
  const currentStop = activeOrder.routeSequence && activeOrder.routeSequence[currentRouteIndex];
  const isDonation = activeOrder?.orderId?.startsWith('DEAL-DON-') || activeOrder?.dealType === 'DONATION';
  const isDropStage = currentStop?.type === 'DROP';
  const targetLoc = isDropStage ? { lat: activeOrder.dropLat, lon: activeOrder.dropLon } : { lat: activeOrder.pickupLat, lon: activeOrder.pickupLon };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', backgroundColor: '#f9fafb' }}>
      <div style={{ flex: 1, position: 'relative' }}>
        <MapContainer center={targetLoc?.lat ? [targetLoc.lat, targetLoc.lon] : [20, 77]} zoom={15} style={{ height: '100%', width: '100%' }} zoomControl={false}>
          <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
          <MapBounds driverLoc={driverLoc} targetLoc={targetLoc} />
          {targetLoc?.lat && <Marker position={[targetLoc.lat, targetLoc.lon]} icon={isDropStage ? dropIcon : pickupIcon} />}
          {driverLoc && <Marker position={[driverLoc.lat, driverLoc.lon]} icon={driverIcon} />}
          {routePolyline.length > 0 && <Polyline positions={routePolyline} color="#3b82f6" weight={5} opacity={0.8} />}
        </MapContainer>

        <div style={{ position: 'absolute', top: '24px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000, backgroundColor: 'rgba(255,255,255,0.95)', backdropFilter: 'blur(8px)', padding: '12px 24px', borderRadius: '30px', boxShadow: '0 8px 32px rgba(0,0,0,0.1)', display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Clock size={20} color="#3b82f6" />
            <span style={{ fontSize: '18px', fontWeight: 800, color: '#1a1a1a' }}>{liveEta}</span>
          </div>
        </div>
      </div>

      <div style={{ padding: '24px', backgroundColor: '#fff', borderTopLeftRadius: '24px', borderTopRightRadius: '24px', boxShadow: '0 -4px 20px rgba(0,0,0,0.05)', position: 'relative', zIndex: 1001 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
          <div>
            <span style={{ display: 'inline-block', padding: '6px 12px', backgroundColor: isDropStage ? '#fff3e0' : '#e8f5e9', color: isDropStage ? '#e65100' : '#2e7d32', borderRadius: '20px', fontSize: '12px', fontWeight: 800, textTransform: 'uppercase' }}>
              {activeOrder.status.includes('WAITING') ? 'Waiting at' : 'Heading to'} {isDropStage ? 'Drop' : 'Pickup'}
            </span>
          </div>
          {timeoutRemaining !== null && !isDropStage && (
            <div style={{ color: '#ef4444', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Clock size={16} /> {Math.floor(timeoutRemaining / 60)}:{(timeoutRemaining % 60).toString().padStart(2, '0')}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '16px', backgroundColor: '#f9fafb', borderRadius: '16px', marginBottom: '24px' }}>
          <UserAvatar src={isDropStage ? userInfo?.profileImageUrl : farmerInfo?.profileImageUrl} alt="Profile" size={56} />
          <div style={{ flex: 1 }}>
            <h3 style={{ margin: '0 0 4px', fontSize: '18px', fontWeight: 800 }}>{isDropStage ? userInfo?.name || (isDonation ? 'NGO' : 'Customer') : farmerInfo?.name || 'Farmer'}</h3>
            <p style={{ margin: 0, fontSize: '14px', color: '#666', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Phone size={14} /> {isDropStage ? userInfo?.phone : farmerInfo?.phone}
            </p>
            <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#9ca3af' }}>
              {isDropStage ? activeOrder.dropAddress : activeOrder.pickupAddress}
            </p>
          </div>
        </div>

        <div style={{ borderTop: '1px solid #f3f4f6', paddingTop: '24px' }}>
          {!activeOrder.status.includes('WAITING') ? (
            <button onClick={handleArrive} disabled={isProcessing} style={{ width: '100%', padding: '16px', borderRadius: '16px', backgroundColor: isDropStage ? 'var(--terracotta-primary)' : 'var(--golden-yellow)', color: '#fff', border: 'none', fontSize: '16px', fontWeight: 800, cursor: 'pointer' }}>
              {isProcessing ? <AgriLoading size={24} color="#fff" /> : `I'm Arrived at ${isDropStage ? 'Drop' : 'Pickup'}`}
            </button>
          ) : (
            <button onClick={() => setShowOtpModal(true)} style={{ width: '100%', padding: '16px', borderRadius: '16px', backgroundColor: '#1f2937', color: '#fff', border: 'none', fontSize: '16px', fontWeight: 800, cursor: 'pointer' }}>
              Verify {isDropStage ? 'Drop' : 'Pickup'} OTP
            </button>
          )}
        </div>
      </div>

      {showOtpModal && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 2000, backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', width: '90%', maxWidth: '400px' }}>
            <h3 style={{ textAlign: 'center', margin: '0 0 8px', fontSize: '24px' }}>Enter {isDropStage ? (isDonation ? 'NGO' : 'User') : 'Farmer'} OTP</h3>
            <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', marginBottom: '24px', marginTop: '24px' }}>
              {[0, 1, 2, 3].map(i => (
                <input
                  key={i}
                  ref={otpRefs[i]}
                  maxLength={1}
                  value={otpValues[i]}
                  onChange={e => {
                    const val = e.target.value.replace(/\D/g, '');
                    const newVals = [...otpValues];
                    newVals[i] = val.slice(-1);
                    setOtpValues(newVals);
                    if (val && i < 3) otpRefs[i + 1].current.focus();
                  }}
                  style={{ width: '60px', height: '60px', borderRadius: '16px', border: '2px solid #e5e7eb', fontSize: '24px', fontWeight: 800, textAlign: 'center' }}
                />
              ))}
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button onClick={() => setShowOtpModal(false)} style={{ flex: 1, padding: '16px', borderRadius: '16px', backgroundColor: '#f3f4f6', color: '#374151', border: 'none', fontWeight: 800 }}>Cancel</button>
              <button onClick={handleVerify} disabled={isProcessing || otpValues.join('').length !== 4} style={{ flex: 1, padding: '16px', borderRadius: '16px', backgroundColor: '#3b82f6', color: '#fff', border: 'none', fontWeight: 800 }}>
                {isProcessing ? <AgriLoading size={20} color="#fff" /> : 'Verify'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DriverStandardDelivery;
