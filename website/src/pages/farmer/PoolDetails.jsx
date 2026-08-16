import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { doc, onSnapshot } from 'firebase/firestore';
import { ArrowLeft, CheckCircle, Clock, Truck, MapPin, User, Phone, Navigation, Shield, Package, X, Trash2 } from 'lucide-react';
import AgriLoading from '../../components/common/AgriLoading';
import { MapContainer, TileLayer, Marker, Polyline } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import apiClient from '../../utils/apiClient';
import { formatCurrency } from '../../utils/constants';
import UserAvatar from '../../components/common/UserAvatar';

// Fix leaflet default icon issue
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const createIcon = (color, svgContent) => L.divIcon({
  className: 'custom-icon',
  html: `<div style="background-color: ${color}; width: 36px; height: 36px; border-radius: 50%; border: 3px solid white; box-shadow: 0 4px 6px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white;">
           ${svgContent || ''}
         </div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 18]
});
const homeSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`;
const leafSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 12 12"/></svg>`;
const pickupIcon = createIcon('var(--golden-yellow, #FDB931)', leafSvg);
const dropIcon = createIcon('var(--terracotta-primary, #7C3D12)', homeSvg);

const PoolDetails = () => {
  const { poolId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [pool, setPool] = useState(null);
  const [driver, setDriver] = useState(null);
  const [coLoader, setCoLoader] = useState(null);
  const [hostProfile, setHostProfile] = useState(null);
  const [routePolyline, setRoutePolyline] = useState([]);

  useEffect(() => {
    if (!poolId) return;
    const unsub = onSnapshot(doc(db, 'orders', poolId), async (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        setPool(data);
        if (data.driverEmail) {
          try {
            const dSnap = await apiClient.post('/api/profile/fetch-details', { email: data.driverEmail });
            if (dSnap.data) setDriver(dSnap.data);
          } catch (e) {
            console.error(e);
          }
        }
        if (data.coLoaderEmail) {
          try {
            const cSnap = await apiClient.post('/api/profile/fetch-details', { email: data.coLoaderEmail });
            if (cSnap.data) setCoLoader(cSnap.data);
          } catch (e) {
            console.error(e);
          }
        }
        if (data.hostEmail) {
          try {
            const hSnap = await apiClient.post('/api/profile/fetch-details', { email: data.hostEmail });
            if (hSnap.data) setHostProfile(hSnap.data);
          } catch (e) {
            console.error(e);
          }
        }
      }
    });
    return () => unsub();
  }, [poolId]);

  useEffect(() => {
    if (!pool) return;
    const fetchRoute = async () => {
      try {
        let waypoints = `${pool.pickupLon},${pool.pickupLat};${pool.dropLon},${pool.dropLat}`;
        if (pool.coLoaderEmail && pool.coLoaderPickupLat && pool.coLoaderDropLat) {
          waypoints = `${pool.pickupLon},${pool.pickupLat};${pool.coLoaderPickupLon},${pool.coLoaderPickupLat};${pool.dropLon},${pool.dropLat};${pool.coLoaderDropLon},${pool.coLoaderDropLat}`;
        }
        
        const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${waypoints}?overview=full&geometries=geojson`);
        const data = await res.json();
        if (data.routes && data.routes.length > 0) {
          const coords = data.routes[0].geometry.coordinates.map(c => [c[1], c[0]]);
          setRoutePolyline(coords);
        }
      } catch (e) {
        console.error("OSRM Route Error", e);
      }
    };
    fetchRoute();
  }, [pool]);

  if (!pool) return <div style={{ padding: '80px', display: 'flex', justifyContent: 'center' }}><AgriLoading size={48} /></div>;

  const isHost = pool.hostEmail === user?.email;
  const isCoLoader = pool.coLoaderEmail === user?.email;

  const isCompleted = pool.status === 'DELIVERED' || pool.status.includes('CANCELLED');
  const isExpired = pool.status === 'EXPIRED' || pool.status === 'FAILED_NO_DRIVER' || pool.status === 'NO_DRIVER';
  const canCancel = !isExpired && !isCompleted && !pool.driverEmail && !pool.coLoaderEmail;

  const handleDelete = async () => {
    try {
      const res = await apiClient.post('/api/logistics/delete-pool', { orderId: poolId, email: user?.email });
      if (res.data.success) navigate(-1);
    } catch (e) {
      alert("Network Error");
    }
  };

  const handleCancel = async () => {
    try {
      const res = await apiClient.post('/api/orders/cancel_order', { orderId: poolId });
      if (res.data.success) {
        alert("Pool Cancelled & Escrow Refunded");
      } else {
        alert("Failed to cancel.");
      }
    } catch (e) {
      alert("Cannot cancel: Funds locked.");
    }
  };

  const getStatusInfo = () => {
    if (pool.status.includes('CANCELLED')) return { color: '#ef4444', text: 'Cancelled' };
    if (isExpired) return { color: '#ef4444', text: 'FAILED - NO DRIVER' };
    if (pool.status === 'DELIVERED') return { color: '#10b981', text: 'Completed' };
    if (pool.status === 'PENDING_CO_LOADER') return { color: '#f59e0b', text: 'Waiting for Co-loader' };
    if (pool.status === 'PENDING_DRIVER') return { color: '#3b82f6', text: 'Waiting for Driver' };
    if (pool.status === 'EN_ROUTE_TO_HOST_PICKUP') return { color: '#8b5cf6', text: 'Driver En Route to Host' };
    if (pool.status === 'EN_ROUTE_TO_CO_LOADER_PICKUP') return { color: '#8b5cf6', text: 'Driver En Route to Co-loader' };
    if (pool.status === 'IN_TRANSIT') return { color: '#3b82f6', text: 'In Transit' };
    return { color: '#6b7280', text: pool.status };
  };

  const statusInfo = getStatusInfo();

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', padding: '32px 24px 80px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate(-1)} style={{ background: '#fff', border: '1px solid #f0f0f0', borderRadius: '50%', width: '48px', height: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 4px 12px rgba(0,0,0,0.04)' }}>
          <ArrowLeft size={24} color="#333" />
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: '28px', fontWeight: 900, color: 'var(--terracotta-primary)' }}>Pool Details</h1>
          <p style={{ margin: '4px 0 0 0', color: '#6b7280', fontSize: '14px', fontWeight: 600 }}>ID: {pool.orderId}</p>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: '12px', alignItems: 'center' }}>
          {isExpired && (
            <button onClick={handleDelete} style={{ padding: '8px 16px', borderRadius: '12px', background: '#fee2e2', color: '#ef4444', border: '1px solid #fecaca', fontWeight: 800, fontSize: '14px', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
              <Trash2 size={16} /> Delete Record
            </button>
          )}
          {canCancel && (
            <button onClick={handleCancel} style={{ padding: '8px 16px', borderRadius: '12px', background: '#fff', color: '#ef4444', border: '1px solid #ef4444', fontWeight: 800, fontSize: '14px', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
              <X size={16} /> Cancel & Refund
            </button>
          )}
          <div style={{ padding: '8px 16px', borderRadius: '12px', background: `${statusInfo.color}15`, color: statusInfo.color, fontWeight: 800, fontSize: '15px' }}>
            {statusInfo.text}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
        
        {/* Left Column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          
          {/* Map View */}
          <div style={{ background: '#fff', borderRadius: '24px', padding: '16px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0', height: '350px', overflow: 'hidden' }}>
            <MapContainer center={[pool.pickupLat, pool.pickupLon]} zoom={12} style={{ width: '100%', height: '100%', borderRadius: '12px' }}>
              <TileLayer url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png" />
              <Marker position={[pool.pickupLat, pool.pickupLon]} icon={pickupIcon} />
              <Marker position={[pool.dropLat, pool.dropLon]} icon={dropIcon} />
              {pool.coLoaderEmail && pool.coLoaderPickupLat && (
                <>
                  <Marker position={[pool.coLoaderPickupLat, pool.coLoaderPickupLon]} icon={pickupIcon} />
                  <Marker position={[pool.coLoaderDropLat, pool.coLoaderDropLon]} icon={dropIcon} />
                </>
              )}
              {routePolyline.length > 0 && <Polyline positions={routePolyline} color="#3b82f6" weight={5} opacity={0.8} />}
            </MapContainer>
          </div>

          {/* Tracking Timeline */}
          <div style={{ background: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0' }}>
            <h3 style={{ margin: '0 0 24px 0', fontSize: '20px', fontWeight: 800, color: '#1f2937', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Navigation size={24} color="var(--terracotta-primary)" /> Live Tracking
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', position: 'relative' }}>
              <div style={{ position: 'absolute', left: '20px', top: '10px', bottom: '10px', width: '2px', background: '#f3f4f6' }} />
              
              <Step title="Waiting for Driver" active={!pool.driverEmail && pool.status !== 'EXPIRED'} completed={!!pool.driverEmail || pool.status === 'DELIVERED'} />
              <Step title="Driver Assigned" active={!!pool.driverEmail} completed={!!pool.driverEmail || pool.status === 'DELIVERED'} />
              <Step title="Host Pickup" active={pool.status === 'EN_ROUTE_TO_HOST_PICKUP' || pool.status === 'WAITING_AT_HOST_PICKUP' || pool.pickupVerified || pool.status === 'DELIVERED'} completed={pool.pickupVerified || pool.status === 'DELIVERED'} />
              {pool.coLoaderEmail && (
                <Step title="Co-loader Pickup" active={pool.status === 'EN_ROUTE_TO_CO_LOADER_PICKUP' || pool.status === 'WAITING_AT_CO_LOADER_PICKUP' || pool.coLoaderPickupVerified || pool.status === 'DELIVERED'} completed={pool.coLoaderPickupVerified || pool.status === 'DELIVERED'} />
              )}
              <Step title="Host Drop" active={pool.status === 'IN_TRANSIT' || pool.status.includes('DROP') || pool.status === 'DELIVERED'} completed={pool.dropVerified || pool.status === 'DELIVERED'} />
              {pool.coLoaderEmail && (
                <Step title="Co-loader Drop" active={pool.status.includes('DROP_B') || pool.status === 'DELIVERED'} completed={pool.coLoaderDropVerified || pool.status === 'DELIVERED'} />
              )}
              <Step title="Completed" active={pool.status === 'DELIVERED'} completed={pool.status === 'DELIVERED'} />
            </div>
          </div>

          {/* Shipment Information */}
          <div style={{ background: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0' }}>
            <h3 style={{ margin: '0 0 24px 0', fontSize: '20px', fontWeight: 800, color: '#1f2937', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Package size={24} color="var(--terracotta-primary)" /> Shipment Information
            </h3>
            
            {isHost && (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
                  <div>
                    <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Crop</span>
                    <span style={{ fontSize: '16px', color: '#111', fontWeight: 800 }}>{pool.cropName}</span>
                  </div>
                  <div>
                    <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Quantity</span>
                    <span style={{ fontSize: '16px', color: '#111', fontWeight: 800 }}>{pool.weightKg} kg</span>
                  </div>
                </div>
                <div style={{ background: '#f9fafb', padding: '20px', borderRadius: '16px' }}>
                  <div style={{ marginBottom: '16px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>
                      <MapPin size={16} /> Pickup
                    </span>
                    <span style={{ fontSize: '15px', color: '#374151', fontWeight: 600 }}>{pool.pickupAddress}</span>
                  </div>
                  <div style={{ marginBottom: '16px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>
                      <MapPin size={16} /> Drop
                    </span>
                    <span style={{ fontSize: '15px', color: '#374151', fontWeight: 600 }}>{pool.dropAddress}</span>
                  </div>
                  <div style={{ borderTop: '1px dashed #d1d5db', margin: '16px 0' }} />
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Base Distance</span>
                      <span style={{ fontSize: '15px', color: '#111', fontWeight: 800 }}>{pool.routeDistanceKm} km</span>
                    </div>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <OTPBox label="Your Pickup OTP" otp={pool.pickupOtp_A} verified={pool.pickupVerified} />
                    <OTPBox label="Your Drop OTP" otp={pool.dropOtp_A} verified={pool.dropVerified} />
                  </div>
                </div>
              </>
            )}

            {isCoLoader && (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
                  <div>
                    <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Crop</span>
                    <span style={{ fontSize: '16px', color: '#111', fontWeight: 800 }}>{pool.coLoaderCropName || 'Mixed Crops'}</span>
                  </div>
                  <div>
                    <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Quantity</span>
                    <span style={{ fontSize: '16px', color: '#111', fontWeight: 800 }}>{pool.coLoaderWeightKg} kg</span>
                  </div>
                </div>
                <div style={{ background: '#f9fafb', padding: '20px', borderRadius: '16px' }}>
                  <div style={{ marginBottom: '16px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>
                      <MapPin size={16} /> Pickup
                    </span>
                    <span style={{ fontSize: '15px', color: '#374151', fontWeight: 600 }}>{pool.coLoaderPickupAddress}</span>
                  </div>
                  <div style={{ marginBottom: '16px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>
                      <MapPin size={16} /> Drop
                    </span>
                    <span style={{ fontSize: '15px', color: '#374151', fontWeight: 600 }}>{pool.coLoaderDropAddress}</span>
                  </div>
                  <div style={{ borderTop: '1px dashed #d1d5db', margin: '16px 0' }} />
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Added Distance</span>
                      <span style={{ fontSize: '15px', color: '#111', fontWeight: 800 }}>+{pool.coLoaderAddedDistanceKm} km</span>
                    </div>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <OTPBox label="Your Pickup OTP" otp={pool.pickupOtp_B} verified={pool.coLoaderPickupVerified} />
                    <OTPBox label="Your Drop OTP" otp={pool.dropOtp_B} verified={pool.coLoaderDropVerified} />
                  </div>
                </div>
              </>
            )}

            {!isHost && !isCoLoader && (
                <div style={{ textAlign: 'center', padding: '24px 0', color: '#9ca3af', fontSize: '14px', fontWeight: 600 }}>
                  You are not authorized to view shipment contents.
                </div>
            )}
          </div>
        </div>

        {/* Right Column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          


          {/* Driver Information */}
          <div style={{ background: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0' }}>
            <h3 style={{ margin: '0 0 24px 0', fontSize: '18px', fontWeight: 800, color: '#1f2937', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Truck size={20} color="var(--terracotta-primary)" /> Driver Details
            </h3>
            {driver ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <UserAvatar src={driver.profile_image_url} alt="Driver" size={64} style={{ borderRadius: '16px' }} />
                <div>
                  <span style={{ display: 'block', fontSize: '16px', fontWeight: 800, color: '#111' }}>{driver.name}</span>
                  <span style={{ display: 'block', fontSize: '12px', color: '#3b82f6', fontWeight: 700, marginTop: '4px' }}>
                    {(() => {
                      const id = pool.driverId || driver.driverId || '';
                      if (!id) return 'GW-D0000';
                      const numMatch = String(id).match(/\d+/);
                      return numMatch ? `GW-D${numMatch[0].padStart(4, '0')}` : id;
                    })()}
                  </span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '14px', color: '#6b7280', fontWeight: 500, marginTop: '4px' }}><Phone size={14} /> {driver.phone || 'N/A'}</span>
                </div>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '24px 0', color: '#9ca3af', fontSize: '14px', fontWeight: 600 }}>
                {pool.status === 'EXPIRED' ? 'No driver available before expiry.' : 'Waiting for driver assignment...'}
              </div>
            )}
          </div>

          {/* Host Information (Visible to Co-loader only) */}
          {isCoLoader && (
            <div style={{ background: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0' }}>
              <h3 style={{ margin: '0 0 24px 0', fontSize: '18px', fontWeight: 800, color: '#1f2937', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <User size={20} color="var(--terracotta-primary)" /> Host Farmer
              </h3>
              {hostProfile ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <UserAvatar src={hostProfile.profile_image_url} alt="Host" size={64} style={{ borderRadius: '16px' }} />
                  <div>
                    <span style={{ display: 'block', fontSize: '16px', fontWeight: 800, color: '#111' }}>{(hostProfile.name && hostProfile.name !== 'Farmer') ? hostProfile.name : 'Unknown Farmer'}</span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '14px', color: '#6b7280', fontWeight: 500, marginTop: '4px' }}><Phone size={14} /> {hostProfile.phone || 'N/A'}</span>
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '24px 0', color: '#9ca3af', fontSize: '14px', fontWeight: 600 }}>
                  Loading host details...
                </div>
              )}
            </div>
          )}

          {/* Co-loader Information (Visible to Host only) */}
          {isHost && (
            <div style={{ background: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0' }}>
              <h3 style={{ margin: '0 0 24px 0', fontSize: '18px', fontWeight: 800, color: '#1f2937', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <User size={20} color="var(--terracotta-primary)" /> Co-loader Details
              </h3>
              {coLoader ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <UserAvatar src={coLoader.profile_image_url} alt="Co-loader" size={64} style={{ borderRadius: '16px' }} />
                    <div>
                      <span style={{ display: 'block', fontSize: '16px', fontWeight: 800, color: '#111' }}>{coLoader.name}</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '14px', color: '#6b7280', fontWeight: 500, marginTop: '4px' }}><Phone size={14} /> {coLoader.phone || 'N/A'}</span>
                    </div>
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '24px 0', color: '#9ca3af', fontSize: '14px', fontWeight: 600 }}>
                  {pool.status === 'EXPIRED' ? 'No co-loader joined.' : 'Waiting for a co-loader...'}
                </div>
              )}
            </div>
          )}

          {/* Escrow & Payment Status */}
          {pool.status === 'DELIVERED' ? (
            <div style={{ background: '#ecfdf5', borderRadius: '24px', padding: '32px', border: '1px solid #10b981' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
                <CheckCircle size={28} color="#059669" />
                <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 900, color: '#065f46' }}>Paid</h3>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <span style={{ color: '#064e3b', fontSize: '15px', fontWeight: 600 }}>Total Trip Cost</span>
                <span style={{ color: '#047857', fontSize: '16px', fontWeight: 800 }}>{formatCurrency(((pool.totalPayment || 0) + (pool.coLoaderPayment || 0)).toFixed(2))}</span>
              </div>
              {isHost && (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <span style={{ color: '#064e3b', fontSize: '15px', fontWeight: 600 }}>Your Payment</span>
                  <span style={{ color: '#047857', fontSize: '18px', fontWeight: 900 }}>{formatCurrency((pool.totalPayment || 0).toFixed(2))}</span>
                </div>
              )}
              {isCoLoader && (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <span style={{ color: '#064e3b', fontSize: '15px', fontWeight: 600 }}>Your Payment</span>
                  <span style={{ color: '#047857', fontSize: '18px', fontWeight: 900 }}>{formatCurrency((pool.coLoaderPayment || 0).toFixed(2))}</span>
                </div>
              )}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid rgba(16, 185, 129, 0.2)', paddingTop: '16px' }}>
                <span style={{ color: '#064e3b', fontSize: '15px', fontWeight: 700 }}>Payment Released</span>
                <span style={{ color: '#047857', fontSize: '16px', fontWeight: 900 }}>✓ Success</span>
              </div>
            </div>
          ) : (
            <div style={{ background: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f0f0f0' }}>
              <h3 style={{ margin: '0 0 24px 0', fontSize: '18px', fontWeight: 800, color: '#1f2937' }}>Escrow Status</h3>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <span style={{ color: '#6b7280', fontSize: '14px', fontWeight: 600 }}>Total Pool Value</span>
                <span style={{ color: '#111', fontSize: '16px', fontWeight: 800 }}>{formatCurrency(((pool.totalPayment || 0) + (pool.coLoaderPayment || 0)).toFixed(2))}</span>
              </div>
              {isHost && (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#6b7280', fontSize: '14px', fontWeight: 600 }}>Your Payment</span>
                  <span style={{ color: '#10b981', fontSize: '18px', fontWeight: 900 }}>{formatCurrency((pool.totalPayment || 0).toFixed(2))}</span>
                </div>
              )}
              {isCoLoader && (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#6b7280', fontSize: '14px', fontWeight: 600 }}>Your Payment</span>
                  <span style={{ color: '#10b981', fontSize: '18px', fontWeight: 900 }}>{formatCurrency((pool.coLoaderPayment || 0).toFixed(2))}</span>
                </div>
              )}
              {isExpired ? (
                <div style={{ marginTop: '24px', padding: '16px', background: '#fee2e2', border: '1px solid #fecaca', borderRadius: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: '#991b1b', fontWeight: 600, fontSize: '14px' }}>Refund Status</span>
                    <span style={{ color: '#ef4444', fontWeight: 900, fontSize: '16px' }}>Refunded</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: '#991b1b', fontWeight: 600, fontSize: '14px' }}>Reason</span>
                    <span style={{ color: '#b91c1c', fontWeight: 700, fontSize: '14px' }}>No Driver Available</span>
                  </div>
                </div>
              ) : (
                <div style={{ marginTop: '24px', padding: '12px', background: '#fef3c7', borderRadius: '12px', textAlign: 'center', color: '#d97706', fontWeight: 700, fontSize: '14px' }}>
                  Funds Locked in Escrow
                </div>
              )}
            </div>
          )}

        </div>
      </div>
    </div>
  );
};

const Step = ({ title, active, completed }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', zIndex: 2 }}>
    <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: completed ? '#10b981' : (active ? 'var(--terracotta-primary)' : '#fff'), border: completed ? 'none' : (active ? 'none' : '2px solid #e5e7eb'), display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      {completed ? <CheckCircle size={16} color="#fff" /> : <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: active ? '#fff' : '#e5e7eb' }} />}
    </div>
    <span style={{ fontSize: '14px', fontWeight: active || completed ? 800 : 600, color: active || completed ? '#111' : '#9ca3af' }}>{title}</span>
  </div>
);

const OTPBox = ({ label, otp, verified }) => (
  <div style={{ background: verified ? '#f0fdf4' : '#f9fafb', border: verified ? '1px solid #bbf7d0' : '1px solid #f0f0f0', borderRadius: '16px', padding: '16px', marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
    <div>
      <span style={{ display: 'block', fontSize: '11px', color: verified ? '#166534' : '#6b7280', fontWeight: 800, textTransform: 'uppercase', marginBottom: '4px' }}>{label}</span>
      <span style={{ fontSize: '20px', fontWeight: 900, color: verified ? '#15803d' : '#111', letterSpacing: '2px' }}>{verified ? '✓✓✓✓' : otp || '----'}</span>
    </div>
    {verified && <CheckCircle size={24} color="#22c55e" />}
  </div>
);

export default PoolDetails;
