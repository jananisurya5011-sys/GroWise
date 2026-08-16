import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { collection, doc, addDoc, setDoc } from 'firebase/firestore';
import gsap from 'gsap';
import { motion, AnimatePresence } from 'framer-motion';
import { Heart, MapPin, Package, ArrowLeft, CheckCircle, Clock, X, Loader2 } from 'lucide-react';

const DonationHub = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [items, setItems] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  
  // Dialog State
  const [activeDonation, setActiveDonation] = useState(null);
  const [requestQty, setRequestQty] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchRescueFeed = async () => {
      try {
        const lat = localStorage.getItem('LAT') || 0;
        const lon = localStorage.getItem('LON') || 0;
        const res = await apiClient.post('/api/ngo/rescue-feed', { lat, lon });
        setItems(res.data || []);
      } catch (e) {
        console.error("Error fetching rescue feed", e);
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchRescueFeed();
  }, []);

  const handleAcceptClick = (item) => {
    setActiveDonation(item);
    setRequestQty(item.availableKg.toString());
  };

  const submitDonationRequest = async () => {
    if (!requestQty || parseFloat(requestQty) <= 0) {
      alert("Please enter a valid quantity.");
      return;
    }
    
    const qty = parseFloat(requestQty);
    if (qty <= 0 || qty > activeDonation.availableKg) {
      alert("Invalid quantity requested.");
      return;
    }
    
    setIsSubmitting(true);
    try {
      const farmerEmail = activeDonation.farmerEmail || activeDonation.email;
      const ngoEmail = user.email;
      const chatId = ngoEmail < farmerEmail ? `${ngoEmail}_${farmerEmail}` : `${farmerEmail}_${ngoEmail}`;
      
      const payload = {
        ngoEmail: ngoEmail,
        farmerEmail: farmerEmail,
        itemId: activeDonation.id,
        requestedQuantity: qty
      };
      
      const res = await apiClient.post('/api/orders/request-donation', payload);
      if (!res.data.success) {
        alert("Failed: " + res.data.error);
        setIsSubmitting(false);
        return;
      }

      setItems(items.filter(i => i.id !== activeDonation.id));
      setActiveDonation(null);
      navigate(`/deals/${chatId}`);
    } catch (e) {
      alert("Failed to send donation request: " + (e.response?.data?.error || e.message));
      console.error(e);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async (itemId) => {
    try {
      await apiClient.post('/api/inventory/reject-ngo', { itemId, ngoEmail: user.email });
      setItems(items.filter(i => i.id !== itemId));
    } catch (e) {
      alert("Error rejecting item");
    }
  };

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', padding: '24px', paddingBottom: '80px', minHeight: '100vh', backgroundColor: '#F9FAFB' }}>
      
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/home/user')} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: '28px', fontWeight: 900, color: '#111' }}>Donation Hub</h1>
          <p style={{ margin: '4px 0 0 0', fontSize: '15px', color: '#666', fontWeight: 600 }}>Rescue fresh produce before it expires</p>
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '60px' }}>
          <Clock size={32} color="#F2A33A" style={{ animation: 'spin 2s linear infinite' }} />
        </div>
      ) : items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px', backgroundColor: '#fff', borderRadius: '24px', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
          <Heart size={48} color="#ccc" style={{ margin: '0 auto 16px' }} />
          <h3 style={{ fontSize: '20px', fontWeight: 800, color: '#444' }}>No Rescue Alerts</h3>
          <p style={{ color: '#888' }}>There are currently no products available for donation in your area.</p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '24px' }}>
          <AnimatePresence>
            {items.map((item) => (
              <motion.div 
                key={item.id}
                layout
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, x: -200, scale: 0.9 }}
                drag="x"
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={0.8}
                onDragEnd={(e, { offset, velocity }) => {
                  const swipe = offset.x;
                  if (swipe < -100) {
                    handleReject(item.id);
                  }
                }}
                style={{
                  backgroundColor: '#fff', 
                  borderRadius: '24px', 
                  overflow: 'hidden',
                  boxShadow: '0 10px 30px rgba(0,0,0,0.04)', 
                  border: '1px solid rgba(255,255,255,0.5)',
                  display: 'flex', flexDirection: 'column',
                  cursor: 'grab'
                }}
                whileTap={{ cursor: 'grabbing' }}
              >
                <div style={{ height: '160px', backgroundColor: '#f1f5f9', position: 'relative' }}>
                  {item.imageUrl ? (
                    <img src={`/api/inventory/uploads/product_photos/${item.imageUrl.split(/[\/\\]/).pop()}`} alt="Produce" style={{ width: '100%', height: '100%', objectFit: 'cover', pointerEvents: 'none' }} />
                  ) : (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Package size={40} color="#ccc" /></div>
                  )}
                  <div style={{ position: 'absolute', top: '12px', right: '12px', backgroundColor: 'rgba(255,255,255,0.9)', backdropFilter: 'blur(4px)', padding: '6px 12px', borderRadius: '20px', fontSize: '12px', fontWeight: 800, color: '#d84315', display: 'flex', alignItems: 'center', gap: '4px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                    <Heart size={14} fill="#d84315" /> Rescue
                  </div>
                </div>
                
                <div style={{ padding: '24px', flex: 1, display: 'flex', flexDirection: 'column' }}>
                  <h3 style={{ margin: '0 0 8px 0', fontSize: '20px', fontWeight: 900, color: '#111' }}>{item.cropName}</h3>
                  <div style={{ display: 'flex', gap: '16px', marginBottom: '16px' }}>
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontSize: '12px', color: '#888', fontWeight: 600 }}>Quantity</span>
                      <span style={{ fontSize: '16px', fontWeight: 800, color: '#10b981' }}>{item.availableKg} KG</span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontSize: '12px', color: '#888', fontWeight: 600 }}>Distance</span>
                      <span style={{ fontSize: '16px', fontWeight: 800, color: '#6366f1' }}>{item.distanceKm} km</span>
                    </div>
                  </div>
                  
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px', backgroundColor: '#f8fafc', borderRadius: '12px', marginBottom: '24px' }}>
                    <MapPin size={16} color="#64748b" />
                    <span style={{ fontSize: '13px', color: '#475569', fontWeight: 600 }}>Farmer: {item.farmerName}</span>
                  </div>
                  
                  <div style={{ marginTop: 'auto', display: 'flex', gap: '12px' }}>
                    <button onClick={() => handleReject(item.id)} style={{ flex: 1, padding: '12px', backgroundColor: '#fff', border: '2px solid #e2e8f0', borderRadius: '12px', color: '#64748b', fontSize: '14px', fontWeight: 800, cursor: 'pointer', transition: 'background 0.2s' }}>Ignore</button>
                    <button onClick={() => handleAcceptClick(item)} style={{ flex: 1, padding: '12px', background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)', border: 'none', borderRadius: '12px', color: '#fff', fontSize: '14px', fontWeight: 800, cursor: 'pointer', boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)' }}>Request</button>
                  </div>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
      )}

      {/* Quantity & Address Request Dialog */}
      <AnimatePresence>
        {activeDonation && (
          <motion.div 
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px' }}
          >
            <motion.div 
              initial={{ scale: 0.9, y: 20 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.9, y: 20 }}
              style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', width: '100%', maxWidth: '400px', boxShadow: '0 24px 48px rgba(0,0,0,0.2)' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                <h2 style={{ margin: 0, fontSize: '20px', fontWeight: 800, color: '#111' }}>Request Donation</h2>
                <button onClick={() => setActiveDonation(null)} style={{ background: 'transparent', border: 'none', cursor: 'pointer', padding: '4px' }}><X size={20} color="#666" /></button>
              </div>
              
              <div style={{ marginBottom: '20px' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#555', marginBottom: '8px' }}>Quantity Required (kg)</label>
                <input 
                  type="number" 
                  value={requestQty} 
                  onChange={(e) => setRequestQty(e.target.value)}
                  max={activeDonation.availableKg}
                  style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '16px', outline: 'none' }}
                />
                <div style={{ fontSize: '11px', color: '#888', marginTop: '6px' }}>Available: {activeDonation.availableKg} kg</div>
              </div>



              <button 
                onClick={submitDonationRequest}
                disabled={isSubmitting}
                style={{ width: '100%', padding: '16px', background: '#10b981', color: '#fff', border: 'none', borderRadius: '12px', fontSize: '16px', fontWeight: 800, cursor: isSubmitting ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
              >
                {isSubmitting ? <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> : <Heart size={18} fill="#fff" />}
                {isSubmitting ? 'Sending Request...' : 'Confirm Request'}
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default DonationHub;
