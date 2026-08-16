import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { collection, query, where, onSnapshot, doc, updateDoc, arrayUnion, getDoc } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { getDistance } from '../../utils/geoUtils';
import DriverLoadCard from '../../components/driver/DriverLoadCard';
import DriverActiveTripCard from '../../components/driver/DriverActiveTripCard';
import AgriLoading from '../../components/common/AgriLoading';
import { Settings, RefreshCw, Car } from 'lucide-react';

import { ACTIVE_DRIVER_STATUSES } from '../../utils/constants';

const DriverHome = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeOrder, setActiveOrder] = useState(null);
  const [availableLoads, setAvailableLoads] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isDevMode, setIsDevMode] = useState(false); // Developer toggle
  const [location, setLocation] = useState({ lat: 0, lon: 0 });
  const [driverProfile, setDriverProfile] = useState({ username: 'Driver', vehicleType: 'Any' });

  // 1. Fetch Driver Profile
  useEffect(() => {
    const fetchProfile = async () => {
      if (!user?.email) return;
      try {
        const userDoc = await getDoc(doc(db, 'users', user.email));
        if (userDoc.exists()) {
          const data = userDoc.data();
          setDriverProfile({
            username: data.username || data.name || 'Driver',
            vehicleType: data.vehicleType || 'Any',
            phone: data.phone || '',
            driverId: data.driverId || `GW-D${user.uid?.substring(0, 4) || '0000'}`,
            profileImageUrl: data.profileImageUrl || ''
          });
        }
      } catch (error) {
        console.error("Failed to fetch driver profile:", error);
      }
    };
    fetchProfile();
  }, [user?.email]);

  // 2. Get Current Location
  const refreshLocation = useCallback(() => {
    return new Promise((resolve, reject) => {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            const newLoc = { lat: position.coords.latitude, lon: position.coords.longitude };
            setLocation(newLoc);
            resolve(newLoc);
          },
          (error) => {
            console.warn("Location access denied or failed", error);
            resolve(location); // fallback to old location
          },
          { enableHighAccuracy: true }
        );
      } else {
        resolve(location);
      }
    });
  }, [location]);

  useEffect(() => {
    refreshLocation();
  }, [refreshLocation]);



  // 2. Listen for Active Trips
  useEffect(() => {
    if (!user?.email) return;

    const activeStatuses = ACTIVE_DRIVER_STATUSES;

    const q = query(
      collection(db, 'orders'),
      where('driverEmail', '==', user.email)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      let foundActive = null;
      snapshot.docs.forEach(doc => {
        const data = doc.data();
        if (activeStatuses.includes(data.status)) {
          foundActive = data;
        }
      });
      setActiveOrder(foundActive);
    });

    return () => unsubscribe();
  }, [user]);

  // 3. Fetch Available Loads via API
  const fetchLoads = useCallback(async (currentLoc = location) => {
    if (!user?.email) return;
    
    try {
      const res = await apiClient.get('/api/logistics/available-loads', {
        params: { lat: currentLoc.lat, lon: currentLoc.lon, email: user.email }
      });
      
      if (res.data.success) {
        const loadsWithDistance = res.data.loads.map(load => {
          const dist = getDistance(currentLoc.lat, currentLoc.lon, load.pickupLat || 0, load.pickupLon || 0) / 1000;
          return { ...load, distanceToPickup: dist };
        });
        setAvailableLoads(loadsWithDistance);
      }
    } catch (error) {
      console.error("Failed to fetch loads:", error);
      alert("Failed to fetch available loads");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [user?.email, location]);

  const handleFullRefresh = async () => {
    setIsLoading(true); // Show AgriLoading
    setIsRefreshing(true);
    try {
      const freshLoc = await refreshLocation(); // Request fresh GPS and wait
      await fetchLoads(freshLoc); // Call API, backend normalizes, renders orders, hides loading
    } catch (e) {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchLoads();
    const interval = setInterval(() => fetchLoads(), 30000);
    return () => clearInterval(interval);
  }, [fetchLoads]);

  // Actions
  const handleAccept = async (order) => {
    if (activeOrder) {
      alert("You already have an active trip!");
      return;
    }
    
    try {
      const payload = {
        orderId: order.orderId,
        email: user.email,
        lat: location.lat,
        lon: location.lon
      };
      
      const res = await apiClient.post('/api/logistics/accept-order', payload);
      
      if (res.data.success) {
        if (order.orderId.startsWith('GW-') || order.orderId.startsWith('DEAL-DON-')) {
          navigate('/driver/standard-delivery');
        } else {
          navigate('/driver/status');
        }
      } else {
        throw new Error(res.data.error || "Failed to accept.");
      }
    } catch (error) {
      console.error("Failed to accept:", error);
      alert(error.response?.data?.error || error.message || "Failed to accept order. It may have been taken.");
    }
  };

  const handleDecline = async (order) => {
    // Optimistic UI update
    setAvailableLoads(prev => prev.filter(l => l.orderId !== order.orderId));
    try {
      await updateDoc(doc(db, 'orders', order.orderId), {
        declinedBy: arrayUnion(user.email)
      });
    } catch (error) {
      console.error("Failed to decline:", error);
      alert("Failed to update preferences");
      fetchLoads(); // Revert on failure
    }
  };

  const handleCancelActiveOrder = async (order) => {
    try {
      // Revert status to PENDING_DRIVER (or PENDING_CO_LOADER if no coloader?)
      // Wait, if we just set status to PENDING_DRIVER it restores availability
      await updateDoc(doc(db, 'orders', order.orderId), {
        driverEmail: null,
        driverName: null,
        driverPhone: null,
        driverId: null,
        driverProfilePic: null,
        status: 'PENDING_DRIVER'
      });
      alert("Active order cancelled (Dev Mode)");
      setActiveOrder(null);
      fetchLoads();
    } catch (error) {
      console.error("Failed to decline active order:", error);
      alert("Failed to decline active order");
    }
  };

  if (isLoading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', height: '60vh', gap: '16px' }}>
        <AgriLoading size={56} />
        <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--terracotta-primary)' }}>Loading Loads...</span>
      </div>
    );
  }

  return (
    <div style={{ paddingBottom: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 900, color: '#1a1a1a' }}>
            Good Morning, {driverProfile.username}
          </h1>
          <p style={{ margin: '4px 0 0', fontSize: '14px', color: '#666', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Car size={16} /> Registered Vehicle: <strong style={{ color: 'var(--terracotta-primary)' }}>{driverProfile.vehicleType}</strong>
          </p>
        </div>
        <div style={{ display: 'flex', gap: '12px' }}>
          <button 
            onClick={() => setIsDevMode(!isDevMode)}
            style={{
              padding: '10px', borderRadius: '12px',
              backgroundColor: isDevMode ? '#ef4444' : '#f3f4f6',
              color: isDevMode ? '#fff' : '#6b7280',
              border: 'none', cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: isDevMode ? '0 4px 12px rgba(239, 68, 68, 0.2)' : 'none'
            }}
            title="Toggle Developer Mode"
          >
            <Settings size={20} />
          </button>
          <button 
            onClick={handleFullRefresh}
            disabled={isRefreshing}
            style={{
              padding: '10px', borderRadius: '12px',
              backgroundColor: 'var(--peach-background)',
              color: 'var(--terracotta-primary)',
              border: 'none', cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              opacity: isRefreshing ? 0.6 : 1,
              transition: 'transform 0.2s ease',
              transform: isRefreshing ? 'scale(0.95)' : 'scale(1)'
            }}
          >
            {isRefreshing ? <AgriLoading size={20} /> : <RefreshCw size={20} />}
          </button>
        </div>
      </div>

      {/* Main Content Area */}
      {activeOrder && !isDevMode ? (
        // PRODUCTION: If active order, strictly show ActiveTripCard and NOTHING ELSE.
        <DriverActiveTripCard 
          activeOrder={activeOrder} 
          onCancelActiveOrder={handleCancelActiveOrder}
          isDevMode={isDevMode}
        />
      ) : activeOrder && isDevMode ? (
        // DEV MODE: Show ActiveTripCard AND Load Board
        <>
        <DriverActiveTripCard 
          activeOrder={activeOrder} 
          onCancelActiveOrder={handleCancelActiveOrder}
          isDevMode={isDevMode}
        />
          <div style={{ margin: '32px 0 16px', display: 'flex', alignItems: 'center' }}>
            <div style={{ flex: 1, height: '1px', backgroundColor: '#e5e7eb' }} />
            <span style={{ padding: '0 16px', fontSize: '12px', fontWeight: 800, color: '#9ca3af', textTransform: 'uppercase', letterSpacing: '1px' }}>
              Load Board (Dev Mode)
            </span>
            <div style={{ flex: 1, height: '1px', backgroundColor: '#e5e7eb' }} />
          </div>
          {availableLoads.length === 0 ? (
             <div style={{ textAlign: 'center', padding: '40px 0', color: '#9ca3af' }}>
              <p style={{ fontWeight: 600 }}>No available loads found.</p>
            </div>
          ) : (
            availableLoads.map(order => (
              <DriverLoadCard 
                key={order.orderId}
                order={order}
                onAccept={handleAccept}
                onDecline={handleDecline}
              />
            ))
          )}
        </>
      ) : (
        // NO ACTIVE ORDER: Show Load Board
        <>
          <h2 style={{ fontSize: '18px', fontWeight: 800, color: '#1a1a1a', marginBottom: '16px' }}>
            Available Loads
          </h2>
          {availableLoads.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#9ca3af' }}>
              <div style={{ width: '64px', height: '64px', backgroundColor: '#f3f4f6', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <Car size={32} color="#9ca3af" />
              </div>
              <p style={{ fontWeight: 600, fontSize: '16px', color: '#374151' }}>Looking for loads...</p>
              <p style={{ fontSize: '14px', marginTop: '4px' }}>New orders will appear here automatically.</p>
            </div>
          ) : (
            availableLoads.map(order => (
              <DriverLoadCard 
                key={order.orderId}
                order={order}
                onAccept={handleAccept}
                onDecline={handleDecline}
              />
            ))
          )}
        </>
      )}
    </div>
  );
};

export default DriverHome;
