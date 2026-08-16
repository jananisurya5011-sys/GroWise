import React, { useState, useEffect } from 'react';
import { 
  Truck, Clock, MapPin, CheckCircle, 
  X, AlertTriangle, ArrowRight, ArrowLeft, Calendar, Info, Search, FileText, User, Package
} from 'lucide-react';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { motion, AnimatePresence } from 'framer-motion';
import AgriLoading from './AgriLoading';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { TimePicker } from '@mui/x-date-pickers/TimePicker';
import dayjs from 'dayjs';
import { doc, getDoc } from 'firebase/firestore';
import { db } from '../../utils/firebase';

// --- UI Components moved outside to prevent remounting and focus loss ---
const SegmentedControl = ({ value, onChange, onAutoClick }) => (
  <div style={{ display: 'flex', background: '#f3f4f6', borderRadius: '12px', padding: '4px', width: '200px' }}>
    <div 
      onClick={() => { onChange('auto'); if (onAutoClick) onAutoClick(); }} 
      style={{ flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: '8px', cursor: 'pointer', fontSize: '14px', fontWeight: 600, background: value === 'auto' ? '#fff' : 'transparent', color: value === 'auto' ? 'var(--terracotta-primary)' : '#6b7280', boxShadow: value === 'auto' ? '0 2px 4px rgba(0,0,0,0.05)' : 'none', transition: 'all 0.2s' }}
    >
      Auto
    </div>
    <div 
      onClick={() => onChange('manual')} 
      style={{ flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: '8px', cursor: 'pointer', fontSize: '14px', fontWeight: 600, background: value === 'manual' ? '#fff' : 'transparent', color: value === 'manual' ? 'var(--terracotta-primary)' : '#6b7280', boxShadow: value === 'manual' ? '0 2px 4px rgba(0,0,0,0.05)' : 'none', transition: 'all 0.2s' }}
    >
      Manual
    </div>
  </div>
);

const InputField = ({ label, icon: Icon, ...props }) => (
  <div style={{ marginBottom: '16px', width: '100%' }}>
    {label && <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#374151' }}>{label}</label>}
    <div style={{ position: 'relative' }}>
      {Icon && <Icon size={18} color="#9ca3af" style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)' }} />}
      <input 
        style={{ 
          width: '100%', padding: Icon ? '14px 14px 14px 44px' : '14px', 
          borderRadius: '12px', border: '1px solid #e5e7eb', background: '#f9fafb',
          fontSize: '15px', color: '#111', transition: 'border-color 0.2s, box-shadow 0.2s',
          outline: 'none'
        }}
        onFocus={(e) => { e.target.style.borderColor = 'var(--terracotta-primary)'; e.target.style.boxShadow = '0 0 0 3px rgba(225, 90, 70, 0.1)'; }}
        onBlur={(e) => { e.target.style.borderColor = '#e5e7eb'; e.target.style.boxShadow = 'none'; }}
        {...props} 
      />
    </div>
  </div>
);

const SectionCard = ({ title, icon: Icon, children }) => (
  <div style={{ background: '#fff', borderRadius: '16px', padding: '24px', border: '1px solid #f0f0f0', boxShadow: '0 4px 12px rgba(0,0,0,0.02)', marginBottom: '24px' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
      <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Icon size={20} color="var(--terracotta-primary)" />
      </div>
      <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700, color: '#1f2937' }}>{title}</h3>
    </div>
    {children}
  </div>
);

// --- Payment Overlay (Matches Deals.jsx FullScreenPayment) ---
import { CreditCard } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { formatCurrency } from '../../utils/constants';

const CropPoolPaymentOverlay = ({ step, totalPrice, walletBal, onCancel, onComplete, onAddMoney }) => {
  const navigate = useNavigate();
  
  if (step === 2) {
    return (
      <div style={{ position: 'fixed', inset: 0, zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.6)', padding: 20 }}>
        <div style={{ background: 'white', borderRadius: 24, padding: 32, maxWidth: 400, width: '100%', textAlign: 'center', boxShadow: '0 20px 40px rgba(0,0,0,0.2)' }}>
          <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#FFF3E0', margin: '0 auto 20px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <CreditCard size={40} color="#E65100" />
          </div>
          <h2 style={{ margin: '0 0 12px', fontSize: 24, fontWeight: 900, color: '#E65100' }}>Insufficient Wallet Balance</h2>
          <p style={{ margin: '0 0 24px', fontSize: 15, color: '#555', lineHeight: 1.5 }}>
            Your wallet balance is lower than the required escrow total.
          </p>
          <div style={{ background: '#FAFAFA', padding: 16, borderRadius: 16, marginBottom: 24, border: '1px solid #EEE' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 14 }}>
              <span style={{ color: '#666' }}>Required Amount</span>
              <span style={{ fontWeight: 800, color: '#333' }}>{formatCurrency((totalPrice || 0).toFixed(2))}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 14 }}>
              <span style={{ color: '#666' }}>Available Balance</span>
              <span style={{ fontWeight: 800, color: '#333' }}>{formatCurrency((walletBal || 0).toFixed(2))}</span>
            </div>
            <div style={{ borderTop: '1px dashed #CCC', margin: '8px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
              <span style={{ color: '#E65100', fontWeight: 700 }}>Additional Amount Needed</span>
              <span style={{ fontWeight: 900, color: '#E65100' }}>{formatCurrency(Math.max(0, (totalPrice || 0) - (walletBal || 0)).toFixed(2))}</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button onClick={onCancel} style={{ flex: 1, padding: 14, background: '#F5F5F5', color: '#555', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 15, cursor: 'pointer' }}>Cancel</button>
            <button onClick={() => { if (onAddMoney) onAddMoney(); if (onCancel) onCancel(); navigate('/track?tab=wallet&action=topup'); }} style={{ flex: 1, padding: 14, background: '#E65100', color: 'white', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 15, cursor: 'pointer', boxShadow: '0 4px 12px rgba(230,81,0,0.3)' }}>Add Money</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', background: step === 0 ? 'rgba(255,255,255,0.97)' : 'rgba(46,125,50,0.97)', transition: 'background 0.5s' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
        {step === 0 ? (
          <>
            <div style={{ width: 88, height: 88, borderRadius: '50%', border: `3px solid var(--terracotta-primary)`, background: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center', animation: 'spin 2s linear infinite' }}>
              <div style={{ width: 60, height: 60, borderRadius: '50%', borderTop: `3px solid var(--terracotta-primary)`, borderRight: '3px solid transparent', borderBottom: `3px solid var(--terracotta-primary)`, borderLeft: '3px solid transparent' }} />
            </div>
            <p style={{ fontWeight: 800, fontSize: 18, color: 'var(--terracotta-primary)' }}>Securing transaction...</p>
            <p style={{ fontWeight: 900, fontSize: 28, color: '#2E7D32' }}>{formatCurrency((totalPrice || 0).toFixed(2))}</p>
          </>
        ) : (
          <>
            <div style={{ width: 88, height: 88, borderRadius: '50%', background: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <CheckCircle size={56} color="#2E7D32" />
            </div>
            <p style={{ fontWeight: 800, fontSize: 22, color: 'white' }}>Payment Successful!</p>
            <p style={{ color: 'rgba(255,255,255,0.8)', fontSize: 14 }}>Escrow Locked.</p>
          </>
        )}
      </div>
      <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
    </div>
  );
};

const CropPoolCard = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState(0);

  // --- Create Pool State ---
  const [cropName, setCropName] = useState('');
  const [weightToPool, setWeightToPool] = useState('');
  
  const [pickupMode, setPickupMode] = useState('manual');
  const [pickupAddress, setPickupAddress] = useState('');
  const [pickupLat, setPickupLat] = useState('');
  const [pickupLon, setPickupLon] = useState('');
  
  const [dropMode, setDropMode] = useState('manual');
  const [dropAddress, setDropAddress] = useState('');
  const [dropLat, setDropLat] = useState('');
  const [dropLon, setDropLon] = useState('');
  
  const [dispatchDate, setDispatchDate] = useState('');
  const [dispatchTime, setDispatchTime] = useState('');
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [showPrivacyPolicy, setShowPrivacyPolicy] = useState(false);
  const [hasReadPrivacyPolicy, setHasReadPrivacyPolicy] = useState(false);

  const [showDisclaimerDialog, setShowDisclaimerDialog] = useState(false);
  const [showJoinDisclaimerDialog, setShowJoinDisclaimerDialog] = useState(false);
  const [paymentOverlayStep, setPaymentOverlayStep] = useState(null);
  const [currentWalletBal, setCurrentWalletBal] = useState(0);
  const [typedConsent, setTypedConsent] = useState('');

  // Auto-retry Join Pool on Return from Wallet
  useEffect(() => {
    if (user?.email) {
      const pendingStr = localStorage.getItem('pendingPoolRequest');
      if (pendingStr) {
        localStorage.removeItem('pendingPoolRequest');
        const pendingReq = JSON.parse(pendingStr);
        if (pendingReq.type === 'JOIN_POOL') {
          retryJoinPool(pendingReq);
        }
      }
    }
  }, [user]);

  const retryJoinPool = async (req) => {
    setPaymentOverlayStep(0);
    try {
      const quoteRes = await apiClient.post('/api/logistics/quote-join-pool', req);
      if (!quoteRes.data.success) {
        setPaymentOverlayStep(null);
        setErrorMessage("Failed to recalculate quote.");
        return;
      }
      const freshQuote = quoteRes.data;
      setJoinQuoteData(freshQuote);

      const walletDoc = await getDoc(doc(db, 'wallets', user?.email));
      const bal = walletDoc.exists() ? (walletDoc.data().balance || 0) : 0;
      setCurrentWalletBal(bal);
      
      if (bal < freshQuote.totalShareB) {
        setPaymentOverlayStep(2);
        return;
      }

      req.paymentAmount = freshQuote.totalShareB;
      
      console.log("----- FRONTEND JOIN POOL LOG -----");
      console.log(`Wallet Balance: ${bal}`);
      console.log(`Quote Amount: ${freshQuote.totalShareB}`);
      console.log(`Pool ID: ${req.poolId}`);
      console.log(`User ID: ${user?.email}`);
      console.log(`Current Wallet Version: Latest from Firestore`);
      console.log(`Current Timestamp: ${new Date().toISOString()}`);
      
      const res = await apiClient.post('/api/logistics/join-pool', req);
      
      console.log(`HTTP Status: ${res.status}`);
      console.log(`Backend Error: null`);
      console.log(`Response Body:`, res.data);

      if (res.data.success) {
        setPaymentOverlayStep(1);
        setTimeout(() => {
          setPaymentOverlayStep(null);
          setShowJoinInvoice(false);
          setSelectedPoolToJoin(null);
          clearJoinForm();
          fetchPools(); 
        }, 2000);
      } else {
        if (res.data.error === "INSUFFICIENT_BALANCE") {
          setPaymentOverlayStep(2);
        } else {
          setPaymentOverlayStep(null);
          setErrorMessage(res.data.message || res.data.error || "Failed to join pool.");
        }
      }
    } catch (e) {
      console.log("Status:", e.response?.status);
      console.log("Response:", e.response?.data);
      const backendError = e.response?.data?.error || e.message;
      if (backendError === "INSUFFICIENT_BALANCE") {
        setPaymentOverlayStep(2);
      } else {
        setPaymentOverlayStep(null);
        setErrorMessage(e.response?.data?.message || backendError || "Join Pool Failed");
      }
    }
  };

  const [quoteData, setQuoteData] = useState(null);

  const [isProcessing, setIsProcessing] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const [availablePools, setAvailablePools] = useState([]);
  const [isLoadingPools, setIsLoadingPools] = useState(true);
  const [selectedPoolToJoin, setSelectedPoolToJoin] = useState(null);

  const [joinCropName, setJoinCropName] = useState('');
  const [joinWeight, setJoinWeight] = useState('');
  const [joinPickupMode, setJoinPickupMode] = useState('manual');
  const [joinPickupAddress, setJoinPickupAddress] = useState('');
  const [joinPickupLat, setJoinPickupLat] = useState('');
  const [joinPickupLon, setJoinPickupLon] = useState('');
  const [joinDropMode, setJoinDropMode] = useState('manual');
  const [joinDropAddress, setJoinDropAddress] = useState('');
  const [joinDropLat, setJoinDropLat] = useState('');
  const [joinDropLon, setJoinDropLon] = useState('');

  const [joinQuoteData, setJoinQuoteData] = useState(null);
  const [isQuotingJoin, setIsQuotingJoin] = useState(false);
  const [showJoinInvoice, setShowJoinInvoice] = useState(false);

  const clearCreateForm = () => {
    setCropName('');
    setWeightToPool('');
    setPickupMode('manual');
    setPickupAddress('');
    setPickupLat('');
    setPickupLon('');
    setDropMode('manual');
    setDropAddress('');
    setDropLat('');
    setDropLon('');
    setDispatchDate('');
    setDispatchTime('');
    setTermsAccepted(false);
    setHasReadPrivacyPolicy(false);
    setTypedConsent('');
    setQuoteData(null);
  };

  const clearJoinForm = () => {
    setJoinCropName('');
    setJoinWeight('');
    setJoinPickupMode('manual');
    setJoinPickupAddress('');
    setJoinPickupLat('');
    setJoinPickupLon('');
    setJoinDropMode('manual');
    setJoinDropAddress('');
    setJoinDropLat('');
    setJoinDropLon('');
    setJoinQuoteData(null);
  };

  const fetchPools = async () => {
    setIsLoadingPools(true);
    
    let userLat = 0.0, userLon = 0.0;
    
    if (user?.email) {
      try {
        const profileRes = await apiClient.post('/api/profile/fetch-details', { email: user.email });
        if (profileRes.data) {
          userLat = profileRes.data.homeLat || profileRes.data.farmLat || 0.0;
          userLon = profileRes.data.homeLon || profileRes.data.farmLon || 0.0;
        }
      } catch (e) {
        console.warn("Failed to fetch profile for location, using defaults.", e);
      }
    }

    try {
      const res = await apiClient.get('/api/logistics/available-pools', {
        params: { lat: userLat, lon: userLon, email: user?.email }
      });
      if (res.data.success) {
        setAvailablePools(res.data.pools || []);
      }
    } catch (e) {
      console.error("Failed to fetch pools:", e);
    } finally {
      setIsLoadingPools(false);
    }
  };

  useEffect(() => {
    if (activeTab === 1) {
      fetchPools();
    }
  }, [activeTab, user?.email]);

  const handleAutoFetchLocation = (type) => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const lat = position.coords.latitude;
          const lon = position.coords.longitude;
          if (type === 'pickup') { setPickupLat(lat); setPickupLon(lon); }
          if (type === 'drop') { setDropLat(lat); setDropLon(lon); }
          if (type === 'joinPickup') { setJoinPickupLat(lat); setJoinPickupLon(lon); }
          if (type === 'joinDrop') { setJoinDropLat(lat); setJoinDropLon(lon); }
          
          try {
            const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`);
            const data = await res.json();
            const addr = data?.display_name || `Lat: ${lat.toFixed(4)}, Lon: ${lon.toFixed(4)}`;
            if (type === 'pickup') setPickupAddress(addr);
            if (type === 'drop') setDropAddress(addr);
            if (type === 'joinPickup') setJoinPickupAddress(addr);
            if (type === 'joinDrop') setJoinDropAddress(addr);
          } catch (e) {
            const addr = `Lat: ${lat.toFixed(4)}, Lon: ${lon.toFixed(4)}`;
            if (type === 'pickup') setPickupAddress(addr);
            if (type === 'drop') setDropAddress(addr);
            if (type === 'joinPickup') setJoinPickupAddress(addr);
            if (type === 'joinDrop') setJoinDropAddress(addr);
          }
        },
        () => alert("Location permission denied.")
      );
    }
  };

  const hasValidGps = pickupLat !== '' && pickupLon !== '' && dropLat !== '' && dropLon !== '' && 
                      parseFloat(pickupLat) !== 0 && parseFloat(pickupLon) !== 0 && parseFloat(dropLat) !== 0 && parseFloat(dropLon) !== 0 &&
                      !isNaN(parseFloat(pickupLat)) && !isNaN(parseFloat(pickupLon)) && !isNaN(parseFloat(dropLat)) && !isNaN(parseFloat(dropLon));

  const isFormComplete = cropName && weightToPool && pickupAddress && dropAddress && dispatchDate && dispatchTime && termsAccepted && hasValidGps;
  const handleQuotePool = async () => {
    setIsProcessing(true);
    try {
      const req = {
        weightKg: parseFloat(weightToPool) || 0,
        pickupLat: parseFloat(pickupLat) || 0,
        pickupLon: parseFloat(pickupLon) || 0,
        dropLat: parseFloat(dropLat) || 0,
        dropLon: parseFloat(dropLon) || 0
      };
      const res = await apiClient.post('/api/logistics/quote-pool', req);
      if (res.data.success) {
        setQuoteData(res.data);
        setShowDisclaimerDialog(true);
      } else {
        alert("Failed to calculate secure fare.");
      }
    } catch (e) {
      alert("Network Error");
    } finally {
      setIsProcessing(false);
    }
  };

  const handleCreatePool = async () => {
    setErrorMessage('');
    
    try {
      const walletDoc = await getDoc(doc(db, 'wallets', user?.email));
      const bal = walletDoc.exists() ? (walletDoc.data().balance || 0) : 0;
      setCurrentWalletBal(bal);
      
      if (bal < quoteData.totalAmount) {
        setPaymentOverlayStep(2);
        return;
      }
    } catch (e) {
      console.warn("Could not pre-fetch balance", e);
    }

    setPaymentOverlayStep(0);

    try {
      const dateObj = new Date(`${dispatchDate}T${dispatchTime}`);
      const dispatchTimeMillis = dateObj.getTime();

      const req = {
        farmerEmail: user?.email,
        cropName,
        weightKg: parseFloat(weightToPool) || 0,
        pickupAddress,
        dropAddress,
        pickupLat: parseFloat(pickupLat) || 0,
        pickupLon: parseFloat(pickupLon) || 0,
        dropLat: parseFloat(dropLat) || 0,
        dropLon: parseFloat(dropLon) || 0,
        dispatchTime: dispatchTimeMillis,
        vehicleType: quoteData.vehicleType,
        distanceKm: quoteData.distanceKm,
        totalAmount: quoteData.totalAmount
      };
      
      const res = await apiClient.post('/api/logistics/create-pool', req);
      
      if (res.data.success) {
        setPaymentOverlayStep(1);
        setTimeout(() => {
          setPaymentOverlayStep(null);
          setShowDisclaimerDialog(false);
          clearCreateForm();
          fetchPools(); // Reload Review tab
          setActiveTab(1); 
        }, 2000);
      } else {
        if (res.data.error === "Insufficient wallet balance.") {
          setPaymentOverlayStep(2);
        } else {
          setPaymentOverlayStep(null);
          setErrorMessage(res.data.error || "Failed to create pool.");
        }
      }
    } catch (e) {
      const backendError = e.response?.data?.error || e.message;
      if (
        backendError === "Insufficient balance." || 
        backendError === "Insufficient wallet balance." || 
        backendError === "Insufficient wallet balance for Escrow lock."
      ) {
        setPaymentOverlayStep(2);
      } else {
        setPaymentOverlayStep(null);
        setErrorMessage(backendError || "Network Error");
      }
    }
  };

  const handleDeletePool = async (orderId) => {
    try {
      const res = await apiClient.post('/api/logistics/delete-pool', { orderId, email: user?.email });
      if (res.data.success) fetchPools();
    } catch (e) {
      alert("Network Error");
    }
  };

  const handleCancelOrder = async (orderId) => {
    try {
      const res = await apiClient.post('/api/orders/cancel_order', { orderId });
      if (res.data.success) {
        alert("Order Cancelled. Escrow Refunded.");
        fetchPools();
      } else {
        alert("Failed to cancel. Funds might be locked.");
      }
    } catch (e) {
      if (e.response?.status === 400) {
        alert("Cannot Cancel: Funds locked in Escrow.");
      } else {
        alert("Network Error");
      }
    }
  };

  const isJoinFormComplete = joinCropName && joinWeight && joinPickupAddress && joinDropAddress;

  const handleQuoteJoin = async () => {
    setIsQuotingJoin(true);
    try {
      const req = {
        poolId: selectedPoolToJoin.orderId,
        farmerEmail: user?.email,
        weightKg: parseFloat(joinWeight) || 0,
        cropName: joinCropName,
        pickupLat: parseFloat(joinPickupLat) || 0,
        pickupLon: parseFloat(joinPickupLon) || 0,
        dropLat: parseFloat(joinDropLat) || 0,
        dropLon: parseFloat(joinDropLon) || 0
      };
      const res = await apiClient.post('/api/logistics/quote-join-pool', req);
      if (res.data.success) {
        setJoinQuoteData(res.data);
        setShowJoinInvoice(true);
      } else {
        alert("Failed to calculate dynamic detour.");
      }
    } catch (e) {
      alert("Network Error");
    } finally {
      setIsQuotingJoin(false);
    }
  };

  const handleJoinPool = async () => {
    if (!selectedPoolToJoin) return;
    setErrorMessage('');
    
    try {
      // Always fetch latest wallet directly before quoting
      const walletDoc = await getDoc(doc(db, 'wallets', user?.email));
      const bal = walletDoc.exists() ? (walletDoc.data().balance || 0) : 0;
      setCurrentWalletBal(bal);
      
      if (bal < joinQuoteData.share_B) {
        setPaymentOverlayStep(2);
        return;
      }
    } catch (e) {
      console.warn("Could not pre-fetch balance", e);
    }

    setPaymentOverlayStep(0);

    try {
      const req = {
        poolId: selectedPoolToJoin.orderId,
        farmerEmail: user?.email,
        cropName: joinCropName,
        weightKg: parseFloat(joinWeight) || 0,
        pickupAddress: joinPickupAddress,
        dropAddress: joinDropAddress,
        pickupLat: parseFloat(joinPickupLat) || 0,
        pickupLon: parseFloat(joinPickupLon) || 0,
        dropLat: parseFloat(joinDropLat) || 0,
        dropLon: parseFloat(joinDropLon) || 0,
        paymentAmount: joinQuoteData.totalShareB
      };

      console.log("----- FRONTEND JOIN POOL LOG -----");
      console.log(`Wallet Balance: ${currentWalletBal}`);
      console.log(`Quote Amount: ${joinQuoteData.totalShareB}`);
      console.log(`Pool ID: ${req.poolId}`);
      console.log(`User ID: ${user?.email}`);
      console.log(`Current Wallet Version: Latest from Firestore`);
      console.log(`Current Timestamp: ${new Date().toISOString()}`);

      const res = await apiClient.post('/api/logistics/join-pool', req);

      console.log(`HTTP Status: ${res.status}`);
      console.log(`Backend Error: null`);
      console.log(`Response Body:`, res.data);

      if (res.data.success) {
        setPaymentOverlayStep(1);
        setTimeout(() => {
          setPaymentOverlayStep(null);
          setShowJoinInvoice(false);
          setSelectedPoolToJoin(null);
          clearJoinForm();
          fetchPools(); // Reload Review tab
        }, 2000);
      } else {
        if (res.data.error === "INSUFFICIENT_BALANCE") {
          setPaymentOverlayStep(2);
        } else {
          setPaymentOverlayStep(null);
          setErrorMessage(res.data.message || res.data.error || "Failed to join pool.");
        }
      }
    } catch (e) {
      console.log("Status:", e.response?.status);
      console.log("Response:", e.response?.data);
      const backendError = e.response?.data?.error || e.message;
      if (backendError === "INSUFFICIENT_BALANCE") {
        setPaymentOverlayStep(2);
      } else {
        setPaymentOverlayStep(null);
        setErrorMessage(e.response?.data?.message || backendError || "Join Pool Failed");
      }
    }
  };

  return (
    <div style={{ width: '100%' }}>
      <div style={{ display: 'flex', background: '#f3f4f6', borderRadius: '16px', padding: '6px', marginBottom: '32px', position: 'relative' }}>
        <div 
          onClick={() => setActiveTab(0)}
          style={{ flex: 1, padding: '14px', textAlign: 'center', cursor: 'pointer', zIndex: 1, fontSize: '15px', fontWeight: 700, color: activeTab === 0 ? 'var(--terracotta-primary)' : '#6b7280', transition: 'color 0.3s' }}
        >
          Create New Pool
        </div>
        <div 
          onClick={() => setActiveTab(1)}
          style={{ flex: 1, padding: '14px', textAlign: 'center', cursor: 'pointer', zIndex: 1, fontSize: '15px', fontWeight: 700, color: activeTab === 1 ? 'var(--terracotta-primary)' : '#6b7280', transition: 'color 0.3s' }}
        >
          Review Pools
        </div>
        <motion.div 
          initial={false}
          animate={{ x: activeTab === 0 ? '0%' : '100%' }}
          transition={{ type: 'spring', stiffness: 300, damping: 30 }}
          style={{ position: 'absolute', top: '6px', bottom: '6px', left: '6px', width: 'calc(50% - 6px)', background: '#fff', borderRadius: '12px', boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}
        />
      </div>

      <AnimatePresence mode="wait">
        {activeTab === 0 && (
          <motion.div 
            key="tab1"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.2 }}
          >
            <SectionCard title="Cargo Details" icon={Package}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <InputField label="Crop Name" placeholder="e.g. Tomatoes" value={cropName} onChange={e => setCropName(e.target.value)} />
                <InputField label="Weight (kg)" type="number" placeholder="0" value={weightToPool} onChange={e => setWeightToPool(e.target.value)} />
              </div>
            </SectionCard>

            <SectionCard title="Pickup Details" icon={MapPin}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <span style={{ fontSize: '14px', color: '#6b7280', fontWeight: 600 }}>Location Method</span>
                <SegmentedControl value={pickupMode} onChange={setPickupMode} onAutoClick={() => handleAutoFetchLocation('pickup')} />
              </div>
              <InputField label="Exact Pickup Address" placeholder="Enter complete address" value={pickupAddress} onChange={e => setPickupAddress(e.target.value)} />
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <InputField label="Latitude" type="number" placeholder="Auto-filled" value={pickupLat} onChange={e => setPickupLat(e.target.value)} disabled={pickupMode === 'auto'} />
                <InputField label="Longitude" type="number" placeholder="Auto-filled" value={pickupLon} onChange={e => setPickupLon(e.target.value)} disabled={pickupMode === 'auto'} />
              </div>
            </SectionCard>

            <SectionCard title="Drop Details" icon={MapPin}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <span style={{ fontSize: '14px', color: '#6b7280', fontWeight: 600 }}>Location Method</span>
                <SegmentedControl value={dropMode} onChange={setDropMode} onAutoClick={() => handleAutoFetchLocation('drop')} />
              </div>
              <InputField label="Exact Drop Destination" placeholder="Enter complete address" value={dropAddress} onChange={e => setDropAddress(e.target.value)} />
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <InputField label="Latitude" type="number" placeholder="Auto-filled" value={dropLat} onChange={e => setDropLat(e.target.value)} disabled={dropMode === 'auto'} />
                <InputField label="Longitude" type="number" placeholder="Auto-filled" value={dropLon} onChange={e => setDropLon(e.target.value)} disabled={dropMode === 'auto'} />
              </div>
            </SectionCard>

            <SectionCard title="Dispatch Schedule" icon={Calendar}>
              <LocalizationProvider dateAdapter={AdapterDayjs}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <DatePicker 
                    label="Dispatch Date" 
                    value={dispatchDate ? dayjs(dispatchDate) : null}
                    onChange={(newValue) => setDispatchDate(newValue ? newValue.format('YYYY-MM-DD') : '')}
                    slotProps={{ textField: { variant: 'outlined', sx: { width: '100%', '& .MuiOutlinedInput-root': { borderRadius: '12px', background: '#f9fafb' } } } }}
                  />
                  <TimePicker 
                    label="Dispatch Time" 
                    value={dispatchTime ? dayjs(`2000-01-01T${dispatchTime}`) : null}
                    onChange={(newValue) => setDispatchTime(newValue ? newValue.format('HH:mm') : '')}
                    slotProps={{ textField: { variant: 'outlined', sx: { width: '100%', '& .MuiOutlinedInput-root': { borderRadius: '12px', background: '#f9fafb' } } } }}
                  />
                </div>
              </LocalizationProvider>
            </SectionCard>

            <div style={{ background: '#fff9f9', border: '1px solid #fee2e2', borderRadius: '16px', padding: '20px', marginBottom: '32px', display: 'flex', gap: '16px', alignItems: 'flex-start' }}>
              <AlertTriangle color="#ef4444" size={24} style={{ marginTop: '2px', flexShrink: 0 }} />
              <div>
                <h4 style={{ margin: '0 0 8px 0', color: '#991b1b', fontSize: '16px', fontWeight: 700 }}>Privacy & Escrow Policy</h4>
                <p style={{ margin: '0 0 16px 0', color: '#b91c1c', fontSize: '14px', lineHeight: 1.5 }}>
                  By proceeding, your funds will be securely locked in an Escrow account. Because Heavy Freight requires a guaranteed fare for drivers, you CANNOT cancel this order once the Escrow is engaged.
                </p>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <input 
                    type="checkbox" 
                    checked={termsAccepted} 
                    onChange={e => { setTermsAccepted(e.target.checked); setHasReadPrivacyPolicy(true); }}
                    style={{ width: '20px', height: '20px', accentColor: 'var(--terracotta-primary)', cursor: 'pointer' }}
                  />
                  <span style={{ fontSize: '14px', fontWeight: 600, color: '#7f1d1d' }}>I acknowledge and accept this policy.</span>
                </div>
              </div>
            </div>

            {!hasValidGps && (
              <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '12px', padding: '12px', marginBottom: '24px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                <AlertTriangle color="#ef4444" size={20} />
                <span style={{ fontSize: '13px', color: '#b91c1c', fontWeight: 600 }}>Please tap the GPS icons (Auto Fetch) to capture valid coordinates before creating a pool.</span>
              </div>
            )}

            <button 
              onClick={handleQuotePool}
              disabled={!isFormComplete || isProcessing}
              style={{ 
                width: '100%', padding: '20px', borderRadius: '16px', 
                background: isFormComplete ? 'var(--terracotta-primary)' : '#e5e7eb', 
                color: isFormComplete ? '#fff' : '#9ca3af', 
                fontSize: '18px', fontWeight: 800, border: 'none', 
                cursor: isFormComplete ? 'pointer' : 'not-allowed',
                boxShadow: isFormComplete ? '0 12px 24px rgba(225, 90, 70, 0.25)' : 'none',
                transition: 'all 0.3s'
              }}
            >
              {isProcessing ? <div style={{ display: 'flex', justifyContent: 'center' }}><AgriLoading size={24} /></div> : 'Review & Lock Escrow'}
            </button>
          </motion.div>
        )}

        {activeTab === 1 && (
          <motion.div 
            key="tab2"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.2 }}
            style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}
          >
            {isLoadingPools ? (
              <div style={{ padding: '80px', display: 'flex', justifyContent: 'center' }}><AgriLoading size={48} /></div>
            ) : availablePools.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '64px 24px', background: '#fff', borderRadius: '24px', border: '1px dashed #e5e7eb' }}>
                <Search size={48} color="#d1d5db" style={{ margin: '0 auto 16px auto' }} />
                <h3 style={{ margin: '0 0 8px 0', color: '#374151', fontSize: '20px', fontWeight: 700 }}>No Active Pools Found</h3>
                <p style={{ margin: 0, color: '#6b7280', fontSize: '15px' }}>There are currently no logistics pools available in your area. Be the first to create one!</p>
              </div>
            ) : (
              availablePools.map((pool, idx) => {
                const isHost = pool.hostEmail === user?.email;
                const isCoLoader = pool.coLoaderEmail === user?.email;
                const isCompleted = pool.status === 'DELIVERED' || pool.status.includes('CANCELLED');
                const isTimeExpired = (pool.dispatchTimestamp - Date.now() <= 0) && !isCompleted;
                const isExpired = pool.status === 'EXPIRED' || pool.status === 'FAILED_NO_DRIVER' || pool.status === 'NO_DRIVER' || isTimeExpired;
                const isCancellable = isHost && !pool.coLoaderEmail && !pool.driverEmail && pool.status === 'PENDING_CO_LOADER' && !isExpired;
                
                // Do not show failed pools in Review Pools for Co-loader, unless they are the Host
                if (isCoLoader && !isHost && isExpired) return null;

                let statusColor = '#f59e0b', statusBg = '#fef3c7', statusText = 'Waiting for Co-loader';
                if (pool.status.includes('CANCELLED')) { statusColor = '#ef4444'; statusBg = '#fee2e2'; statusText = 'Cancelled'; }
                else if (isExpired) { statusColor = '#ef4444'; statusBg = '#fee2e2'; statusText = 'FAILED - NO DRIVER'; }
                else if (pool.status === 'DELIVERED') { statusColor = '#10b981'; statusBg = '#d1fae5'; statusText = 'Completed'; }
                else if (pool.driverEmail || pool.status.includes('IN_TRANSIT') || pool.status.includes('EN_ROUTE')) { statusColor = '#3b82f6'; statusBg = '#dbeafe'; statusText = 'Ongoing'; }

                const totalWeight = (pool.weightKg || 0) + (pool.remainingCapacity || 0);
                const capacityPercent = totalWeight > 0 ? (pool.remainingCapacity / totalWeight) * 100 : 0;

                return (
                  <motion.div 
                    whileHover={{ y: -4, boxShadow: '0 12px 32px rgba(0,0,0,0.08)' }}
                    key={idx} 
                    style={{ 
                      padding: '24px', borderRadius: '24px', background: '#fff', border: '1px solid #f0f0f0', 
                      cursor: 'pointer', 
                      boxShadow: '0 4px 12px rgba(0,0,0,0.03)', position: 'relative', overflow: 'hidden' 
                    }} 
                    onClick={() => { 
                      if (isExpired) return;
                      if (isHost || isCoLoader) {
                        navigate(`/farmer/pool/${pool.orderId}`);
                      } else {
                        setSelectedPoolToJoin(pool);
                      }
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                          <span style={{ padding: '6px 12px', borderRadius: '12px', fontSize: '13px', fontWeight: 800, color: statusColor, background: statusBg }}>{statusText}</span>
                          {pool.coLoaderEmail && <span style={{ padding: '6px 12px', borderRadius: '12px', fontSize: '13px', fontWeight: 800, color: '#10b981', background: '#d1fae5' }}>Co-Loaded</span>}
                          {isExpired && <span style={{ padding: '6px 12px', borderRadius: '12px', fontSize: '13px', fontWeight: 800, color: '#991b1b', background: '#fecaca' }}>Refund Completed</span>}
                        </div>
                        <h4 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#1f2937' }}>{pool.cropName}</h4>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600 }}>Vehicle</span>
                        <span style={{ fontSize: '15px', fontWeight: 800, color: 'var(--terracotta-primary)' }}>{pool.vehicleType}</span>
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 32px 1fr', gap: '8px', alignItems: 'center', background: '#f9fafb', padding: '16px', borderRadius: '16px', marginBottom: '20px' }}>
                      <div>
                        <span style={{ display: 'block', fontSize: '12px', color: '#9ca3af', fontWeight: 600, marginBottom: '4px' }}>Pickup</span>
                        <span style={{ fontSize: '14px', color: '#374151', fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{pool.pickupAddress}</span>
                      </div>
                      <ArrowRight size={20} color="#cbd5e1" style={{ margin: '0 auto' }} />
                      <div>
                        <span style={{ display: 'block', fontSize: '12px', color: '#9ca3af', fontWeight: 600, marginBottom: '4px' }}>Destination</span>
                        <span style={{ fontSize: '14px', color: '#374151', fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{pool.dropAddress}</span>
                      </div>
                    </div>

                    <div style={{ marginBottom: '24px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', fontWeight: 600, marginBottom: '8px' }}>
                        <span style={{ color: '#6b7280' }}>Capacity</span>
                        <span style={{ color: '#10b981' }}>{pool.remainingCapacity} kg available</span>
                      </div>
                      <div style={{ height: '8px', background: '#f3f4f6', borderRadius: '4px', overflow: 'hidden' }}>
                        <div style={{ height: '100%', width: `${100 - capacityPercent}%`, background: 'var(--golden-yellow)', borderRadius: '4px' }} />
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '16px', borderTop: '1px solid #f3f4f6' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <User size={16} color="#9ca3af" />
                        <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>Host: {pool.hostEmail.substring(0,12)}...</span>
                      </div>
                      
                      {isCancellable && (
                        <button onClick={(e) => { e.stopPropagation(); handleCancelOrder(pool.orderId); }} style={{ padding: '8px 16px', borderRadius: '12px', background: '#fee2e2', color: '#ef4444', border: 'none', fontSize: '13px', fontWeight: 700, cursor: 'pointer' }}>Cancel & Refund</button>
                      )}
                      {isExpired && (
                        <button onClick={(e) => { e.stopPropagation(); handleDeletePool(pool.orderId); }} style={{ padding: '8px 16px', borderRadius: '12px', background: '#f3f4f6', color: '#6b7280', border: 'none', fontSize: '13px', fontWeight: 700, cursor: 'pointer' }}>Delete Record</button>
                      )}
                    </div>
                  </motion.div>
                );
              })
            )}
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {showDisclaimerDialog && quoteData && (
          <motion.div 
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            style={{ position: 'fixed', inset: 0, background: 'rgba(17,24,39,0.7)', zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(8px)', padding: '24px' }}
          >
            <motion.div 
              initial={{ scale: 0.95, y: 20 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, y: 20 }}
              style={{ background: '#fff', padding: '32px', borderRadius: '32px', width: '100%', maxWidth: '440px', boxShadow: '0 24px 48px rgba(0,0,0,0.2)' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <h3 style={{ margin: 0, color: '#1f2937', fontSize: '24px', fontWeight: 900 }}>Quote Summary</h3>
                <button onClick={() => setShowDisclaimerDialog(false)} style={{ background: '#f3f4f6', border: 'none', borderRadius: '50%', width: '36px', height: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}><X size={20} color="#6b7280" /></button>
              </div>

              <div style={{ background: '#f9fafb', borderRadius: '20px', padding: '24px', marginBottom: '24px', border: '1px solid #f0f0f0' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Total Distance</span><strong style={{ color: '#1f2937' }}>{quoteData.distanceKm.toFixed(2)} km</strong></div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Vehicle Class</span><strong style={{ color: 'var(--terracotta-primary)' }}>{quoteData.vehicleType}</strong></div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Base Fare</span><strong style={{ color: '#1f2937' }}>{formatCurrency(quoteData.baseFare)}</strong></div>
                <div style={{ borderBottom: '1px dashed #d1d5db', margin: '16px 0' }} />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#374151', fontWeight: 800, fontSize: '18px' }}>Escrow Total</span>
                  <strong style={{ color: 'var(--terracotta-primary)', fontSize: '24px', fontWeight: 900 }}>{formatCurrency(quoteData.totalAmount.toFixed(2))}</strong>
                </div>
              </div>
              
              <div style={{ background: '#fff9f9', border: '1px solid #fee2e2', borderRadius: '16px', padding: '16px', marginBottom: '24px', display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                <AlertTriangle color="#ef4444" size={20} style={{ flexShrink: 0, marginTop: '2px' }} />
                <p style={{ margin: 0, color: '#b91c1c', fontSize: '13px', lineHeight: 1.5, fontWeight: 500 }}>
                  You are responsible for 100% of this fare unless another farmer joins your pool. Funds will be locked instantly.
                </p>
              </div>

              <button 
                onClick={() => { handleCreatePool(); }}
                style={{ width: '100%', padding: '18px', background: 'var(--terracotta-primary)', color: '#fff', borderRadius: '16px', border: 'none', fontSize: '16px', fontWeight: 800, cursor: 'pointer', boxShadow: '0 8px 24px rgba(225, 90, 70, 0.25)' }}
              >
                Proceed to Escrow Lock
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {selectedPoolToJoin && (
          <motion.div 
            initial={{ opacity: 0, x: '100%' }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: '100%' }} transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            style={{ position: 'fixed', inset: 0, background: '#fff', zIndex: 120, display: 'flex', flexDirection: 'column', overflowY: 'auto' }}
          >
            <AnimatePresence>
              {isQuotingJoin && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  style={{ position: 'absolute', inset: 0, background: 'rgba(255,255,255,0.9)', zIndex: 200, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(4px)' }}
                >
                  <AgriLoading size={64} />
                  <h3 style={{ marginTop: '24px', color: 'var(--terracotta-primary)', fontSize: '20px', fontWeight: 800 }}>Calculating Split Cost...</h3>
                  <p style={{ color: '#6b7280', marginTop: '8px', fontWeight: 600 }}>Please wait while we calculate the optimal detour and fare.</p>
                </motion.div>
              )}
            </AnimatePresence>
            <div style={{ maxWidth: '800px', margin: '0 auto', width: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'center', padding: '32px 24px', position: 'sticky', top: 0, background: 'rgba(255,255,255,0.9)', backdropFilter: 'blur(8px)', zIndex: 10 }}>
                <button disabled={isQuotingJoin} onClick={() => setSelectedPoolToJoin(null)} style={{ background: '#f3f4f6', border: 'none', borderRadius: '12px', width: '48px', height: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', marginRight: '24px' }}><ArrowLeft size={24} color="#374151" /></button>
                <h2 style={{ margin: 0, fontSize: '28px', fontWeight: 900, color: 'var(--terracotta-primary)' }}>Join Co-Load Pool</h2>
              </div>

              <div style={{ padding: '0 24px 80px 24px' }}>
                <SectionCard title="Selected Pool Details" icon={Package}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '16px' }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600 }}>Crop Name</span>
                      <span style={{ fontSize: '15px', color: '#1f2937', fontWeight: 700 }}>{selectedPoolToJoin.cropName}</span>
                    </div>
                    <div>
                      <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600 }}>Remaining Capacity</span>
                      <span style={{ fontSize: '15px', color: '#10b981', fontWeight: 700 }}>{selectedPoolToJoin.remainingCapacity} kg</span>
                    </div>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div>
                      <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600 }}>Host Pickup Address</span>
                      <span style={{ fontSize: '14px', color: '#374151', fontWeight: 500 }}>{selectedPoolToJoin.pickupAddress}</span>
                    </div>
                    <div>
                      <span style={{ display: 'block', fontSize: '13px', color: '#6b7280', fontWeight: 600 }}>Host Drop Address</span>
                      <span style={{ fontSize: '14px', color: '#374151', fontWeight: 500 }}>{selectedPoolToJoin.dropAddress}</span>
                    </div>
                  </div>
                </SectionCard>

                <SectionCard title="Your Cargo Details" icon={Package}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <InputField label="Crop Name" placeholder="e.g. Potatoes" value={joinCropName} onChange={e => setJoinCropName(e.target.value)} />
                    <InputField label="Weight (kg)" type="number" placeholder="0" value={joinWeight} onChange={e => setJoinWeight(e.target.value)} />
                  </div>
                  {(parseFloat(joinWeight) || 0) > selectedPoolToJoin.remainingCapacity && (
                    <div style={{ color: '#ef4444', fontSize: '14px', marginTop: '8px', fontWeight: 600 }}>
                      Entered quantity exceeds remaining pool capacity. Only {selectedPoolToJoin.remainingCapacity} kg capacity is available.
                    </div>
                  )}
                </SectionCard>

                <SectionCard title="Your Pickup Location" icon={MapPin}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                    <span style={{ fontSize: '14px', color: '#6b7280', fontWeight: 600 }}>Location Method</span>
                    <SegmentedControl value={joinPickupMode} onChange={setJoinPickupMode} onAutoClick={() => handleAutoFetchLocation('joinPickup')} />
                  </div>
                  <InputField label="Exact Pickup Address" placeholder="Enter complete address" value={joinPickupAddress} onChange={e => setJoinPickupAddress(e.target.value)} />
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <InputField label="Latitude" type="number" placeholder="Auto-filled" value={joinPickupLat} onChange={e => setJoinPickupLat(e.target.value)} disabled={joinPickupMode === 'auto'} />
                    <InputField label="Longitude" type="number" placeholder="Auto-filled" value={joinPickupLon} onChange={e => setJoinPickupLon(e.target.value)} disabled={joinPickupMode === 'auto'} />
                  </div>
                </SectionCard>

                <SectionCard title="Your Destination" icon={MapPin}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                    <span style={{ fontSize: '14px', color: '#6b7280', fontWeight: 600 }}>Location Method</span>
                    <SegmentedControl value={joinDropMode} onChange={setJoinDropMode} onAutoClick={() => handleAutoFetchLocation('joinDrop')} />
                  </div>
                  <InputField label="Exact Drop Destination" placeholder="Enter complete address" value={joinDropAddress} onChange={e => setJoinDropAddress(e.target.value)} />
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <InputField label="Latitude" type="number" placeholder="Auto-filled" value={joinDropLat} onChange={e => setJoinDropLat(e.target.value)} disabled={joinDropMode === 'auto'} />
                    <InputField label="Longitude" type="number" placeholder="Auto-filled" value={joinDropLon} onChange={e => setJoinDropLon(e.target.value)} disabled={joinDropMode === 'auto'} />
                  </div>
                </SectionCard>

                <button 
                  onClick={handleQuoteJoin}
                  disabled={!isJoinFormComplete || isQuotingJoin || (parseFloat(joinWeight) || 0) > selectedPoolToJoin.remainingCapacity}
                  style={{ 
                    width: '100%', padding: '20px', marginTop: '16px', borderRadius: '16px', 
                    background: (isJoinFormComplete && (parseFloat(joinWeight) || 0) <= selectedPoolToJoin.remainingCapacity) ? 'var(--terracotta-primary)' : '#e5e7eb', 
                    color: (isJoinFormComplete && (parseFloat(joinWeight) || 0) <= selectedPoolToJoin.remainingCapacity) ? '#fff' : '#9ca3af', 
                    fontSize: '18px', fontWeight: 800, border: 'none', cursor: (isJoinFormComplete && (parseFloat(joinWeight) || 0) <= selectedPoolToJoin.remainingCapacity && !isQuotingJoin) ? 'pointer' : 'not-allowed',
                    boxShadow: (isJoinFormComplete && (parseFloat(joinWeight) || 0) <= selectedPoolToJoin.remainingCapacity) ? '0 12px 24px rgba(225, 90, 70, 0.25)' : 'none',
                    transition: 'all 0.3s'
                  }}
                >
                  Calculate Share & Review
                </button>
              </div>
            </div>

            <AnimatePresence>
              {showJoinInvoice && joinQuoteData && (
                <motion.div 
                  initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                  style={{ position: 'fixed', inset: 0, background: 'rgba(17,24,39,0.7)', zIndex: 130, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(8px)', padding: '24px' }}
                >
                  <motion.div initial={{ scale: 0.95, y: 20 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, y: 20 }} style={{ background: '#fff', padding: '32px', borderRadius: '32px', width: '100%', maxWidth: '440px', boxShadow: '0 24px 48px rgba(0,0,0,0.2)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                      <h3 style={{ margin: 0, color: '#1f2937', fontSize: '24px', fontWeight: 900 }}>Split Invoice</h3>
                      <button onClick={() => setShowJoinInvoice(false)} style={{ background: '#f3f4f6', border: 'none', borderRadius: '50%', width: '36px', height: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}><X size={20} color="#6b7280" /></button>
                    </div>
                    
                    <div style={{ background: '#f9fafb', borderRadius: '20px', padding: '24px', marginBottom: '24px', border: '1px solid #f0f0f0' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Crop Name</span><strong style={{ color: '#1f2937' }}>{joinQuoteData.cropName}</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Requested Weight</span><strong style={{ color: '#1f2937' }}>{joinQuoteData.joinedQuantity} kg</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Remaining Capacity</span><strong style={{ color: '#10b981' }}>{joinQuoteData.remainingCapacityAfter} kg</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Pickup Address</span><strong style={{ color: '#1f2937', textAlign: 'right', maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{joinPickupAddress}</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Drop Address</span><strong style={{ color: '#1f2937', textAlign: 'right', maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{joinDropAddress}</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Distance</span><strong style={{ color: '#1f2937' }}>{joinQuoteData.totalDistance} km</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Vehicle</span><strong style={{ color: 'var(--terracotta-primary)' }}>{joinQuoteData.vehicleType}</strong></div>
                      <div style={{ borderBottom: '1px dashed #d1d5db', margin: '12px 0' }} />
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Total Transport Cost</span><strong style={{ color: '#1f2937' }}>{formatCurrency(joinQuoteData.totalDriverPayout.toFixed(2))}</strong></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}><span style={{ color: '#6b7280', fontWeight: 600 }}>Escrow Amount</span><strong style={{ color: '#1f2937' }}>{formatCurrency(joinQuoteData.escrowAmount.toFixed(2))}</strong></div>
                      <div style={{ borderBottom: '1px dashed #d1d5db', margin: '16px 0' }} />
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontSize: '18px', fontWeight: 800, color: '#374151' }}>Total Payable</span>
                        <strong style={{ color: 'var(--terracotta-primary)', fontSize: '24px', fontWeight: 900 }}>{formatCurrency(joinQuoteData.totalPayable.toFixed(2))}</strong>
                      </div>
                    </div>

                    <div style={{ background: '#fff9f9', border: '1px solid #fee2e2', borderRadius: '16px', padding: '16px', marginBottom: '24px', display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                      <AlertTriangle color="#ef4444" size={20} style={{ flexShrink: 0, marginTop: '2px' }} />
                      <p style={{ margin: 0, color: '#b91c1c', fontSize: '13px', lineHeight: 1.5, fontWeight: 500 }}>
                        By confirming, you will be added to this vehicle and your wallet will be charged immediately.
                      </p>
                    </div>

                    <button 
                      onClick={() => { handleJoinPool(); }}
                      style={{ width: '100%', padding: '18px', background: 'var(--terracotta-primary)', color: '#fff', borderRadius: '16px', border: 'none', fontSize: '16px', fontWeight: 800, cursor: 'pointer', boxShadow: '0 8px 24px rgba(225, 90, 70, 0.25)' }}
                    >
                      Proceed to Pay {formatCurrency(joinQuoteData.totalShareB.toFixed(2))}
                    </button>
                  </motion.div>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>

      {paymentOverlayStep !== null && (
        <CropPoolPaymentOverlay 
          step={paymentOverlayStep} 
          totalPrice={showDisclaimerDialog ? quoteData?.totalAmount : joinQuoteData?.totalShareB} 
          walletBal={currentWalletBal} 
          onCancel={() => setPaymentOverlayStep(null)} 
          onAddMoney={() => {
            if (!showDisclaimerDialog && selectedPoolToJoin) {
              const pendingReq = {
                type: 'JOIN_POOL',
                poolId: selectedPoolToJoin.orderId,
                farmerEmail: user?.email,
                cropName: joinCropName,
                weightKg: parseFloat(joinWeight) || 0,
                pickupAddress: joinPickupAddress,
                dropAddress: joinDropAddress,
                pickupLat: parseFloat(joinPickupLat) || 0,
                pickupLon: parseFloat(joinPickupLon) || 0,
                dropLat: parseFloat(joinDropLat) || 0,
                dropLon: parseFloat(joinDropLon) || 0
              };
              localStorage.setItem('pendingPoolRequest', JSON.stringify(pendingReq));
            }
          }}
        />
      )}
    </div>
  );
};

export default CropPoolCard;
