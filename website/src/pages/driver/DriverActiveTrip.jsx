import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { collection, query, where, onSnapshot, doc, updateDoc, getDoc, writeBatch, serverTimestamp, increment, getDocs } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Phone, CheckCircle2, Navigation, X, ShieldAlert, Car, Bike, Truck, Activity } from 'lucide-react';
import UserAvatar from '../../components/common/UserAvatar';
import AgriLoading from '../../components/common/AgriLoading';
import apiClient from '../../utils/apiClient';
import { getDistance } from '../../utils/geoUtils';

// Custom icons
const createIcon = (color, svgContent, badgeText) => L.divIcon({
  className: 'custom-icon',
  html: `<div style="position: relative;">
           <div style="background-color: ${color}; width: 36px; height: 36px; border-radius: 50%; border: 3px solid white; box-shadow: 0 4px 6px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white;">
             ${svgContent || ''}
           </div>
           ${badgeText ? `<div style="position: absolute; top: -5px; right: -5px; background: #1f2937; color: white; border-radius: 50%; width: 18px; height: 18px; font-size: 11px; font-weight: 800; display: flex; align-items: center; justify-content: center; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.2);">${badgeText}</div>` : ''}
         </div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 18]
});

// SVG strings for icons
const homeSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`;
const leafSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/></svg>`;

const hostPickupIcon = createIcon('var(--golden-yellow)', leafSvg, 'H');
const hostDropIcon = createIcon('var(--terracotta-primary)', homeSvg, 'H');
const coLoaderPickupIcon = createIcon('var(--golden-yellow)', leafSvg, 'C');
const coLoaderDropIcon = createIcon('var(--terracotta-primary)', homeSvg, 'C');

const pickupIcon = createIcon('var(--golden-yellow)', leafSvg); // Fallback for standard order
const dropIcon = createIcon('var(--terracotta-primary)', homeSvg); // Fallback for standard order

const getDriverIcon = (vehicleType) => {
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/><circle cx="7" cy="17" r="2"/><path d="M9 17h6"/><circle cx="17" cy="17" r="2"/></svg>`; // default car/minitruck
  
  if (vehicleType) {
    const v = vehicleType.toLowerCase();
    if (v.includes('bike') || v.includes('two')) {
      svg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="5.5" cy="17.5" r="3.5"/><circle cx="18.5" cy="17.5" r="3.5"/><path d="M15 6a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm-3 11.5V14l-3-3 4-3 2 3h2"/></svg>`;
    } else if (v.includes('lorry') || v.includes('truck')) {
      svg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 17h4V5H2v12h3"/><circle cx="8.5" cy="17.5" r="1.5"/><path d="M20 17h2v-3.34a4 4 0 0 0-1.17-2.83L19 9h-5v8h1"/><circle cx="17.5" cy="17.5" r="1.5"/></svg>`;
    }
  }
  return createIcon('#3b82f6', svg);
};

const MapBounds = ({ routeSequence, driverLoc, fallbackPoints }) => {
  const map = useMap();
  useEffect(() => {
    if (!map) return;
    const bounds = L.latLngBounds();
    if (driverLoc) bounds.extend([driverLoc.lat, driverLoc.lon]);
    
    if (routeSequence && routeSequence.length > 0) {
      routeSequence.forEach(stop => bounds.extend([stop.lat, stop.lon]));
    } else if (fallbackPoints) {
      fallbackPoints.forEach(p => { if(p.lat) bounds.extend([p.lat, p.lon]); });
    }
    
    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [50, 50], animate: true });
    }
  }, [map, routeSequence, driverLoc, fallbackPoints]);
  return null;
};

const getDynamicIcon = (stop) => {
  if (stop.role === 'HOST' && stop.type === 'PICKUP') return hostPickupIcon;
  if (stop.role === 'HOST' && stop.type === 'DROP') return hostDropIcon;
  if (stop.role === 'CO_LOADER' && stop.type === 'PICKUP') return coLoaderPickupIcon;
  if (stop.role === 'CO_LOADER' && stop.type === 'DROP') return coLoaderDropIcon;
  return pickupIcon;
};

import { ACTIVE_DRIVER_STATUSES } from '../../utils/constants';
import { formatCurrency } from '../../utils/constants';

const DriverActiveTrip = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeOrder, setActiveOrder] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const [driverLoc, setDriverLoc] = useState(null);
  const [routePolyline, setRoutePolyline] = useState([]);
  const [liveEta, setLiveEta] = useState('-- Mins');
  
  const [farmerProfileImageUrl, setFarmerProfileImageUrl] = useState('');
  const [userProfileImageUrl, setUserProfileImageUrl] = useState('');
  const [coLoaderProfileImageUrl, setCoLoaderProfileImageUrl] = useState('');

  const [showOtpModal, setShowOtpModal] = useState(false);
  const [otpType, setOtpType] = useState('pickup'); // 'pickup' | 'drop' | 'coloader_pickup' | 'coloader_drop'
  const [otpValues, setOtpValues] = useState(['', '', '', '']);
  const otpRefs = [useRef(null), useRef(null), useRef(null), useRef(null)];

  const [farmerPhone, setFarmerPhone] = useState('');
  const [farmerName, setFarmerName] = useState('Farmer');
  const [userPhone, setUserPhone] = useState('');
  const [userName, setUserName] = useState('Customer');
  const [coLoaderPhone, setCoLoaderPhone] = useState('');
  const [coLoaderName, setCoLoaderName] = useState('Co-loader');

  const [isProcessing, setIsProcessing] = useState(false);
  const [driverProfile, setDriverProfile] = useState(null);
  const [toastMsg, setToastMsg] = useState(null);

  const showToast = (msg) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 3000);
  };

  // Fetch Driver Profile
  useEffect(() => {
    if (user?.email) {
      getDoc(doc(db, 'users', user.email)).then(d => {
        if (d.exists()) setDriverProfile(d.data());
      });
    }
  }, [user]);

  // 1. Fetch Active Order (Realtime)
  useEffect(() => {
    if (!user?.email) return;

    const q = query(collection(db, 'orders'), where('driverEmail', '==', user.email));
    const unsub = onSnapshot(q, async (snap) => {
      const activeStatuses = ACTIVE_DRIVER_STATUSES;
      let found = null;
      
      snap.docs.forEach(d => {
        const data = d.data();
        if (activeStatuses.includes(data.status)) found = data;
      });

      if (found) {
        setActiveOrder(found);
        
        const hostOrFarmerEmail = found.hostEmail || found.farmerEmail;
        if (!farmerPhone && hostOrFarmerEmail) {
          const fDoc = await getDoc(doc(db, 'users', hostOrFarmerEmail));
          if (fDoc.exists()) {
            setFarmerPhone(fDoc.data().phone || '');
            const fetchedName = fDoc.data().name;
            setFarmerName((fetchedName && fetchedName !== 'Farmer') ? fetchedName : 'Unknown Farmer');
            setFarmerProfileImageUrl(fDoc.data().profileImageUrl || '');
          }
        }
        if (!userPhone && found.userEmail) {
          const uDoc = await getDoc(doc(db, 'users', found.userEmail));
          if (uDoc.exists()) {
            setUserPhone(uDoc.data().phone || '');
            setUserName(uDoc.data().name || 'Customer');
            setUserProfileImageUrl(uDoc.data().profileImageUrl || '');
          }
        }
        if (!coLoaderPhone && found.coLoaderEmail) {
          const cDoc = await getDoc(doc(db, 'users', found.coLoaderEmail));
          if (cDoc.exists()) {
            setCoLoaderPhone(cDoc.data().phone || '');
            const fetchedCoName = cDoc.data().name;
            setCoLoaderName((fetchedCoName && fetchedCoName !== 'Farmer') ? fetchedCoName : 'Unknown Farmer');
            setCoLoaderProfileImageUrl(cDoc.data().profileImageUrl || '');
          }
        }
      } else {
        navigate('/home/driver');
      }
      setIsLoading(false);
    });

    return () => unsub();
  }, [user, navigate, farmerPhone, userPhone]);

  // 2. Poll GPS Location
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

  // 3. Fetch OSRM Route
  const fetchRoute = useCallback(async (start, end) => {
    if (!start || !end || !start.lat || !end.lat) return;
    try {
      const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${start.lon},${start.lat};${end.lon},${end.lat}?overview=full&geometries=geojson`);
      const data = await res.json();
      if (data.routes && data.routes.length > 0) {
        const route = data.routes[0];
        // Leaflet expects [lat, lon], GeoJSON gives [lon, lat]
        const latLngs = route.geometry.coordinates.map(coord => [coord[1], coord[0]]);
        setRoutePolyline(latLngs);
        setLiveEta(`${Math.round(route.duration / 60)} Mins`);
      }
    } catch (e) {
      console.error("OSRM Error", e);
    }
  }, []);

  useEffect(() => {
    if (driverLoc && activeOrder && activeOrder.routeSequence) {
      const currentIndex = activeOrder.currentRouteIndex || 0;
      if (currentIndex < activeOrder.routeSequence.length) {
        const currentStop = activeOrder.routeSequence[currentIndex];
        fetchRoute(driverLoc, { lat: currentStop.lat, lon: currentStop.lon });
      }
    } else if (driverLoc && activeOrder) {
      // Fallback for legacy static route
      const status = activeOrder.status;
      let targetLoc = null;

      if (status.includes('HOST_PICKUP') || status === 'EN_ROUTE_TO_PICKUP' || status === 'WAITING_AT_PICKUP') {
        targetLoc = { lat: activeOrder.pickupLat, lon: activeOrder.pickupLon };
      } else if (status.includes('CO_LOADER_PICKUP')) {
        targetLoc = { lat: activeOrder.coLoaderPickupLat, lon: activeOrder.coLoaderPickupLon };
      } else if (status === 'IN_TRANSIT' || status.includes('HOST_DROP') || status === 'EN_ROUTE_TO_DROP' || status === 'WAITING_AT_DROP') {
        targetLoc = { lat: activeOrder.dropLat, lon: activeOrder.dropLon };
      } else if (status.includes('CO_LOADER_DROP')) {
        targetLoc = { lat: activeOrder.coLoaderDropLat, lon: activeOrder.coLoaderDropLon };
      }

      if (targetLoc) {
        fetchRoute(driverLoc, targetLoc);
      }
    }
  }, [driverLoc, activeOrder, fetchRoute]);

  if (isLoading || !activeOrder) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', justifyContent: 'center', alignItems: 'center' }}>
        <AgriLoading size={64} />
        <span style={{ marginTop: '16px', fontWeight: 600, color: 'var(--terracotta-primary)' }}>Loading Trip Details...</span>
      </div>
    );
  }

  const { status, pickupLat, pickupLon, dropLat, dropLon, pickupAddress, dropAddress, cropName, cropValue, coLoaderEmail } = activeOrder;
  const isPool = !!coLoaderEmail || !!activeOrder.orderId?.includes('POOL');

  const calculatedTransportFare = isPool 
    ? (parseFloat(activeOrder.totalPayment || 0) + parseFloat(activeOrder.coLoaderPayment || 0))
    : parseFloat(activeOrder.transportFare || 0);

  // Defensive State Reconstruction for Legacy Orders
  const routeSequence = activeOrder.routeSequence || (isPool ? [
    {"id": "HOST_PICKUP", "type": "PICKUP", "role": "HOST", "lat": activeOrder.pickupLat, "lon": activeOrder.pickupLon, "address": activeOrder.pickupAddress, "farmerName": "Unknown Farmer", "farmerPhone": "", "profilePic": "", "cropName": activeOrder.cropName},
    {"id": "CO_LOADER_PICKUP", "type": "PICKUP", "role": "CO_LOADER", "lat": activeOrder.coLoaderPickupLat, "lon": activeOrder.coLoaderPickupLon, "address": activeOrder.coLoaderPickupAddress, "farmerName": "Co-loader", "farmerPhone": "", "profilePic": "", "cropName": activeOrder.coLoaderCropName},
    {"id": "HOST_DROP", "type": "DROP", "role": "HOST", "lat": activeOrder.dropLat, "lon": activeOrder.dropLon, "address": activeOrder.dropAddress, "farmerName": "Unknown Farmer", "farmerPhone": "", "profilePic": "", "cropName": activeOrder.cropName},
    {"id": "CO_LOADER_DROP", "type": "DROP", "role": "CO_LOADER", "lat": activeOrder.coLoaderDropLat, "lon": activeOrder.coLoaderDropLon, "address": activeOrder.coLoaderDropAddress, "farmerName": "Co-loader", "farmerPhone": "", "profilePic": "", "cropName": activeOrder.coLoaderCropName}
  ] : [
    {"id": "HOST_PICKUP", "type": "PICKUP", "role": "HOST", "lat": activeOrder.pickupLat, "lon": activeOrder.pickupLon, "address": activeOrder.pickupAddress, "farmerName": "Unknown Farmer", "farmerPhone": "", "profilePic": "", "cropName": activeOrder.cropName},
    {"id": "HOST_DROP", "type": "DROP", "role": "HOST", "lat": activeOrder.dropLat, "lon": activeOrder.dropLon, "address": activeOrder.dropAddress, "farmerName": "Unknown Farmer", "farmerPhone": "", "profilePic": "", "cropName": activeOrder.cropName}
  ]);

  const currentIndex = activeOrder.currentRouteIndex ?? 0;
  let currentStop = routeSequence[currentIndex] ?? null;

  if (currentStop) {
    currentStop = { ...currentStop };
    if (currentStop.role === 'HOST') {
      currentStop.farmerName = farmerName || currentStop.farmerName;
      currentStop.farmerPhone = farmerPhone || currentStop.farmerPhone;
      currentStop.profilePic = farmerProfileImageUrl || currentStop.profilePic;
    } else if (currentStop.role === 'CO_LOADER') {
      currentStop.farmerName = coLoaderName || currentStop.farmerName;
      currentStop.farmerPhone = coLoaderPhone || currentStop.farmerPhone;
      currentStop.profilePic = coLoaderProfileImageUrl || currentStop.profilePic;
    }
  }

  const getMarkerPosition = (stop, allStops) => {
    let lat = stop.lat;
    let lon = stop.lon;
    if (stop.role === 'CO_LOADER') {
      const hostStop = allStops.find(s => s.role === 'HOST' && s.type === stop.type);
      if (hostStop && hostStop.lat === stop.lat && hostStop.lon === stop.lon) {
        lon += 0.00015; // Visual offset for overlapping markers
      }
    }
    return [lat, lon];
  };

  const handleArriveStop = async () => {
    if (!currentStop) return;
    
    setDriverLoc({ lat: currentStop.lat, lon: currentStop.lon });
    
    try {
      await apiClient.post('/api/logistics/arrive-stop', {
        orderId: activeOrder.orderId,
        stopId: currentStop.id
      });
      
      // Update local coords independently of backend status to ensure map traces correctly
      await updateDoc(doc(db, 'orders', activeOrder.orderId), {
        driverLat: currentStop.lat,
        driverLon: currentStop.lon
      }).catch(() => {});
      
      setOtpType(currentStop.id);
      setOtpValues(['', '', '', '']);
      setShowOtpModal(true);
    } catch (err) {
      console.error(err);
      showToast("Failed to record arrival securely.");
    }
  };

  const handleVerifyStop = async () => {
    if (!currentStop) return;

    const enteredOtp = otpValues.join('');
    setIsProcessing(true);
    
    const nextIndex = currentIndex + 1;
    const isFinalStop = nextIndex >= routeSequence.length;
    const nextStop = !isFinalStop ? routeSequence[nextIndex] : null;

    try {
      const payload = {
        orderId: activeOrder.orderId,
        driverEmail: user?.email,
        isPool: isPool,
        cropValue: parseFloat(cropValue) || 0,
        transportFare: parseFloat(calculatedTransportFare) || 0,
        cropName: cropName || 'Crop',
        farmerEmail: activeOrder.farmerEmail,
        stopId: currentStop.id,
        enteredOtp: enteredOtp,
        isFinalStop: isFinalStop,
        nextStatus: nextStop ? `EN_ROUTE_TO_${nextStop.id}` : null,
        nextIndex: nextIndex
      };

      const res = await apiClient.post('/api/logistics/complete-delivery', payload);

      if (res.data.success) {
        if (isFinalStop) {
          showToast("Trip Completed! Earnings added to wallet.", true);
          setShowOtpModal(false);
          setActiveOrder(null);
          setTimeout(() => {
            navigate('/home/driver');
          }, 2000);
        } else {
          setShowOtpModal(false);
        }
      } else {
        showToast(res.data.error || `Invalid OTP for ${currentStop.role} ${currentStop.type}`);
      }
    } catch (e) {
      console.error(e);
      showToast(e.response?.data?.error || "Network Error Verifying OTP");
    }
    setIsProcessing(false);
  };




  const handleOtpChange = (index, value) => {
    const val = value.replace(/\D/g, '');
    if (!val) {
      const newVals = [...otpValues];
      newVals[index] = '';
      setOtpValues(newVals);
      if (index > 0) otpRefs[index - 1].current.focus();
      return;
    }
    const newVals = [...otpValues];
    newVals[index] = val.slice(-1);
    setOtpValues(newVals);
    if (index < 3) otpRefs[index + 1].current.focus();
  };

  // Determine Distance logic for arriving based on TSP dynamic route
  const distanceToNextStop = (driverLoc && currentStop) 
    ? getDistance(driverLoc.lat, driverLoc.lon, currentStop.lat, currentStop.lon) 
    : Infinity;
  const canArriveStop = distanceToNextStop < 1000; // Allow arrival if within 1km

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', backgroundColor: '#f9fafb' }}>
      {/* Map Area */}
      <div style={{ flex: 1, position: 'relative' }}>
        <MapContainer 
          center={driverLoc ? [driverLoc.lat, driverLoc.lon] : [pickupLat, pickupLon]} 
          zoom={15} 
          style={{ height: '100%', width: '100%' }}
          zoomControl={false}
        >
          <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
          <MapBounds 
            routeSequence={routeSequence} 
            driverLoc={driverLoc} 
            fallbackPoints={[
              { lat: pickupLat, lon: pickupLon },
              { lat: dropLat, lon: dropLon },
              ...(isPool ? [{ lat: activeOrder.coLoaderPickupLat, lon: activeOrder.coLoaderPickupLon }, { lat: activeOrder.coLoaderDropLat, lon: activeOrder.coLoaderDropLon }] : [])
            ]} 
          />
          
          {true ? (
            routeSequence.map((stop, i, arr) => (
              <Marker key={i} position={getMarkerPosition(stop, arr)} icon={getDynamicIcon(stop)} />
            ))
          ) : (
            <>
              <Marker position={[pickupLat, pickupLon]} icon={pickupIcon} />
              <Marker position={[dropLat, dropLon]} icon={dropIcon} />
              {isPool && activeOrder.coLoaderPickupLat && (
                <Marker position={[activeOrder.coLoaderPickupLat, activeOrder.coLoaderPickupLon]} icon={pickupIcon} />
              )}
              {isPool && activeOrder.coLoaderDropLat && (
                <Marker position={[activeOrder.coLoaderDropLat, activeOrder.coLoaderDropLon]} icon={dropIcon} />
              )}
            </>
          )}
          
          {driverLoc && <Marker position={[driverLoc.lat, driverLoc.lon]} icon={getDriverIcon(driverProfile?.vehicleType)} />}
          {routePolyline.length > 0 && <Polyline positions={routePolyline} color="#3b82f6" weight={5} opacity={0.8} />}
        </MapContainer>

        {/* Floating ETA & Progress */}
        <div style={{
          position: 'absolute', top: '24px', left: '50%', transform: 'translateX(-50%)', zIndex: 1000,
          backgroundColor: 'rgba(255,255,255,0.95)', backdropFilter: 'blur(8px)',
          padding: '12px 24px', borderRadius: '30px', boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Navigation size={18} color="#3b82f6" />
            <span style={{ fontWeight: 800, color: '#1f2937' }}>{liveEta}</span>
          </div>
          {true && (
            <span style={{ fontSize: '12px', fontWeight: 700, color: '#6b7280' }}>
              Stop {currentIndex + 1} of {routeSequence.length}
            </span>
          )}
        </div>
      </div>

      {/* Action Card */}
      <div style={{ 
        backgroundColor: '#fff', borderTopLeftRadius: '32px', borderTopRightRadius: '32px', 
        padding: '32px 24px', boxShadow: '0 -12px 48px rgba(0,0,0,0.08)', zIndex: 1000,
        maxHeight: '60vh', overflowY: 'auto'
      }}>
        {currentStop && (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
              <div>
                <h2 style={{ margin: '0 0 4px 0', fontSize: '24px', fontWeight: 900, color: '#111827' }}>{currentStop.cropName}</h2>
                <span style={{ display: 'inline-block', padding: '6px 12px', backgroundColor: currentStop.type === 'PICKUP' ? '#e8f5e9' : '#fff3e0', color: currentStop.type === 'PICKUP' ? '#2e7d32' : '#e65100', borderRadius: '20px', fontSize: '12px', fontWeight: 800, textTransform: 'uppercase' }}>
                  {status.includes('WAITING') ? `Waiting at ${currentStop.role} ${currentStop.type}` : `Heading to ${currentStop.role} ${currentStop.type}`}
                </span>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ display: 'block', fontSize: '12px', color: '#6b7280', fontWeight: 600 }}>Fare</span>
                <span style={{ fontSize: '24px', fontWeight: 900, color: 'var(--terracotta-primary)' }}>{formatCurrency(calculatedTransportFare)}</span>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '16px', backgroundColor: '#f9fafb', borderRadius: '16px', marginBottom: '24px' }}>
              <UserAvatar src={currentStop.profilePic} alt="Farmer" size={48} style={{ border: '2px solid #fff', boxShadow: '0 4px 6px rgba(0,0,0,0.05)' }} />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '16px', fontWeight: 800, color: '#1f2937' }}>{currentStop.farmerName}</div>
                <div style={{ fontSize: '13px', color: '#6b7280', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>{currentStop.farmerPhone}</div>
              </div>
              <div style={{ padding: '6px 10px', backgroundColor: currentStop.role === 'HOST' ? '#eff6ff' : '#f5f3ff', color: currentStop.role === 'HOST' ? '#3b82f6' : '#8b5cf6', borderRadius: '8px', fontSize: '11px', fontWeight: 800 }}>
                {currentStop.role.replace('_', ' ')}
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginBottom: '24px' }}>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                <div style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: currentStop.type === 'PICKUP' ? 'var(--golden-yellow)' : 'var(--terracotta-primary)', marginTop: '4px', flexShrink: 0 }} />
                <div style={{ flex: 1, fontSize: '15px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>{currentStop.address}</div>
              </div>
            </div>

            <div style={{ borderTop: '1px solid #f3f4f6', paddingTop: '24px' }}>
              {!status.includes('WAITING') ? (
                <button
                  onClick={handleArriveStop}
                  disabled={isProcessing} 
                  style={{
                    width: '100%', padding: '16px', borderRadius: '16px',
                    backgroundColor: currentStop.type === 'PICKUP' ? 'var(--golden-yellow)' : 'var(--terracotta-primary)', color: '#fff', border: 'none',
                    fontSize: '18px', fontWeight: 800, cursor: isProcessing ? 'not-allowed' : 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    boxShadow: `0 8px 16px ${currentStop.type === 'PICKUP' ? 'rgba(251, 191, 36, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`
                  }}
                >
                  {isProcessing ? <AgriLoading size={24} color="#fff" /> : `I'm Arrived at ${currentStop.role.replace('_', ' ')} ${currentStop.type}`}
                </button>
              ) : (
                <button
                  onClick={() => setShowOtpModal(true)}
                  style={{
                    width: '100%', padding: '16px', borderRadius: '16px',
                    backgroundColor: '#1f2937', color: '#fff', border: 'none',
                    fontSize: '18px', fontWeight: 800, cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    boxShadow: '0 8px 16px rgba(31, 41, 55, 0.3)'
                  }}
                >
                  Verify {currentStop.type} OTP
                </button>
              )}
            </div>
          </>
        )}
      </div>

      {/* Premium OTP Modal */}
      {showOtpModal && currentStop && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, zIndex: 2000,
          backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px'
        }}>
          <div style={{
            backgroundColor: '#fff', borderRadius: '24px', padding: '32px', width: '100%', maxWidth: '400px',
            boxShadow: '0 24px 48px rgba(0,0,0,0.2)', position: 'relative'
          }}>
            <h2 style={{ margin: '0 0 8px 0', fontSize: '24px', fontWeight: 900, color: '#111827', textAlign: 'center' }}>
              {`${currentStop.type.charAt(0) + currentStop.type.slice(1).toLowerCase()} Verification`}
            </h2>
            
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: '24px' }}>
              <div style={{ width: 64, height: 64, borderRadius: '50%', backgroundColor: '#e5e7eb', marginBottom: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', border: '3px solid #fff', boxShadow: '0 4px 6px rgba(0,0,0,0.1)' }}>
                <img 
                  src={currentStop.profilePic || '/favicon.svg'}
                  alt="Profile" 
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  onError={(e)=>{e.target.src='/favicon.svg'}}
                />
              </div>
              <h3 style={{ margin: '0', fontSize: '18px', fontWeight: 800, color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
                {currentStop.farmerName}
              </h3>
              <div style={{ marginTop: '4px', padding: '4px 8px', background: currentStop.role === 'CO_LOADER' ? '#f0fdf4' : '#fff7ed', border: `1px solid ${currentStop.role === 'CO_LOADER' ? '#bbf7d0' : '#ffedd5'}`, borderRadius: '6px', fontSize: '10px', fontWeight: 800, color: currentStop.role === 'CO_LOADER' ? '#166534' : '#c2410c', letterSpacing: '0.5px' }}>
                {currentStop.role.replace('_', ' ')}
              </div>
              <p style={{ margin: '8px 0 0', color: '#6b7280', fontSize: '14px', fontWeight: 600 }}>
                {currentStop.farmerPhone}
              </p>
            </div>

            <p style={{ margin: '0 0 24px 0', color: '#6b7280', fontSize: '14px', fontWeight: 500, textAlign: 'center' }}>
              Enter the 4-digit code to confirm.
            </p>

            <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', marginBottom: '32px' }}>
              {[0, 1, 2, 3].map(i => (
                <input
                  key={i}
                  ref={otpRefs[i]}
                  type="text"
                  maxLength={1}
                  value={otpValues[i]}
                  onChange={(e) => handleOtpChange(i, e.target.value)}
                  disabled={isProcessing}
                  style={{
                    width: '60px', height: '60px', borderRadius: '16px', border: '2px solid #e5e7eb',
                    fontSize: '24px', fontWeight: 900, textAlign: 'center', outline: 'none',
                    backgroundColor: isProcessing ? '#f9fafb' : '#fff', color: '#111827'
                  }}
                />
              ))}
            </div>

            <div style={{ display: 'flex', gap: '12px' }}>
              <button
                onClick={() => setShowOtpModal(false)}
                disabled={isProcessing}
                style={{
                  flex: 1, padding: '16px', borderRadius: '16px', backgroundColor: '#f3f4f6', 
                  color: '#4b5563', border: 'none', fontSize: '16px', fontWeight: 700, 
                  cursor: isProcessing ? 'not-allowed' : 'pointer'
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleVerifyStop}
                disabled={isProcessing || otpValues.join('').length !== 4}
                style={{
                  flex: 1, padding: '16px', borderRadius: '16px',
                  backgroundColor: currentStop.type === 'PICKUP' ? 'var(--golden-yellow)' : 'var(--terracotta-primary)', 
                  color: '#fff', border: 'none', fontSize: '16px', fontWeight: 800, 
                  cursor: (isProcessing || otpValues.join('').length !== 4) ? 'not-allowed' : 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                  opacity: (isProcessing || otpValues.join('').length !== 4) ? 0.6 : 1
                }}
              >
                {isProcessing ? <AgriLoading size={20} color="#fff" /> : <CheckCircle2 size={24} />}
                <span>Verify</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Simple Toast */}
      {toastMsg && (
        <div style={{
          position: 'fixed', bottom: '24px', left: '50%', transform: 'translateX(-50%)',
          backgroundColor: '#333', color: '#fff', padding: '12px 24px', borderRadius: '8px',
          boxShadow: '0 4px 12px rgba(0,0,0,0.1)', zIndex: 9999, fontSize: '14px', fontWeight: 600
        }}>
          {toastMsg}
        </div>
      )}
    </div>
  );
};

export default DriverActiveTrip;
