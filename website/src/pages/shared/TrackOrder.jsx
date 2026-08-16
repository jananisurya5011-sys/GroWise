import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { doc, onSnapshot, getDoc } from 'firebase/firestore';
import { db } from '../../utils/firebase';
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet';
import L from 'leaflet';
import { motion } from 'framer-motion';
import 'leaflet/dist/leaflet.css';
import UserAvatar from '../../components/common/UserAvatar';
import { useAuth } from '../../contexts/AuthContext';
import { ArrowLeft, Navigation, Phone, CheckCircle, Clock, MapPin, Truck, Package, User, AlertTriangle } from 'lucide-react';
import apiClient from '../../utils/apiClient';
import { formatCurrency } from '../../utils/constants';

// Fix leaflet default icon issue
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Define custom icons using SVG wrappers exactly like DriverActiveTrip
const createIcon = (color, svgContent) => L.divIcon({
  className: 'custom-icon',
  html: `<div style="background-color: ${color}; width: 36px; height: 36px; border-radius: 50%; border: 3px solid white; box-shadow: 0 4px 6px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white;">
           ${svgContent || ''}
         </div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 18]
});

const homeSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`;
const leafSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/></svg>`;

const pickupIcon = createIcon('var(--golden-yellow, #FDB931)', leafSvg);
const dropIcon = createIcon('var(--terracotta-primary, #7C3D12)', homeSvg);

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

const C = {
  terracotta: '#7C3D12',
  terracottaLight: '#c2845c',
  golden: '#FDB931',
  peach: '#FDF0E8',
  textDark: '#1A1A1A',
  textMuted: '#7A7A7A',
  green: '#2E7D32',
  blue: '#3b82f6',
};

const TrackOrder = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [order, setOrder] = useState(null);
  const [driverInfo, setDriverInfo] = useState(null);
  const [loadingDriver, setLoadingDriver] = useState(false);
  const [routePolyline, setRoutePolyline] = useState([]);
  const [etaStr, setEtaStr] = useState('Calculating...');
  
  // Real-time listener for the order
  useEffect(() => {
    if (!orderId) return;
    const unsub = onSnapshot(doc(db, 'orders', orderId), (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        if (data.orderId?.startsWith('GW-DON-') && data.vehicleType === 'Self Pickup') {
          navigate(`/track-self-pickup/${orderId}`, { replace: true });
        } else {
          setOrder(data);
        }
      }
    });
    return () => unsub();
  }, [orderId, navigate]);
  
  // Fetch driver profile to always get the latest profile photo
  useEffect(() => {
    if (order?.driverEmail) {
      const fetchDriver = async () => {
        setLoadingDriver(true);
        try {
          const docSnap = await getDoc(doc(db, 'users', order.driverEmail));
          if (docSnap.exists()) {
            setDriverInfo(docSnap.data());
          }
        } catch (e) {
          console.error("Failed to fetch driver profile:", e);
        } finally {
          setLoadingDriver(false);
        }
      };
      fetchDriver();
    } else {
      setDriverInfo(null);
    }
  }, [order?.driverEmail]);
  
  // Fetch OSRM Route
  useEffect(() => {
    if (order?.pickupLat && order?.pickupLon && order?.dropLat && order?.dropLon) {
      const fetchRoute = async () => {
        try {
          const url = `https://router.project-osrm.org/route/v1/driving/${order.pickupLon},${order.pickupLat};${order.dropLon},${order.dropLat}?overview=full&geometries=geojson`;
          const res = await fetch(url);
          const data = await res.json();
          if (data.routes && data.routes.length > 0) {
            const coords = data.routes[0].geometry.coordinates.map(c => [c[1], c[0]]); // GeoJSON is [lon, lat], Leaflet is [lat, lon]
            setRoutePolyline(coords);
            const durationSecs = data.routes[0].duration;
            setEtaStr(`${Math.ceil(durationSecs / 60)} Mins`);
          }
        } catch (e) { console.error("Error fetching OSRM route:", e); }
      };
      fetchRoute();
    }
  }, [order?.pickupLat, order?.pickupLon, order?.dropLat, order?.dropLon]);
  
  if (!order) {
    return (
      <div style={{ display: 'flex', height: '100vh', justifyContent: 'center', alignItems: 'center', background: C.peach }}>
        <p style={{ color: C.terracotta, fontWeight: 700 }}>Loading order details...</p>
      </div>
    );
  }
  
  const isFarmer = user?.email === order.farmerEmail;
  const otpToShow = isFarmer ? order.pickupOtp : order.dropOtp;
  const titleToShow = isFarmer ? "YOUR FARM PICKUP OTP" : "YOUR SECURE DROP OTP";
  
  const driverNameDisplay = order.driverName || driverInfo?.name || driverInfo?.username || "Assigning Driver...";
  const driverPhoneDisplay = order.driverPhone || driverInfo?.phone || '';
  const driverIdDisplay = order.driverId || driverInfo?.driverId || 'N/A';
  const driverPicDisplay = order.driverProfilePic || order.profileImageUrl || driverInfo?.profileImageUrl || '/app_logo.svg';

  const vehicle = order.vehicleType || "Any";
  const status = order.status || "PENDING";
  
  const mapCenter = [
    order.pickupLat || 20.5937, 
    order.pickupLon || 78.9629
  ];

  const renderTimeline = () => {
    const states = [
      { id: 'PENDING_DRIVER', label: 'Driver Assigned' },
      { id: 'EN_ROUTE_TO_PICKUP', label: 'Reaching Pickup' },
      { id: 'WAITING_AT_PICKUP', label: 'Waiting at Pickup' },
      { id: 'IN_TRANSIT', label: 'Reaching Drop' },
      { id: 'WAITING_AT_DROP', label: 'Waiting at Drop' },
      { id: 'DELIVERED', label: 'Delivered' }
    ];
    
    let currentIndex = 0;
    if (status === 'EN_ROUTE_TO_PICKUP') currentIndex = 1;
    else if (status === 'WAITING_AT_PICKUP') currentIndex = 2;
    else if (status === 'IN_TRANSIT') currentIndex = 3;
    else if (status === 'WAITING_AT_DROP') currentIndex = 4;
    else if (status === 'DELIVERED') currentIndex = 5;
    
    return (
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16, marginBottom: 24, padding: '0 10px' }}>
        {states.map((s, i) => (
          <div key={s.id} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 1, position: 'relative' }}>
            {i !== 0 && (
              <div style={{ position: 'absolute', top: 12, left: '-50%', width: '100%', height: 3, background: i <= currentIndex ? C.green : '#E0E0E0', zIndex: 0 }} />
            )}
            <div style={{ 
              width: 26, height: 26, borderRadius: '50%', background: i <= currentIndex ? C.green : '#F5F5F5', 
              border: `2px solid ${i <= currentIndex ? C.green : '#E0E0E0'}`, 
              display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1 
            }}>
              {i < currentIndex || (currentIndex === 5 && i === 5) ? <CheckCircle size={14} color="white" /> : <span style={{ width: 8, height: 8, borderRadius: '50%', background: i === currentIndex ? 'white' : '#CCC' }} />}
            </div>
            <span style={{ fontSize: 10, fontWeight: 700, color: i <= currentIndex ? C.textDark : C.textMuted, marginTop: 8, textAlign: 'center' }}>{s.label}</span>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: '#F5F5F5' }}>
      <div style={{ background: 'white', padding: '16px 24px', display: 'flex', alignItems: 'center', gap: 16, boxShadow: '0 2px 10px rgba(0,0,0,0.05)', zIndex: 10 }}>
        <div onClick={() => navigate(-1)} style={{ cursor: 'pointer', background: '#F9F9F9', width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ArrowLeft size={20} color={C.textDark} />
        </div>
        <div style={{ flex: 1 }}>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: C.textDark }}>Track Order</h2>
          <p style={{ margin: '2px 0 0', fontSize: 13, color: C.textMuted, fontWeight: 600 }}>ID: {order.orderId}</p>
        </div>
      </div>
      
      <div style={{ flex: 1, position: 'relative' }}>
        <MapContainer center={mapCenter} zoom={13} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            attribution='&copy; <a href="https://osm.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {routePolyline.length > 0 && <Polyline positions={routePolyline} color={C.blue} weight={5} opacity={0.7} />}
          {order.pickupLat && order.pickupLon && (
            <Marker position={[order.pickupLat, order.pickupLon]} icon={pickupIcon}>
              <Popup>Pickup: {order.pickupAddress}</Popup>
            </Marker>
          )}
          {order.dropLat && order.dropLon && (
            <Marker position={[order.dropLat, order.dropLon]} icon={dropIcon}>
              <Popup>Drop: {order.dropAddress}</Popup>
            </Marker>
          )}
          {order.driverLat && order.driverLon && status !== 'PENDING_DRIVER' && (
            <Marker position={[order.driverLat, order.driverLon]} icon={getDriverIcon(vehicle)}>
              <Popup>Driver is here</Popup>
            </Marker>
          )}
        </MapContainer>
        
        {/* Floating Card for Order details & OTP */}
        <motion.div initial={{ y: 300, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ type: "spring", damping: 25, stiffness: 200 }} style={{ position: 'absolute', bottom: 0, left: 0, right: 0, background: 'white', borderTopLeftRadius: 32, borderTopRightRadius: 32, padding: '24px', boxShadow: '0 -4px 20px rgba(0,0,0,0.1)', zIndex: 1000, maxHeight: '60vh', overflowY: 'auto' }}>
          
          <div style={{ width: 40, height: 4, background: '#E0E0E0', borderRadius: 2, margin: '0 auto 20px' }} />
          
          <div style={{ display: 'flex', gap: '16px', marginBottom: '20px' }}>
            <img 
              src={order.imageUrl ? (order.imageUrl.startsWith("http") ? order.imageUrl : `http://localhost:5000${order.imageUrl}`) : 'https://placehold.co/100x100/FFF9F5/D85A38?text=Crop'} 
              alt={order.cropName} 
              style={{ width: 80, height: 80, borderRadius: 12, objectFit: 'cover', border: '1px solid rgba(253, 185, 49, 0.4)' }} 
              onError={(e) => { e.target.onerror = null; e.target.src = 'https://placehold.co/100x100/FFF9F5/D85A38?text=Crop'; }}
            />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
              <p style={{ margin: '0 0 4px 0', fontSize: 24, fontWeight: 900, color: C.textDark, textTransform: 'capitalize' }}>{order.cropName}</p>
              <p style={{ margin: '0 0 4px 0', fontSize: 14, color: '#666', fontWeight: 600 }}>Quantity: <span style={{ color: C.green }}>{order.weightKg} KG</span></p>
              {order.isDonation && <p style={{ margin: '0', fontSize: 14, color: '#666', fontWeight: 600 }}>Requested by: <span style={{ color: C.textDark }}>{order.userEmail?.split('@')[0]}</span></p>}
            </div>
          </div>
          
          {renderTimeline()}
          
          {status === 'PENDING_DRIVER' ? (
            <div style={{ background: C.peach, borderRadius: 16, padding: 20, textAlign: 'center', border: `1px solid ${C.golden}`, marginTop: 16 }}>
              <div style={{ width: 60, height: 60, borderRadius: '50%', background: 'rgba(253, 185, 49, 0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 12px' }}>
                <Clock size={32} color={C.terracotta} />
              </div>
              <h4 style={{ margin: '0 0 8px', fontSize: 18, color: C.textDark, fontWeight: 800 }}>Finding a Driver...</h4>
              <p style={{ margin: '0 0 16px', fontSize: 14, color: C.textMuted }}>Requested Vehicle: {vehicle}</p>
              
              <div style={{ background: '#FFF9F5', padding: 16, borderRadius: 12, border: `1px dashed ${C.golden}` }}>
                <p style={{ fontSize: 12, fontWeight: 700, color: C.terracotta, margin: '0 0 4px', textTransform: 'uppercase' }}>{titleToShow}</p>
                <p style={{ fontSize: 10, color: '#7A7A7A', margin: '0 0 8px', fontWeight: 600 }}>Order ID: {order.orderId}</p>
                <p style={{ fontSize: 32, fontWeight: 900, color: C.terracotta, margin: 0, letterSpacing: 6 }}>{otpToShow}</p>
              </div>
            </div>
          ) : status === 'COMPLETED' ? (
            <div style={{ background: '#E8F5E9', borderRadius: 16, padding: 20, textAlign: 'center', marginTop: 16, border: '1px solid #C8E6C9' }}>
              <CheckCircle size={40} color="#2E7D32" style={{ margin: '0 auto 12px' }} />
              <h4 style={{ margin: '0 0 8px', fontSize: 20, color: '#2E7D32', fontWeight: 900 }}>Delivery Completed</h4>
              <p style={{ margin: 0, fontSize: 14, color: '#1B5E20', fontWeight: 600 }}>
                This order was successfully delivered and closed.
              </p>
            </div>
          ) : (
            <div style={{ background: 'white', borderRadius: 16, border: `1px solid #EEE`, padding: 16, marginTop: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <div style={{ width: 56, height: 56, borderRadius: '50%', background: C.peach, border: `2px solid ${C.golden}`, display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', flexShrink: 0 }}>
                  {loadingDriver ? (
                    <div style={{ width: '100%', height: '100%', background: '#d1d5db', animation: 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite' }} />
                  ) : (
                    <UserAvatar src={driverPicDisplay} alt="Driver" size={56} />
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  <h4 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: C.textDark }}>{driverNameDisplay}</h4>
                  <p style={{ margin: '2px 0 0', fontSize: 13, color: C.blue, fontWeight: 700 }}>
                    {(() => {
                      const id = driverIdDisplay !== 'N/A' ? driverIdDisplay : '';
                      if (!id) return 'GW-D0000';
                      const numMatch = String(id).match(/\d+/);
                      return numMatch ? `GW-D${numMatch[0].padStart(4, '0')}` : id;
                    })()}
                  </p>
                  <p style={{ margin: '2px 0 4px', fontSize: 13, color: C.textMuted, fontWeight: 600 }}>
                    {driverPhoneDisplay}
                  </p>
                </div>
              </div>
              
              <div style={{ background: '#FFF9F5', padding: 16, borderRadius: 12, border: `1px solid rgba(253, 185, 49, 0.4)`, marginTop: 16, textAlign: 'center' }}>
                <p style={{ fontSize: 12, fontWeight: 700, color: C.terracotta, margin: '0 0 4px', textTransform: 'uppercase' }}>{titleToShow}</p>
                <p style={{ fontSize: 10, color: '#7A7A7A', margin: '0 0 8px', fontWeight: 600 }}>Order ID: {order.orderId}</p>
                <p style={{ fontSize: 32, fontWeight: 900, color: C.terracotta, margin: 0, letterSpacing: 6 }}>{otpToShow}</p>
                <p style={{ fontSize: 12, color: C.textMuted, margin: '8px 0 0', fontWeight: 600 }}>Share this securely with the driver</p>
              </div>
            </div>
          )}

          {/* Order Details Section */}
          <div style={{ marginTop: 24 }}>
            <h4 style={{ margin: '0 0 12px', fontSize: 16, fontWeight: 800, color: C.textDark }}>Order Details</h4>
            <div style={{ background: '#FAFAFA', borderRadius: 16, border: '1px solid #EEE', overflow: 'hidden' }}>
              <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #EEE' }}>
                <span style={{ color: C.textMuted, fontSize: 14, fontWeight: 600 }}>Crop Amount ({order.weightKg} KG)</span>
                <span style={{ color: C.textDark, fontSize: 14, fontWeight: 800 }}>{formatCurrency(parseFloat(order.cropValue || 0).toFixed(2))}</span>
              </div>
              <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #EEE' }}>
                <span style={{ color: C.textMuted, fontSize: 14, fontWeight: 600 }}>Transport Fare ({parseFloat(order.distanceKm || 0).toFixed(1)} km)</span>
                <span style={{ color: C.textDark, fontSize: 14, fontWeight: 800 }}>{formatCurrency(parseFloat(order.transportFare || 0).toFixed(2))}</span>
              </div>
              <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', background: '#FFF' }}>
                <span style={{ color: C.textDark, fontSize: 16, fontWeight: 800 }}>Grand Total</span>
                <span style={{ color: C.green, fontSize: 18, fontWeight: 900 }}>{formatCurrency(parseFloat(order.totalPaid || 0).toFixed(2))}</span>
              </div>
            </div>
            
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12, padding: '0 4px' }}>
              <span style={{ color: C.textMuted, fontSize: 12, fontWeight: 600 }}>Invoice Status: <strong style={{ color: C.green }}>PAID</strong></span>
              <span style={{ color: C.textMuted, fontSize: 12, fontWeight: 600 }}>Date: {new Date(order.timestamp).toLocaleDateString()}</span>
            </div>
          </div>
          
        </motion.div>
      </div>
    </div>
  );
};

export default TrackOrder;
