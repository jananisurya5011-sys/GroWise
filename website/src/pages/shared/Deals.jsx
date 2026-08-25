import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { collection, query, where, onSnapshot, orderBy, doc, updateDoc, setDoc, addDoc, getDocs, getDoc } from 'firebase/firestore';
import { db } from '../../utils/firebase';
import apiClient from '../../utils/apiClient';
import { ArrowLeft, Send, Search, CheckCheck, Image as ImageIcon, MapPin, Bot, FileText, CheckCircle, Navigation, X, Mic, Square, Trash2, Phone, CreditCard, Copy, ShieldCheck, Loader } from 'lucide-react';
import { motion } from 'framer-motion';
import { DealRequestCard, LogisticsChoiceCard, AudioMessageCard, SystemMessageCard, DonationOrderCard } from '../../components/chat/MessageCards';
import { useDealEngine } from '../../hooks/useDealEngine';
import { formatCurrency } from '../../utils/constants';
import notify from '../../services/NotificationService';


// ─── Color constants (matching Android: TerracottaPrimary, GoldenYellow, PeachBackground) ───
const C = {
  terracotta: '#7C3D12',      // TerracottaPrimary
  terracottaLight: '#c2845c', // secondary/buttons
  golden: '#FDB931',          // GoldenYellow
  peach: '#FDF0E8',           // PeachBackground (warm off-white)
  textDark: '#1A1A1A',        // TextDark
  textMuted: '#7A7A7A',       // TextMuted
  blue: '#1565C0',            // Gemini AI blue
  green: '#2E7D32',           // success green
};

// ─── Helpers ─────────────────────────────────────────────────────────────────
function formatTimestamp(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  const now = new Date();
  
  const isToday = d.toDateString() === now.toDateString();
  const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  if (isToday) return time;

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';

  const dd = String(d.getDate()).padStart(2, '0');
  const mon = d.toLocaleString('default', { month: 'short' });
  return `${dd} ${mon}, ${time}`;
}

function nameFromEmail(email) {
  if (!email) return 'User';
  return email.split('@')[0].split('.').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

// ─── FullScreenPayment ────────────────────────────────────────────────────────
const FullScreenPayment = ({ pendingInvoice, farmerEmail, chatId, onComplete }) => {
  const [step, setStep] = useState(0); // 0: securing, 1: verifying, 2: processing, 3: success, 4: insufficient
  const [walletBal, setWalletBal] = useState(0);
  const { user } = useAuth();
  const navigate = useNavigate();

  const isDonation = pendingInvoice.dealType === 'DONATION' || pendingInvoice.type === 'DONATION_REQUEST';
  const origId = pendingInvoice.dealId || pendingInvoice.id;
  const hasProcessed = useRef(false);

  useEffect(() => {
    if (hasProcessed.current) return;
    hasProcessed.current = true;

    let t1, t2, t3, t4;
    const processPayment = async () => {
      try {
        const walletDoc = await getDoc(doc(db, 'wallets', user.email));
        const bal = walletDoc.exists() ? (walletDoc.data().balance || 0) : 0;
        setWalletBal(bal);
        
        const total = pendingInvoice.totalPrice || 0;
        if (bal < total) {
          setStep(4); // Insufficient
          return;
        }

        t1 = setTimeout(() => {
          setStep(1); // Verifying
          t2 = setTimeout(async () => {
            setStep(2); // Processing
            t3 = setTimeout(async () => {
              try {
                if (isDonation) {
                  await apiClient.post('/api/orders/pay-donation-invoice', {
                    orderId: origId,
                    ngoEmail: user.email,
                    transportCost: pendingInvoice.transportCost,
                    chatId: chatId
                  });
                } else {
                  await apiClient.post('/api/orders/create_order', {
                    farmerEmail, userEmail: user.email,
                    cropName: pendingInvoice.cropName, weightKg: pendingInvoice.kg,
                    cropValue: pendingInvoice.totalPrice - pendingInvoice.transportCost,
                    transportFare: pendingInvoice.transportCost, totalPaid: pendingInvoice.totalPrice,
                    isDonation: isDonation,
                    pickupAddress: pendingInvoice.pickupAddress, dropAddress: pendingInvoice.deliveryAddress,
                    pickupLat: pendingInvoice.pickupLat, pickupLon: pendingInvoice.pickupLon,
                    dropLat: pendingInvoice.dropLat, dropLon: pendingInvoice.dropLon,
                    distanceKm: pendingInvoice.distanceKm || 0.0, vehicleType: pendingInvoice.vehicleType || 'Any',
                    dealId: origId, itemId: pendingInvoice.itemId || pendingInvoice.productId || '',
                    chatId: chatId
                  });
                }
                setStep(3); // Success
                t4 = setTimeout(() => {
                  onComplete();
                }, 1500);
              } catch (err) { 
                console.error('Payment flow error', err); 
                notify.error("Payment Failed: " + (err.response?.data?.error || err.message));
                onComplete(); 
              }
            }, 1200);
          }, 1200);
        }, 1200);
      } catch (err) { console.error('Wallet fetch error', err); onComplete(); }
    };
    
    processPayment();
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); clearTimeout(t4); };
  }, []);

  if (step === 4) {
    return (
      <div style={{ position: 'fixed', inset: 0, zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.6)', padding: 20 }}>
        <motion.div initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} style={{ background: 'white', borderRadius: 24, padding: 32, maxWidth: 400, width: '100%', textAlign: 'center', boxShadow: '0 20px 40px rgba(0,0,0,0.2)' }}>
          <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#FFF3E0', margin: '0 auto 20px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <CreditCard size={40} color="#E65100" />
          </div>
          <h2 style={{ margin: '0 0 12px', fontSize: 24, fontWeight: 900, color: '#E65100' }}>Insufficient Balance</h2>
          <p style={{ margin: '0 0 24px', fontSize: 15, color: '#555', lineHeight: 1.5 }}>
            Your wallet balance is lower than the order total.
          </p>
          <div style={{ background: '#FAFAFA', padding: 16, borderRadius: 16, marginBottom: 24, border: '1px solid #EEE' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 14 }}>
              <span style={{ color: '#666' }}>Current Balance</span>
              <span style={{ fontWeight: 800, color: '#333' }}>{formatCurrency((walletBal || 0).toFixed(2))}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
              <span style={{ color: '#666' }}>Required Amount</span>
              <span style={{ fontWeight: 800, color: '#333' }}>{formatCurrency((pendingInvoice?.totalPrice || 0).toFixed(2))}</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button onClick={onComplete} style={{ flex: 1, padding: 14, background: '#F5F5F5', color: '#555', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 15, cursor: 'pointer' }}>Cancel</button>
            <button onClick={() => navigate('/track?tab=wallet')} style={{ flex: 1, padding: 14, background: '#E65100', color: 'white', border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 15, cursor: 'pointer', boxShadow: '0 4px 12px rgba(230,81,0,0.3)' }}>Add Money</button>
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={{ position: 'fixed', inset: 0, zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', background: step === 3 ? 'rgba(46,125,50,0.97)' : 'rgba(255,255,255,0.95)', backdropFilter: 'blur(10px)', transition: 'background 0.5s' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
        {step < 3 ? (
          <>
            <motion.div 
              animate={{ rotateY: step === 0 ? 360 : 0, scale: step === 1 ? [1, 1.1, 1] : 1, rotate: step === 2 ? 360 : 0 }} 
              transition={{ repeat: step === 0 || step === 2 ? Infinity : 0, duration: 2, ease: "linear" }}
              style={{ width: 88, height: 88, borderRadius: '50%', border: `3px solid ${step === 2 ? 'transparent' : C.terracotta}`, borderTopColor: step === 2 ? C.terracotta : undefined, background: C.peach, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 0 30px rgba(124,61,18,0.2)' }}
            >
              {step === 0 && <ShieldCheck size={40} color={C.terracotta} />}
              {step === 1 && <CreditCard size={40} color={C.terracotta} />}
              {step === 2 && <Loader size={40} color={C.terracotta} />}
            </motion.div>
            <motion.p key={step} initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} style={{ fontWeight: 800, fontSize: 18, color: C.terracotta }}>
              {step === 0 ? "Securing Transaction..." : step === 1 ? "Verifying Wallet..." : "Processing Payment..."}
            </motion.p>
            <p style={{ fontWeight: 900, fontSize: 32, color: C.green }}>{formatCurrency((pendingInvoice?.totalPrice || 0).toFixed(2))}</p>
          </>
        ) : (
          <motion.div initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: "spring", bounce: 0.5 }} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
            <div style={{ width: 88, height: 88, borderRadius: '50%', background: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 0 40px rgba(255,255,255,0.3)' }}>
              <CheckCircle size={56} color={C.green} />
            </div>
            <p style={{ fontWeight: 800, fontSize: 24, color: 'white', textShadow: '0 2px 10px rgba(0,0,0,0.1)' }}>Payment Successful!</p>
            <p style={{ color: 'rgba(255,255,255,0.9)', fontSize: 15, fontWeight: 600 }}>Routing to Logistics...</p>
          </motion.div>
        )}
      </div>
    </motion.div>
  );
};

// ─── Profile Avatar ───────────────────────────────────────────────────────────
const Avatar = ({ name, profileUrl, size = 48 }) => {
  const base = import.meta.env.VITE_API_URL || 'http://127.0.0.1:5000';
  const initial = (name || 'U').charAt(0).toUpperCase();
  if (profileUrl) {
    const src = profileUrl.startsWith('http') ? profileUrl : `${base}${profileUrl}`;
    return (
      <img src={src} alt={name} style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', border: `1.5px solid ${C.golden}`, flexShrink: 0 }}
        onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }} />
    );
  }
  return (
    <div style={{ width: size, height: size, borderRadius: '50%', background: C.peach, border: `1.5px solid ${C.golden}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      <span style={{ fontWeight: 800, fontSize: size * 0.38, color: C.terracotta }}>{initial}</span>
    </div>
  );
};

class InvoiceCardErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }
  componentDidCatch(error, errorInfo) {
    console.error("[InvoiceCardErrorBoundary] Caught rendering error:", error, errorInfo);
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '20px', background: 'red', color: 'white', borderRadius: 8 }}>
          <h4>InvoiceCard Crashed!</h4>
          <p>{this.state.error.toString()}</p>
        </div>
      );
    }
    return this.props.children;
  }
}

const DonationInvoiceCard = ({ msg, isMe, isFarmer, onPay }) => {
  const invoiceId = msg?.orderId || 'PENDING';
  const fullUrl = msg?.imageUrl ? (msg.imageUrl.startsWith("http") ? msg.imageUrl : `http://localhost:5000${msg.imageUrl}`) : null;
  const isFree = msg?.transportCost === 0;

  return (
    <div style={{ width: '100%', maxWidth: 380, alignSelf: 'center', background: 'white', borderRadius: 24, overflow: 'hidden', border: `1.5px solid #4CAF50`, boxShadow: '0 8px 24px rgba(0,0,0,0.12)', margin: '16px auto', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
      {/* Header */}
      <div style={{ background: '#4CAF50', padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h3 style={{ margin: 0, color: 'white', fontSize: 18, fontWeight: 900, letterSpacing: 1 }}>DONATION INVOICE</h3>
          <p style={{ margin: 0, color: 'rgba(255,255,255,0.8)', fontSize: 12, fontWeight: 600 }}>Order ID: {invoiceId}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ background: isFree ? '#FFF' : '#FFB300', color: isFree ? '#4CAF50' : '#333', padding: '4px 8px', borderRadius: 8, fontSize: 11, fontWeight: 800, display: 'inline-block', marginTop: 4 }}>
            {isFree ? 'FREE' : (msg?.status || 'PENDING')}
          </div>
        </div>
      </div>

      {/* Item Details */}
      <div style={{ padding: '20px 20px 12px 20px', display: 'flex', gap: 16, alignItems: 'center', borderBottom: '1px dashed #e0e0e0' }}>
        {fullUrl && (
          <img src={fullUrl} alt="Crop" style={{ width: '60px', height: '60px', borderRadius: '12px', objectFit: 'cover', border: '1px solid #f0f0f0' }} />
        )}
        <div style={{ flex: 1 }}>
          <h4 style={{ margin: '0 0 4px 0', fontSize: 18, fontWeight: 900, color: '#1A1A1A', textTransform: 'capitalize' }}>{msg?.cropName || 'Crop'}</h4>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#666' }}>
            <span>Quantity Required:</span>
            <span style={{ fontWeight: 800, color: '#333' }}>{msg?.kg} kg</span>
          </div>
        </div>
      </div>

      {/* Logistics Details */}
      <div style={{ padding: '16px 20px', background: '#FAFAFA', borderBottom: '1px dashed #e0e0e0' }}>
        <div style={{ marginBottom: 12 }}>
          <p style={{ margin: '0 0 4px 0', fontSize: 12, fontWeight: 800, color: '#4CAF50', textTransform: 'uppercase' }}>Pickup Location</p>
          <p style={{ margin: 0, fontSize: 13, color: '#444', fontWeight: 600 }}>{msg?.pickupAddress || 'Farm'}</p>
        </div>
        <div style={{ marginBottom: 12 }}>
          <p style={{ margin: '0 0 4px 0', fontSize: 12, fontWeight: 800, color: '#1565C0', textTransform: 'uppercase' }}>Drop Location</p>
          <p style={{ margin: 0, fontSize: 13, color: '#444', fontWeight: 600 }}>{isFree ? 'Self Pickup' : (msg?.dropAddress || 'Pending')}</p>
        </div>
        
        {!isFree && (
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#555', marginBottom: 4 }}>
            <span>Distance:</span>
            <span style={{ fontWeight: 700, color: '#333' }}>{msg?.distanceKm} KM</span>
          </div>
        )}
        {!isFree && (
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#555', marginBottom: 4 }}>
            <span>Suggested Vehicle:</span>
            <span style={{ fontWeight: 700, color: '#333' }}>{msg?.vehicleType}</span>
          </div>
        )}
        {!isFree && msg?.capacity && (
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#555' }}>
            <span>Vehicle Capacity:</span>
            <span style={{ fontWeight: 700, color: '#333' }}>{msg?.capacity}</span>
          </div>
        )}
      </div>

      {/* Price Breakdown */}
      <div style={{ padding: '16px 20px' }}>
        {!isFree && msg?.baseFare !== undefined && (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#666', marginBottom: 4 }}>
              <span>Base Fare</span>
              <span>₹{(msg.baseFare || 0).toFixed(2)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#666', marginBottom: 4 }}>
              <span>Distance Charge (₹{msg.perKmRate || 0}/km)</span>
              <span>₹{((msg.perKmRate || 0) * (msg.distanceKm || 0)).toFixed(2)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#666', marginBottom: 8 }}>
              <span>Weight Charge</span>
              <span>₹{(msg.weightCharge || 0).toFixed(2)}</span>
            </div>
          </>
        )}
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, color: '#555', marginBottom: 4, fontWeight: 600 }}>
          <span>Transport Fare</span>
          <span>{isFree ? '₹0.00' : `₹${(msg?.transportCost || 0).toFixed(2)}`}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, color: '#555', marginBottom: 12, fontWeight: 600 }}>
          <span>Crop Value</span>
          <span style={{ color: '#4CAF50' }}>₹0.00 (Donation)</span>
        </div>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 12, borderTop: '2px solid #EEE' }}>
          <span style={{ fontSize: 16, fontWeight: 900, color: '#1A1A1A' }}>GRAND TOTAL</span>
          <span style={{ fontSize: 24, fontWeight: 900, color: '#4CAF50' }}>{isFree ? '₹0.00' : `₹${(msg?.totalPrice || 0).toFixed(2)}`}</span>
        </div>
      </div>

      {/* Actions */}
      {!isFarmer && msg?.status === 'PENDING' && !isFree && (
        <div style={{ padding: '0 20px 20px 20px' }}>
          <button onClick={onPay} style={{ width: '100%', padding: '16px', background: '#4CAF50', color: 'white', fontWeight: 900, borderRadius: '16px', border: 'none', cursor: 'pointer', fontSize: 16, boxShadow: '0 4px 12px rgba(76,175,80,0.3)' }}>
             Pay Transport Fare
          </button>
        </div>
      )}
      {!isFarmer && (msg?.status === 'PAID' || isFree) && (
        <div style={{ padding: '0 20px 20px 20px' }}>
          <div style={{ width: '100%', padding: '14px', background: '#E8F5E9', color: '#2E7D32', fontWeight: 800, borderRadius: '16px', border: '2px solid #4CAF50', fontSize: 14, textAlign: 'center' }}>
             Payment Completed
          </div>
        </div>
      )}
    </div>
  );
};

const InvoiceCardInner = ({ msg, isMe, isFarmer, onPay }) => {
  const cardRef = useRef(null);

  React.useLayoutEffect(() => {
    if (cardRef.current) {
      try {
        const el = cardRef.current;
        const rect = el.getBoundingClientRect();
        const comp = window.getComputedStyle(el);
        
        // Find parent metrics
        let parentEl = el.parentElement;
        const parentData = [];
        while (parentEl && parentData.length < 3) {
          const pComp = window.getComputedStyle(parentEl);
          parentData.push({
            tag: parentEl.tagName,
            className: parentEl.className,
            height: parentEl.clientHeight,
            overflow: pComp.overflow,
            display: pComp.display,
            maxHeight: pComp.maxHeight
          });
          parentEl = parentEl.parentElement;
        }

        const metrics = {
          tag: el.tagName,
          clientHeight: el.clientHeight,
          offsetHeight: el.offsetHeight,
          scrollHeight: el.scrollHeight,
          rect: { width: rect.width, height: rect.height, top: rect.top },
          css: {
            display: comp.display,
            position: comp.position,
            overflow: comp.overflow,
            opacity: comp.opacity,
            visibility: comp.visibility,
            zIndex: comp.zIndex,
            transform: comp.transform,
            flex: comp.flex,
            margin: comp.margin,
            padding: comp.padding,
            height: comp.height,
            maxHeight: comp.maxHeight
          },
          parents: parentData,
          msgData: msg
        };
        
        console.log("[TELEMETRY] DOM Data:", metrics);
      } catch (e) {
        console.error("Telemetry logic error:", e);
      }
    }
  }, [msg]);

  console.log("[InvoiceCardInner] Mounting with msg:", msg);
  try {
    const fullUrl = msg?.imageUrl ? (msg.imageUrl.startsWith("http") ? msg.imageUrl : `http://localhost:5000${msg.imageUrl}`) : null;
    const invoiceId = msg?.orderId || 'PENDING';
    const dateStr = new Date(msg?.timestamp || Date.now()).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' });
    const isDonation = msg?.dealType === 'DONATION' || msg?.type === 'DONATION_REQUEST';
    const cropValue = isDonation ? 0 : (parseFloat(msg?.totalPrice || 0) - parseFloat(msg?.transportCost || 0));
    const negotiatedRate = parseFloat(msg?.kg || 0) > 0 ? (cropValue / parseFloat(msg?.kg)) : 0;
    
    return (
      <div ref={cardRef} style={{ width: '100%', maxWidth: 380, alignSelf: 'center', background: 'white', borderRadius: 24, overflow: 'hidden', border: `1.5px solid var(--golden-yellow)`, boxShadow: '0 8px 24px rgba(0,0,0,0.12)', margin: '16px auto', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
      
      {/* Header */}
      <div style={{ background: 'var(--terracotta-primary)', padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h3 style={{ margin: 0, color: 'white', fontSize: 18, fontWeight: 900, letterSpacing: 1 }}>{msg?.dealType === 'DONATION' ? 'DONATION INVOICE' : 'SMART INVOICE'}</h3>
          <p style={{ margin: 0, color: 'rgba(255,255,255,0.8)', fontSize: 12, fontWeight: 600 }}>Order ID: {invoiceId}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ margin: 0, color: 'white', fontSize: 12, fontWeight: 600 }}>{dateStr}</p>
          <div style={{ background: msg?.status === 'PAID' || (msg?.dealType === 'DONATION' && msg?.transportCost === 0) ? '#4CAF50' : '#FFB300', color: msg?.status === 'PAID' || (msg?.dealType === 'DONATION' && msg?.transportCost === 0) ? 'white' : '#333', padding: '4px 8px', borderRadius: 8, fontSize: 11, fontWeight: 800, display: 'inline-block', marginTop: 4 }}>
            {msg?.dealType === 'DONATION' && msg?.transportCost === 0 ? 'FREE' : (msg?.status || 'PENDING')}
          </div>
        </div>
      </div>

      {/* Item Details */}
      <div style={{ padding: '20px 20px 12px 20px', display: 'flex', gap: 16, alignItems: 'center', borderBottom: '1px dashed #e0e0e0' }}>
        {fullUrl && (
          <img 
            src={fullUrl} 
            alt="Crop" 
            style={{ width: '70px', height: '70px', borderRadius: '14px', objectFit: 'cover', border: '1px solid #f0f0f0' }} 
            onError={(e) => { e.target.onerror = null; e.target.src = 'https://placehold.co/100x100/F9FAF8/D85A38?text=Crop'; }}
          />
        )}
        <div style={{ flex: 1 }}>
          <h4 style={{ margin: '0 0 4px 0', fontSize: 18, fontWeight: 900, color: '#1A1A1A', textTransform: 'capitalize' }}>{msg?.cropName || 'Crop'}</h4>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#666', marginBottom: 2 }}>
            <span>Negotiated Rate:</span>
            <span style={{ fontWeight: 800, color: 'var(--terracotta-primary)' }}>{formatCurrency(negotiatedRate.toFixed(2))}/kg</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#666' }}>
            <span>Quantity Required:</span>
            <span style={{ fontWeight: 800, color: '#333' }}>{msg?.kg} kg</span>
          </div>
        </div>
      </div>

      {/* Logistics Details */}
      <div style={{ padding: '16px 20px', background: '#FAFAFA', borderBottom: '1px dashed #e0e0e0' }}>
        <div style={{ marginBottom: 12 }}>
          <p style={{ margin: '0 0 4px 0', fontSize: 12, fontWeight: 800, color: 'var(--terracotta-primary)', textTransform: 'uppercase' }}>Pickup Location</p>
          <p style={{ margin: 0, fontSize: 13, color: '#444', fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {msg?.pickupAddress || 'Pending'}
          </p>
        </div>
        <div>
          <p style={{ margin: '0 0 4px 0', fontSize: 12, fontWeight: 800, color: '#1565C0', textTransform: 'uppercase' }}>Drop Location</p>
          <p style={{ margin: 0, fontSize: 13, color: '#444', fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {msg?.dealType === 'DONATION' && msg?.transportCost === 0 ? 'Self Pickup' : (msg?.deliveryAddress || 'Pending')}
          </p>
        </div>
        
        {!(msg?.dealType === 'DONATION' && msg?.transportCost === 0) && (
          <div style={{ marginTop: 12, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <div style={{ background: '#FFF', padding: '6px 12px', borderRadius: 8, border: '1px solid #E0E0E0', fontSize: 12, fontWeight: 700, color: '#555' }}>
              🚚 {msg?.vehicleType || 'Vehicle Pending'}
            </div>
            <div style={{ background: '#FFF', padding: '6px 12px', borderRadius: 8, border: '1px solid #E0E0E0', fontSize: 12, fontWeight: 700, color: '#555' }}>
              📍 {msg?.distanceKm !== undefined && msg?.distanceKm !== null ? `${msg.distanceKm} km` : 'N/A'}
            </div>
          </div>
        )}
      </div>

      {/* Price Breakdown */}
      <div style={{ padding: '16px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, color: '#555', marginBottom: 8, fontWeight: 600 }}>
          <span>Crop Subtotal</span>
          <span>{msg?.dealType === 'DONATION' ? '₹0.00 (Donated)' : formatCurrency(cropValue.toFixed(2))}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, color: '#555', marginBottom: 12, fontWeight: 600 }}>
          <span>Transport Fare</span>
          <span>+ {msg?.dealType === 'DONATION' && msg?.transportCost === 0 ? '₹0.00 (Self Pickup)' : formatCurrency((msg?.transportCost || 0).toFixed(2))}</span>
        </div>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 12, borderTop: '2px solid #EEE' }}>
          <span style={{ fontSize: 16, fontWeight: 900, color: '#1A1A1A' }}>GRAND TOTAL</span>
          <span style={{ fontSize: 24, fontWeight: 900, color: 'var(--terracotta-primary)' }}>{msg?.dealType === 'DONATION' && msg?.transportCost === 0 ? '₹0.00' : formatCurrency((msg?.totalPrice || 0).toFixed(2))}</span>
        </div>
      </div>

      {/* Actions */}
      {!isFarmer && msg?.status === 'PENDING' && !(msg?.dealType === 'DONATION' && msg?.transportCost === 0) && (
        <div style={{ padding: '0 20px 20px 20px' }}>
          <button onClick={onPay} style={{ width: '100%', padding: '16px', background: '#4CAF50', color: 'white', fontWeight: 900, borderRadius: '16px', border: 'none', cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, boxShadow: '0 4px 12px rgba(76,175,80,0.3)', transition: 'transform 0.1s' }} onMouseDown={(e) => e.currentTarget.style.transform = 'scale(0.98)'} onMouseUp={(e) => e.currentTarget.style.transform = 'scale(1)'} onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}>
             Proceed to Pay
          </button>
        </div>
      )}
      {!isFarmer && (msg?.status === 'PAID' || (msg?.dealType === 'DONATION' && msg?.transportCost === 0)) && (
        <div style={{ padding: '0 20px 20px 20px' }}>
          <div style={{ width: '100%', padding: '14px', background: '#E8F5E9', color: '#2E7D32', fontWeight: 800, borderRadius: '16px', border: '2px solid #4CAF50', fontSize: 14, textAlign: 'center' }}>
             Payment Completed
          </div>
        </div>
      )}
      {isFarmer && msg?.status === 'PENDING' && !(msg?.dealType === 'DONATION' && msg?.transportCost === 0) && (
         <div style={{ padding: '0 20px 20px 20px' }}>
         <div style={{ width: '100%', padding: '14px', background: '#FFF8E1', color: '#F57C00', fontWeight: 800, borderRadius: '16px', border: '2px dashed #FFB300', fontSize: 13, textAlign: 'center' }}>
            Waiting for buyer payment
         </div>
       </div>
      )}
    </div>
    );
  } catch (renderErr) {
    console.error("[InvoiceCardInner] CRITICAL RENDER CRASH:", renderErr);
    throw renderErr;
  }
};

const InvoiceCard = (props) => (
  <InvoiceCardErrorBoundary>
    <InvoiceCardInner {...props} />
  </InvoiceCardErrorBoundary>
);

// ─── Receipt Card (Compact Premium Design) ─────────────────────────────────
const ReceiptCard = ({ msg, isFarmer }) => {
  console.log("ReceiptCard mounted", msg);
  console.log("ReceiptCard FINAL RETURN");

  try {
    return (
      <div
        className="OfficialReceipt"
        style={{
          width: '100%',
          maxWidth: 320,
          background: 'white',
          borderRadius: 16,
          overflow: 'hidden',
          border: '1px solid #D4AF37', // Thin Golden border
          boxShadow: '0 4px 12px rgba(212, 175, 55, 0.1)',
          position: 'relative',
          fontFamily: 'Inter, sans-serif'
        }}
      >
        {/* Header */}
        <div style={{ padding: '16px 16px 12px', textAlign: 'center', borderBottom: '1px solid rgba(0,0,0,0.05)', background: '#FAFAFA' }}>
          <h3 style={{ margin: 0, color: '#D4AF37', fontSize: 14, fontWeight: 900, letterSpacing: 1.5 }}>OFFICIAL RECEIPT</h3>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 4 }}>
            <CheckCircle size={12} color="#2E7D32" />
            <span style={{ color: '#2E7D32', fontSize: 11, fontWeight: 800, letterSpacing: 0.5 }}>Verified Payment</span>
          </div>
        </div>

        <div style={{ padding: '16px' }}>
          <div style={{ textAlign: 'center', marginBottom: 16 }}>
            <p style={{ margin: '0 0 2px', fontSize: 10, color: C.textMuted, fontWeight: 700, textTransform: 'uppercase' }}>Order ID</p>
            <p style={{ margin: 0, fontSize: 14, fontWeight: 900, color: C.textDark }}>{msg.orderId}</p>
          </div>

          {/* Role-based OTP Display */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {isFarmer ? (
              /* Farmer sees only Pickup OTP */
              <div style={{ background: '#FFF9F5', borderRadius: 10, padding: '12px', textAlign: 'center', border: '1px solid rgba(212, 175, 55, 0.2)' }}>
                <p style={{ fontSize: 10, color: C.terracotta, marginBottom: 4, fontWeight: 800, textTransform: 'uppercase' }}>Pickup OTP</p>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <p style={{ fontWeight: 900, fontSize: 24, color: C.textDark, letterSpacing: 4, margin: 0 }}>
                    {msg.pickupOtp || '----'}
                  </p>
                  <button 
                    onClick={() => {
                      navigator.clipboard.writeText(msg.pickupOtp);
                      // Fallback toast if needed (simplified)
                      notify.success("OTP Copied!");
                    }}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#D4AF37' }}
                    title="Copy Pickup OTP"
                  >
                    <Copy size={16} />
                  </button>
                </div>
                <p style={{ fontSize: 9, color: C.textMuted, fontWeight: 600, margin: '8px 0 0', lineHeight: 1.2 }}>
                  Share this OTP with the driver during pickup.
                </p>
              </div>
            ) : (
              /* Buyer sees only Drop OTP */
              <div style={{ background: '#F5FFF6', borderRadius: 10, padding: '12px', textAlign: 'center', border: '1px solid rgba(46, 125, 50, 0.2)' }}>
                <p style={{ fontSize: 10, color: '#2E7D32', marginBottom: 4, fontWeight: 800, textTransform: 'uppercase' }}>Drop OTP</p>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <p style={{ fontWeight: 900, fontSize: 24, color: C.textDark, letterSpacing: 4, margin: 0 }}>
                    {msg.dropOtp || '----'}
                  </p>
                  <button 
                    onClick={() => {
                      navigator.clipboard.writeText(msg.dropOtp);
                      notify.success("OTP Copied!");
                    }}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2E7D32' }}
                    title="Copy Drop OTP"
                  >
                    <Copy size={16} />
                  </button>
                </div>
                <p style={{ fontSize: 9, color: C.textMuted, fontWeight: 600, margin: '8px 0 0', lineHeight: 1.2 }}>
                  Share this OTP with the driver only after successful delivery.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  } catch (e) {
    console.error("RECEIPT CARD CRASH:", e);
    return <div style={{ padding: 12, background: 'red', color: 'white', borderRadius: 8 }}>Receipt Error: {e.message}</div>;
  }
};

// ─── Chat Bubble (plain text message) ────────────────────────────────────────
const ChatBubble = ({ msg, isMe }) => {
  const time = formatTimestamp(msg.timestamp);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', maxWidth: '72%', alignSelf: isMe ? 'flex-end' : 'flex-start' }}>
      <div style={{
        padding: '10px 14px', borderRadius: isMe ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
        background: isMe ? C.terracottaLight : 'white',
        color: isMe ? 'white' : C.textDark,
        boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        border: isMe ? 'none' : '1px solid #f0e8e0',
        opacity: msg.isOptimistic ? 0.7 : 1
      }}>
        <p style={{ fontSize: 15, fontWeight: 500, lineHeight: 1.4, margin: 0 }}>{msg.text}</p>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 3, marginTop: 3, padding: '0 4px', justifyContent: isMe ? 'flex-end' : 'flex-start' }}>
        <span style={{ fontSize: 10, fontWeight: 600, color: C.textMuted }}>{time}</span>
        {isMe && <CheckCheck size={13} color={C.terracottaLight} />}
      </div>
    </div>
  );
};

// ─── Gemini AI Insight Button + Panel ────────────────────────────────────────
const GeminiInsightBanner = ({ showPanel, setShowPanel, aiCrop, setAiCrop, aiLoc, setAiLoc, aiResult, setAiResult, aiLoading, setAiLoading }) => {
  const handleAnalyze = async () => {
    if (!aiCrop || !aiLoc) return;
    setAiLoading(true);
    try {
      const res = await apiClient.post('/api/chat/analyze-deal', { cropName: aiCrop, location: aiLoc, role: 'farmer' });
      if (res.data?.success) setAiResult(res.data);
    } catch (e) { console.error(e); }
    setAiLoading(false);
  };

  return (
    <>
      {/* Banner */}
      <div onClick={() => setShowPanel(!showPanel)} style={{ margin: '12px 16px 0', padding: '12px 16px', background: 'white', border: `1.5px solid ${C.golden}`, borderRadius: 16, display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', boxShadow: '0 1px 6px rgba(0,0,0,0.06)' }}>
        <Bot size={22} color={C.blue} />
        <span style={{ fontWeight: 800, fontSize: 14, color: C.blue }}>Tap for Gemini AI Market Insight</span>
      </div>

      {/* Expandable Panel */}
      {showPanel && (
        <div style={{ margin: '0 16px', padding: '16px', background: 'white', borderLeft: `1.5px solid ${C.golden}`, borderRight: `1.5px solid ${C.golden}`, borderBottom: `1.5px solid ${C.golden}`, borderRadius: '0 0 16px 16px' }}>
          <p style={{ fontSize: 12, color: C.textMuted, marginBottom: 12 }}>Get AI advice on min & max rates based on product & location.</p>
          <input value={aiCrop} onChange={e => setAiCrop(e.target.value)} placeholder="Product Name (e.g. Tomato)" style={{ width: '100%', padding: '10px 12px', border: `1px solid #e0d5cb`, borderRadius: 12, fontSize: 13, marginBottom: 8, outline: 'none', boxSizing: 'border-box' }} />
          <input value={aiLoc} onChange={e => setAiLoc(e.target.value)} placeholder="Market Location (e.g. Chennai)" style={{ width: '100%', padding: '10px 12px', border: `1px solid #e0d5cb`, borderRadius: 12, fontSize: 13, marginBottom: 12, outline: 'none', boxSizing: 'border-box' }} />
          <button onClick={handleAnalyze} disabled={aiLoading || !aiCrop || !aiLoc} style={{ width: '100%', padding: '11px', background: C.blue, color: 'white', fontWeight: 800, borderRadius: 12, border: 'none', cursor: 'pointer', fontSize: 14, opacity: (aiLoading || !aiCrop || !aiLoc) ? 0.6 : 1 }}>
            {aiLoading ? 'Analyzing...' : 'Analyze Market Rates'}
          </button>
          {aiResult && (
            <div style={{ marginTop: 12, padding: 12, background: C.peach, borderRadius: 12, border: `1px solid #f0e0cc` }}>
              <p style={{ fontWeight: 700, fontSize: 13, color: C.textDark, marginBottom: 6 }}>Recommended Price Band:</p>
              <p style={{ fontWeight: 900, fontSize: 22, color: C.green }}>{formatCurrency(aiResult.minPrice)} – {formatCurrency(aiResult.maxPrice)}<span style={{ fontSize: 12, fontWeight: 600, color: C.textMuted }}> /kg</span></p>
              <p style={{ fontSize: 12, color: C.textMuted, marginTop: 6, lineHeight: 1.5 }}>{aiResult.reason}</p>
            </div>
          )}
        </div>
      )}
    </>
  );
};

// ─── Counter Form (Matches Android UI) ──────────────────────────────────────────
const CounterForm = ({ onClose, onSubmit, targetPrice, setTargetPrice, reason, setReason, baseMsg }) => (
  <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 99999, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
    <div style={{ backgroundColor: '#ffffff', borderRadius: '24px', boxShadow: '0 10px 25px rgba(0,0,0,0.2)', padding: '24px', width: '90%', maxWidth: '400px', position: 'relative', display: 'flex', flexDirection: 'column', border: '1px solid var(--golden-yellow)' }} onClick={(e) => e.stopPropagation()}>
      <h2 style={{ margin: '0 0 16px 0', fontSize: '20px', fontWeight: 800, color: 'var(--terracotta-primary)', textAlign: 'center' }}>Send Counter Offer</h2>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
        <span style={{ fontSize: '13px', color: '#666' }}>Market Rate</span>
        <span style={{ fontSize: '13px', fontWeight: 700, color: '#999', textDecoration: 'line-through' }}>{formatCurrency((parseFloat(baseMsg?.basePrice) || 0).toFixed(2))} /kg</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
        <span style={{ fontSize: '13px', color: '#666' }}>Original Offer</span>
        <span style={{ fontSize: '13px', fontWeight: 700, color: '#1A1A1A' }}>{formatCurrency((parseFloat(baseMsg?.targetPrice) || 0).toFixed(2))} /kg</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px' }}>
        <span style={{ fontSize: '13px', color: '#666' }}>Required Quantity</span>
        <span style={{ fontSize: '13px', fontWeight: 700, color: '#1A1A1A' }}>{baseMsg?.kg} kg</span>
      </div>

      <div style={{ marginBottom: '16px' }}>
        <label style={{ fontSize: '12px', color: '#7A7A7A', fontWeight: 700, marginBottom: '4px', display: 'block' }}>Counter Price (₹/kg)</label>
        <input type="number" placeholder="Enter new price" value={targetPrice} onChange={e => setTargetPrice(e.target.value)} style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid var(--golden-yellow)', outline: 'none', fontSize: '14px', boxSizing: 'border-box' }} />
      </div>

      <div style={{ marginBottom: '16px' }}>
        <label style={{ fontSize: '12px', color: '#7A7A7A', fontWeight: 700, marginBottom: '4px', display: 'block' }}>Reason (Optional)</label>
        <input placeholder="e.g. Quality is premium" value={reason} onChange={e => setReason(e.target.value)} style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #e5e7eb', outline: 'none', fontSize: '14px', boxSizing: 'border-box' }} />
      </div>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px', backgroundColor: '#F9FAF8', borderRadius: '12px', marginBottom: '24px' }}>
        <span style={{ fontSize: '14px', fontWeight: 800, color: '#333' }}>New Total Value</span>
        <span style={{ fontSize: '14px', fontWeight: 900, color: '#2E7D32' }}>{formatCurrency((parseFloat(targetPrice || 0) * parseFloat(baseMsg?.kg || 0)).toFixed(2))}</span>
      </div>

      <div style={{ display: 'flex', gap: '12px', width: '100%' }}>
        <button onClick={onClose} style={{ flex: 1, padding: '16px', backgroundColor: '#f5f5f5', color: '#333', borderRadius: '16px', fontWeight: 'bold', border: 'none', cursor: 'pointer' }}>Cancel</button>
        <button onClick={onSubmit} style={{ flex: 1, padding: '16px', backgroundColor: 'var(--terracotta-primary)', color: 'white', borderRadius: '16px', fontWeight: 'bold', border: 'none', cursor: 'pointer' }}>Send Counter</button>
      </div>
    </div>
  </div>
);

// ─── Location Popup (Replaces old InvoiceForm) ────────────────────────────────
const LocationPopup = ({ onClose, onSubmit, locAddress, setLocAddress, locLat, setLocLat, locLng, setLocLng, fetchLocation, isFarmer }) => (
  <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 99999, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
    <div style={{ backgroundColor: '#ffffff', borderRadius: '24px', boxShadow: '0 10px 25px rgba(0,0,0,0.2)', padding: '24px', width: '90%', maxWidth: '400px', position: 'relative', display: 'flex', flexDirection: 'column', border: '1px solid var(--golden-yellow)' }} onClick={(e) => e.stopPropagation()}>
      <h2 style={{ margin: '0 0 16px 0', fontSize: '20px', fontWeight: 800, color: 'var(--terracotta-primary)', textAlign: 'center' }}>Set {isFarmer ? 'Pickup' : 'Drop'} Location</h2>
      
      <button onClick={fetchLocation} style={{ width: '100%', padding: '12px', background: '#f3f4f6', color: '#1565C0', fontWeight: 800, borderRadius: '12px', border: 'none', cursor: 'pointer', fontSize: '14px', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
        <MapPin size={18} /> Auto Fetch Live Location
      </button>

      <div style={{ marginBottom: '16px' }}>
        <label style={{ fontSize: '12px', color: '#7A7A7A', fontWeight: 700, marginBottom: '4px', display: 'block' }}>{isFarmer ? 'Pickup' : 'Delivery'} Address</label>
        <input placeholder="Enter complete address" value={locAddress} onChange={e => setLocAddress(e.target.value)} style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid var(--golden-yellow)', outline: 'none', fontSize: '14px', boxSizing: 'border-box' }} />
      </div>

      <div style={{ display: 'flex', gap: '12px', marginBottom: '24px' }}>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: '12px', color: '#7A7A7A', fontWeight: 700, marginBottom: '4px', display: 'block' }}>Latitude</label>
          <input value={locLat} onChange={e => setLocLat(e.target.value)} placeholder="e.g. 13.0827" style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #e5e7eb', backgroundColor: 'white', fontSize: '14px', boxSizing: 'border-box', color: '#333' }} />
        </div>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: '12px', color: '#7A7A7A', fontWeight: 700, marginBottom: '4px', display: 'block' }}>Longitude</label>
          <input value={locLng} onChange={e => setLocLng(e.target.value)} placeholder="e.g. 80.2707" style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #e5e7eb', backgroundColor: 'white', fontSize: '14px', boxSizing: 'border-box', color: '#333' }} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', width: '100%' }}>
        <button onClick={onClose} style={{ flex: 1, padding: '16px', backgroundColor: '#f5f5f5', color: '#333', borderRadius: '16px', fontWeight: 'bold', border: 'none', cursor: 'pointer' }}>Cancel</button>
        <button onClick={onSubmit} disabled={!locAddress || !locLat || !locLng} style={{ flex: 1, padding: '16px', backgroundColor: (!locAddress || !locLat || !locLng) ? '#ccc' : 'var(--terracotta-primary)', color: 'white', borderRadius: '16px', fontWeight: 'bold', border: 'none', cursor: (!locAddress || !locLat || !locLng) ? 'not-allowed' : 'pointer' }}>Confirm</button>
      </div>
      {(!locAddress || !locLat || !locLng) && (
        <p style={{ color: '#ef4444', fontSize: '12px', textAlign: 'center', marginTop: '8px', fontWeight: 600 }}>Please fill all fields to confirm.</p>
      )}
    </div>
  </div>
);

const inputStyle = { padding: '9px 12px', border: '1px solid #e0d5cb', borderRadius: 10, fontSize: 13, outline: 'none', width: '100%', boxSizing: 'border-box' };
const iconBtnStyle = { background: 'none', border: 'none', cursor: 'pointer', padding: 8, display: 'flex', alignItems: 'center', justifyContent: 'center' };

const MessageRenderer = ({ msg, isMe, isFarmer, onPay, onAccept, onDeclineDonation, onCounter, onDecline, onWithdraw, onLocation, onConfirmDelivery }) => {
  if (!msg || !msg.id || !msg.type) return null;

  let content = null;
  const isInvoiceVariant = ['INVOICE_CARD', 'INVOICE', 'invoice'].includes(msg.type);
  
  if (isInvoiceVariant) {
    if (msg.dealType === 'DONATION' || msg.type === 'DONATION_REQUEST') {
      content = <DonationInvoiceCard msg={msg} isMe={isMe} isFarmer={isFarmer} onPay={() => onPay(msg)} />;
    } else {
      content = <InvoiceCard msg={msg} isMe={isMe} isFarmer={isFarmer} onPay={() => onPay(msg)} />;
    }
  } else if (msg.type === 'DONATION_ORDER_CARD') {
    content = <DonationOrderCard msg={msg} isMe={isMe} isFarmer={isFarmer} onAcceptDonation={onAccept} onDeclineDonation={onDeclineDonation} onSelectLogistics={onConfirmDelivery} onLocation={onLocation} />;
  } else if (msg.type === 'DONATION_EVENT') {
    content = <SystemMessageCard msg={msg} />;
  } else if (msg.type === 'RECEIPT_CARD') {
    content = <ReceiptCard msg={msg} isFarmer={isFarmer} />;
  } else if (['RESCUE_CARD', 'DONATION_REQUEST', 'COUNTER_CARD', 'INQUIRY_CARD'].includes(msg.type)) {
    content = <DealRequestCard msg={msg} isMe={isMe} isFarmer={isFarmer} onAccept={() => onAccept(msg)} onCounter={() => onCounter(msg)} onDecline={() => onDecline(msg)} onWithdraw={() => onWithdraw(msg)} onLocation={() => onLocation(msg)} />;
  } else if (msg.type === 'LOGISTICS_CHOICE' || msg.type === 'DONATION_DELIVERY') {
    content = <LogisticsChoiceCard msg={msg} isMe={isMe} onSelectLogistics={onConfirmDelivery} />;
  } else if (msg.type === 'SYSTEM_MESSAGE') {
    content = <SystemMessageCard msg={msg} />;
  } else if (msg.type === 'AUDIO_CARD') {
    content = <AudioMessageCard msg={msg} isMe={isMe} />;
  } else if (msg.type === 'TEXT') {
    content = <ChatBubble msg={msg} isMe={isMe} />;
  } else {
    content = (
      <div style={{ padding: '12px', background: '#ffebee', color: '#c62828', borderRadius: '12px', border: '1px solid #ef5350', fontSize: '12px', fontWeight: 'bold' }}>
        [Developer Error] Unknown Message Type: "{msg?.type || 'UNDEFINED'}"
      </div>
    );
  }

  // WhatsApp-style alignment wrapper
  return (
    <div style={{ display: 'flex', width: '100%', justifyContent: isMe ? 'flex-end' : 'flex-start', marginBottom: '8px' }}>
      <div style={{ maxWidth: '75%', width: '100%', display: 'flex', flexDirection: 'column' }}>
        {content}
      </div>
    </div>
  );
};

// ─── MAIN DEALS COMPONENT ─────────────────────────────────────────────────────
const Deals = () => {
  const { email: routeParam } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isFarmer = user?.role === 'farmer';

  // ── State ──
  const [chatThreads, setChatThreads] = useState([]);
  const [isLoadingThreads, setIsLoadingThreads] = useState(true);
  const [messages, setMessages] = useState([]);
  const [messageInput, setMessageInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearchQuery, setDebouncedSearchQuery] = useState('');
  const [showPayment, setShowPayment] = useState(false);
  const [pendingInvoice, setPendingInvoice] = useState(null);
  
  const [showLocationForm, setShowLocationForm] = useState(false);
  const [locTarget, setLocTarget] = useState(null);
  const [locAddress, setLocAddress] = useState('');
  const [locLat, setLocLat] = useState('');
  const [locLng, setLocLng] = useState('');

  const [showCounterForm, setShowCounterForm] = useState(false);
  const [counterTarget, setCounterTarget] = useState(null);
  const [counterPrice, setCounterPrice] = useState('');
  const [counterReason, setCounterReason] = useState('');
  
  const [showFarmerPickupDialog, setShowFarmerPickupDialog] = useState(false);
  const [farmerPickupTarget, setFarmerPickupTarget] = useState(null);
  const [showNgoDeliveryDialog, setShowNgoDeliveryDialog] = useState(false);
  const [ngoDeliveryTarget, setNgoDeliveryTarget] = useState(null);
  const [tempAddress, setTempAddress] = useState("");
  
  const [showAIPanel, setShowAIPanel] = useState(false);
  const [aiCrop, setAiCrop] = useState('');
  const [aiLoc, setAiLoc] = useState('');
  const [aiResult, setAiResult] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  
  const [searchResults, setSearchResults] = useState([]);
  const [isSearchingProfiles, setIsSearchingProfiles] = useState(false);
  const [profileCache, setProfileCache] = useState({});

  const messagesEndRef = useRef(null);

  // ── Debounce search ──
  useEffect(() => {
    const h = setTimeout(() => setDebouncedSearchQuery(searchQuery), 300);
    return () => clearTimeout(h);
  }, [searchQuery]);

  // ── Chat ID derivation ──
  const isChatIdRoute = routeParam?.includes('_');
  const activeChatId = isChatIdRoute 
    ? routeParam 
    : (routeParam && user?.email ? (user.email < routeParam ? `${user.email}_${routeParam}` : `${routeParam}_${user.email}`) : null);
    
  const currentChatForEmail = chatThreads.find(t => t.id === activeChatId);
  const activeChatEmail = isChatIdRoute 
    ? (currentChatForEmail ? (isFarmer ? currentChatForEmail.userEmail : currentChatForEmail.farmerEmail) : (routeParam ? routeParam.split('_').find(e => e !== user?.email && e.includes('@')) : ''))
    : routeParam;

  const { withdrawDeal, declineDeal, acceptDeal, submitCounter, updateLocation, generateInvoice } = useDealEngine(activeChatId, user?.email, activeChatEmail);

  // ── Scroll to bottom on new messages ──
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  // ── Load profile image for a given email (fetched once, cached) ──
  const fetchProfile = useCallback(async (email) => {
    if (!email || profileCache[email] !== undefined) return;
    setProfileCache(prev => ({ ...prev, [email]: null })); // mark as loading
    try {
      const res = await apiClient.get(`/api/profile/fetch-details?email=${encodeURIComponent(email)}`);
      setProfileCache(prev => ({ ...prev, [email]: res.data?.profile_image_url || '' }));
    } catch { setProfileCache(prev => ({ ...prev, [email]: '' })); }
  }, [profileCache]);

  // ── Chat threads listener ──
  useEffect(() => {
    if (!user?.email) return;
    setIsLoadingThreads(true);
    const isActuallyFarmer = user?.role?.toLowerCase() === 'farmer';
    const queryField = isActuallyFarmer ? 'farmerEmail' : 'userEmail';
    const q = query(collection(db, 'chats'), where(queryField, '==', user.email));
    const unsub = onSnapshot(q, (snap) => {
      const threads = snap.docs.map(d => ({ id: d.id, ...d.data() })).sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
      setChatThreads(threads);
      setIsLoadingThreads(false);
      threads.forEach(t => {
        const other = isActuallyFarmer ? t.userEmail : t.farmerEmail;
        if (other) fetchProfile(other);
      });
    }, (err) => { console.error('Chat sync error:', err); setIsLoadingThreads(false); });
    return () => unsub();
  }, [user?.email, user?.role]);

  // ── Fetch profile for active chat partner ──
  useEffect(() => {
    if (activeChatEmail) fetchProfile(activeChatEmail);
  }, [activeChatEmail]);

  // ── Messages & Orders listener ──
  useEffect(() => {
    if (!activeChatId || !user?.email) { setMessages([]); return; }
    const fieldToUpdate = isFarmer ? 'unreadCountFarmer' : 'unreadCountUser';
    updateDoc(doc(db, 'chats', activeChatId), { [fieldToUpdate]: 0 }).catch(() => {});
    
    let currentMsgs = [];
    let currentOrders = [];
    
    const mergeAndSet = () => {
      let combined = [...currentMsgs, ...currentOrders].sort((a, b) => a.timestamp - b.timestamp);
      
      const dealTypes = ["deal", "DONATION_REQUEST", "INVOICE_CARD", "RECEIPT_CARD", "COUNTER_CARD", "LOGISTICS_CHOICE", "DECLINED_CARD", "DONATION_ORDER_CARD"];
      const dealCards = combined.filter(m => dealTypes.includes(m.type) || m.isDonationOrderCard);
      
      const dealMap = {};
      dealCards.forEach(m => {
        const id = m.dealId || m.orderId || m.id;
        if (!dealMap[id] || m.timestamp > dealMap[id].timestamp) {
          dealMap[id] = m;
        }
      });
      
      const validDealMsgs = new Set(Object.values(dealMap));
      
      combined = combined.filter(m => {
        if (dealTypes.includes(m.type) || m.isDonationOrderCard) {
          return validDealMsgs.has(m);
        }
        return true;
      });
      
      // Final deduplication safety net to strictly prevent React duplicate key errors
      const uniqueCombined = [];
      const seenIds = new Set();
      combined.forEach(m => {
        if (!seenIds.has(m.id)) {
          seenIds.add(m.id);
          uniqueCombined.push(m);
        }
      });
      
      setMessages(uniqueCombined);
    };

    const qMsgs = query(collection(db, 'chats', activeChatId, 'messages'), orderBy('timestamp', 'asc'));
    const unsubMsgs = onSnapshot(qMsgs, (snap) => {
      currentMsgs = snap.docs.map(d => ({ id: d.id, ...d.data() }));
      mergeAndSet();
    });

    const qOrders = query(collection(db, 'orders'), where('chatId', '==', activeChatId));
    const unsubOrders = onSnapshot(qOrders, (snap) => {
      currentOrders = snap.docs.map(d => {
        return {
          id: d.id,
          isDonationOrderCard: true,
          ...d.data(),
          type: "DONATION_ORDER_CARD"
        };
      });
      mergeAndSet();
    });

    return () => { unsubMsgs(); unsubOrders(); };
  }, [activeChatId, user?.email, isFarmer]);

  // ── Profile search ──
  useEffect(() => {
    if (!debouncedSearchQuery.trim()) { setSearchResults([]); return; }
    const doSearch = async () => {
      setIsSearchingProfiles(true);
      try {
        const targetRole = isFarmer ? 'user' : 'farmer';
        const q = query(collection(db, 'users'), where('role', '==', targetRole));
        const snap = await getDocs(q);
        const ql = debouncedSearchQuery.trim().toLowerCase();
        const results = [];
        snap.forEach(d => {
          const data = d.data();
          const name = data.name || nameFromEmail(data.email);
          if (name.toLowerCase().includes(ql) || data.email.toLowerCase().includes(ql)) {
            results.push({ email: data.email, name, role: data.role, profileImageUrl: data.profileImageUrl });
          }
        });
        setSearchResults(results);
      } catch (e) { console.error('Search error:', e); }
      setIsSearchingProfiles(false);
    };
    doSearch();
  }, [debouncedSearchQuery, isFarmer]);

  // ── Send helpers ──
  const updateChatHeader = async (lastMsg) => {
    if (!activeChatId) return;
    await setDoc(doc(db, 'chats', activeChatId), {
      lastMessage: lastMsg, timestamp: Date.now(),
      farmerEmail: isFarmer ? user?.email : activeChatEmail,
      userEmail: isFarmer ? activeChatEmail : user?.email,
      [isFarmer ? 'unreadCountUser' : 'unreadCountFarmer']: 1
    }, { merge: true });
  };

  const handleSendMessage = async (e) => {
    e?.preventDefault();
    if (!messageInput.trim() || !activeChatId) return;
    const text = messageInput;
    setMessageInput('');
    const opt = { id: `temp-${Date.now()}`, senderId: user?.email, receiverId: activeChatEmail, type: 'TEXT', text, timestamp: Date.now(), isOptimistic: true };
    setMessages(prev => [...prev, opt]);
    try {
      await addDoc(collection(db, 'chats', activeChatId, 'messages'), { senderId: user?.email, receiverId: activeChatEmail, type: 'TEXT', text, timestamp: Date.now() });
      await updateChatHeader(text);
    } catch { setMessages(prev => prev.filter(m => m.id !== opt.id)); }
  };

  const triggerInvoiceGeneration = async (msgId, mergedData) => {
    const isDonation = mergedData.dealType === 'DONATION' || mergedData.type === 'DONATION_REQUEST';
    const p = isDonation ? 0 : parseFloat(mergedData.targetPrice || mergedData.basePrice);
    const kg = parseFloat(mergedData.kg);
    const origId = mergedData.dealId || mergedData.id || msgId;
    
    let tc = 0;
    let vehicle = 'Bike';
    let dist = 0;

    try {
      if (isDonation) {
        await apiClient.post('/api/deals/transition', { chatId: activeChatId, dealId: origId, status: 'TRANSPORT_SELECTED_DELIVERY' });
      }
      const res = await apiClient.post('/api/logistics/calculate-fare', {
        pickupLat: mergedData.pickupLat,
        pickupLon: mergedData.pickupLon,
        dropLat: mergedData.dropLat,
        dropLon: mergedData.dropLon,
        weight: kg
      });
      if (res.data) {
        tc = res.data.suggestedFare || 0;
        vehicle = res.data.vehicleType || 'Bike';
        dist = res.data.distanceKm || 0;
      }
    } catch (e) {
      console.error("Failed to calculate fare from logistics API:", e);
      notify.error("Failed to calculate logistics fare. Invoice generation aborted.");
      throw e;
    }
    
    const tot = (p * kg) + tc;
    
    await generateInvoice(origId, {
      cropName: mergedData.cropName, 
      kg, 
      basePrice: p, 
      transportCost: tc, 
      totalPrice: tot,
      imageUrl: mergedData.imageUrl || '',
      pickupAddress: mergedData.pickupAddress, 
      deliveryAddress: mergedData.dropAddress,
      pickupLat: parseFloat(mergedData.pickupLat), pickupLon: parseFloat(mergedData.pickupLon),
      dropLat: parseFloat(mergedData.dropLat), dropLon: parseFloat(mergedData.dropLon),
      farmerName: isFarmer ? (user?.name || nameFromEmail(user?.email)) : (mergedData.farmerName || 'Farmer'), 
      vehicleType: vehicle,
      distanceKm: dist,
      dealId: origId
    });
  };

  const submitLocation = async () => {
    if (!locAddress || !locLat || !locLng) return;
    
    if (locTarget.type === 'DONATION_ORDER_CARD') {
      const origId = locTarget.orderId || locTarget.id;
      if (isFarmer) {
        try {
          const locData = {
            pickupAddress: locAddress,
            pickupLat: parseFloat(locLat),
            pickupLon: parseFloat(locLng),
            status: 'WAITING_FOR_TRANSPORT_SELECTION'
          };
          
          await updateDoc(doc(db, 'orders', origId), locData);
          await updateDoc(doc(db, 'chats', activeChatId, 'messages', origId), locData);
          
          await addDoc(collection(db, 'chats', activeChatId, 'messages'), {
            type: 'DONATION_EVENT',
            event: 'FARMER_SET_PICKUP',
            dealId: origId,
            timestamp: Date.now()
          });
          
          setShowLocationForm(false);
          setLocAddress(''); setLocLat(''); setLocLng('');
        } catch (e) {
          notify.error('Failed to set pickup location');
        }
      } else {
        try {
          const fareRes = await apiClient.post('/api/logistics/calculate-fare', {
            weight: parseFloat(locTarget.kg || 0),
            pickupLat: parseFloat(locTarget.pickupLat || 0),
            pickupLon: parseFloat(locTarget.pickupLon || 0),
            dropLat: parseFloat(locLat),
            dropLon: parseFloat(locLng)
          });
          const fareData = fareRes.data;

          await apiClient.post('/api/orders/confirm-donation-logistics', {
            orderId: origId,
            ngoEmail: user?.email,
            vehicleType: fareData.vehicleType || 'GroWise Delivery',
            transportFare: fareData.suggestedFare || 150,
            dropAddress: locAddress,
            dropLat: parseFloat(locLat),
            dropLon: parseFloat(locLng),
            distanceKm: fareData.distanceKm || 5.0,
            baseFare: fareData.baseFare || 0.0,
            perKmRate: fareData.perKmRate || 0.0,
            weightCharge: fareData.weightCharge || 0.0,
            capacity: fareData.capacity || ''
          });
          setShowLocationForm(false);
          setLocAddress(''); setLocLat(''); setLocLng('');
        } catch (e) {
          notify.error(e.response?.data?.error || e.response?.data?.message || 'Failed to select GroWise Delivery');
        }
      }
      return;
    }

    const locData = isFarmer 
      ? { pickupAddress: locAddress, pickupLat: locLat, pickupLon: locLng }
      : { dropAddress: locAddress, dropLat: locLat, dropLon: locLng };
      
    await updateLocation(locTarget.id, locData);
    
    const merged = { ...locTarget, ...locData };
    if (merged.pickupLat && merged.dropLat) {
      await triggerInvoiceGeneration(locTarget.id, merged);
    } else if (isFarmer && merged.pickupLat && !merged.dropLat && (merged.dealType === 'DONATION' || merged.type === 'DONATION_REQUEST')) {
      const origId = merged.dealId || merged.id || locTarget.id;
      try {
        await apiClient.post('/api/deals/transition', {
          chatId: activeChatId, dealId: origId, status: 'PICKUP_ADDRESS_SUBMITTED', type: 'LOGISTICS_CHOICE',
          cropName: merged.cropName, kg: merged.kg,
          pickupAddress: merged.pickupAddress, pickupLat: merged.pickupLat, pickupLon: merged.pickupLon
        });
      } catch (err) { console.error("Failed to transition:", err); }
    }
    
    setShowLocationForm(false);
    setLocAddress(''); setLocLat(''); setLocLng('');
  };

  const handleAcceptDeal = async (msg) => {
    if (msg.type === 'DONATION_ORDER_CARD') {
      try {
        await apiClient.post('/api/orders/accept-donation', { orderId: msg.orderId || msg.id, farmerEmail: user?.email });
      } catch (err) {
        notify.error(err.response?.data?.error || 'Failed to accept donation');
      }
      return;
    }
    await acceptDeal(msg);
    if (msg.pickupLat && msg.dropLat) {
      // If locations already exist, immediately auto-generate invoice
      await triggerInvoiceGeneration(msg.id, msg);
    } else {
      handleOpenLocation(msg);
    }
  };
  
  const handleDeclineDonation = async (msg) => {
    try {
      await apiClient.post('/api/orders/reject-donation', { orderId: msg.orderId || msg.id, farmerEmail: user?.email });
    } catch (err) {
      notify.error(err.response?.data?.error || 'Failed to decline donation');
    }
  };
  
  const handleOpenLocation = (msg) => {
    setLocTarget(msg);
    setShowLocationForm(true);
  };

  const handleOpenCounter = (msg) => {
    setCounterTarget(msg);
    setCounterPrice(msg.targetPrice || msg.basePrice);
    setShowCounterForm(true);
  };

  const handleSubmitCounter = async () => {
    if (!counterPrice || isNaN(counterPrice)) { notify.info('Enter a valid price'); return; }
    try {
      await addDoc(collection(db, 'chats', activeChatId, 'messages'), {
        senderId: user?.email, receiverId: activeChatEmail, type: 'COUNTER_CARD',
        cropName: counterTarget.cropName, kg: counterTarget.kg, basePrice: counterTarget.basePrice,
        targetPrice: parseFloat(counterPrice), imageUrl: counterTarget.imageUrl || '',
        reason: counterReason || '',
        status: 'PENDING', timestamp: Date.now()
      });
      
      try {
        await updateDoc(doc(db, 'chats', activeChatId, 'messages', counterTarget.id), { status: 'COUNTERED' });
      } catch(e) {}
      
      setShowCounterForm(false);
      setCounterReason('');
    } catch (error) {
      notify.error('Failed to send counter offer. Please try again.');
    }
  };

  const fetchLocation = () => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          const lat = pos.coords.latitude;
          const lon = pos.coords.longitude;
          setLocLat(lat.toString());
          setLocLng(lon.toString());
          try {
            const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`);
            const data = await res.json();
            if (data && data.display_name) {
              setLocAddress(data.display_name);
            } else {
              setLocAddress(`Lat: ${lat.toFixed(4)}, Lon: ${lon.toFixed(4)} (GPS)`);
            }
          } catch (e) {
            setLocAddress(`Lat: ${lat.toFixed(4)}, Lon: ${lon.toFixed(4)} (GPS)`);
          }
        },
        () => notify.error('Unable to fetch current location.')
      );
    } else {
      notify.error('Unable to fetch current location.');
    }
  };

  // ── Derived data ──
  const filteredThreads = useMemo(() => {
    return chatThreads.filter(thread => {
      const otherEmail = isFarmer ? thread.userEmail : thread.farmerEmail;
      const otherName = isFarmer ? (thread.userName || nameFromEmail(otherEmail)) : (thread.farmerName || nameFromEmail(otherEmail));
      if (debouncedSearchQuery && !otherName.toLowerCase().includes(debouncedSearchQuery.toLowerCase())) return false;
      return true;
    });
  }, [chatThreads, debouncedSearchQuery, isFarmer]);

  const isEmptyState = chatThreads.length === 0 && !isLoadingThreads && !searchQuery.trim();
  const currentChat = chatThreads.find(t => isFarmer ? t.userEmail === activeChatEmail : t.farmerEmail === activeChatEmail);
  const otherName = currentChat
    ? (isFarmer ? (currentChat.userName || nameFromEmail(activeChatEmail)) : (currentChat.farmerName || nameFromEmail(activeChatEmail)))
    : nameFromEmail(activeChatEmail);
  const otherProfileUrl = profileCache[activeChatEmail] || '';

  const handleConfirmDelivery = async (msg, deliveryType) => {
    if (msg.type === 'DONATION_ORDER_CARD') {
      if (deliveryType === 'SELF') {
        try {
          await apiClient.post('/api/orders/confirm-donation-logistics', {
            orderId: msg.orderId || msg.id,
            ngoEmail: user?.email,
            vehicleType: 'Self Pickup',
            transportFare: 0,
            dropAddress: 'Self Pickup',
            dropLat: 0,
            dropLon: 0,
            distanceKm: 0
          });
        } catch (e) {
          notify.error(e.response?.data?.error || 'Failed to select Self Service');
        }
      } else {
        handleOpenLocation(msg);
      }
      return;
    }

    if (deliveryType === 'SELF') {
      try {
        const isDonation = msg.dealType === 'DONATION' || msg.type === 'DONATION_REQUEST';
        const origId = msg.dealId || msg.id;
        
        // Let the backend handle everything (wallet escrow, inventory deduct, order creation, and RECEIPT_CARD conversion)
        const res = await apiClient.post('/api/orders/create_order', {
          farmerEmail: activeChatEmail, userEmail: user.email,
          cropName: msg.cropName, weightKg: msg.kg,
          cropValue: 0, transportFare: 0, totalPaid: 0,
          isDonation: true,
          pickupAddress: msg.pickupAddress || 'Farm', dropAddress: 'Self Pickup',
          pickupLat: msg.pickupLat || 0.0, pickupLon: msg.pickupLon || 0.0, dropLat: 0.0, dropLon: 0.0,
          distanceKm: 0.0, vehicleType: 'Self Pickup',
          chatId: activeChatId, dealId: origId, itemId: msg.itemId || ''
        });

        setShowDonationDialog(false);
        setPendingAddressMsg(null);
      } catch (e) {
        console.error("Error creating self service order", e);
      }
    } else {
      handleOpenLocation(msg);
    }
  };




  return (
    <div style={{ width: '100%', height: '100vh', display: 'flex', background: C.peach, overflow: 'hidden', position: 'relative' }}>

      {/* ── Payment overlay ── */}
      {showPayment && pendingInvoice && (
        <FullScreenPayment pendingInvoice={pendingInvoice} farmerEmail={activeChatEmail} chatId={activeChatId} onComplete={() => { setShowPayment(false); setPendingInvoice(null); }} />
      )}

      {/* ── LEFT PANE: Chat list ── */}
      <div style={{
        display: 'flex', flexDirection: 'column', flexShrink: 0, background: C.peach,
        // Empty state: full width. Active chat on mobile: hidden. Otherwise sidebar.
        width: isEmptyState ? '100%' : undefined,
        flex: isEmptyState ? 'auto' : '0 0 340px',
        borderRight: isEmptyState ? 'none' : '1px solid #f0e0d0',
      }}
        className={`deals-left-pane${activeChatEmail && !isEmptyState ? ' chat-open' : ''}`}
      >
        {/* Header */}
        <div style={{ padding: '24px 24px 12px' }}>
          <h2 style={{ fontSize: 26, fontWeight: 900, color: C.terracotta, margin: 0, marginBottom: 16 }}>
            {isFarmer ? 'Active Deals' : 'Deal Inbox'}
          </h2>
          {/* Search */}
          <div style={{ position: 'relative' }}>
            <Search size={17} color={C.terracotta} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)' }} />
            <input
              type="text" placeholder="Search..." value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={{ width: '100%', padding: '11px 14px 11px 42px', background: 'white', border: `1.5px solid transparent`, borderRadius: 20, outline: 'none', fontSize: 14, boxSizing: 'border-box', boxShadow: '0 1px 4px rgba(0,0,0,0.06)', color: C.textDark }}
              onFocus={e => e.target.style.borderColor = C.golden}
              onBlur={e => e.target.style.borderColor = 'transparent'}
            />
          </div>
        </div>

        {/* List body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '8px 16px 16px' }}>
          {searchQuery.trim() ? (
            isSearchingProfiles ? (
              <p style={{ textAlign: 'center', color: C.textMuted, fontWeight: 600, fontSize: 13, padding: 20 }}>Searching...</p>
            ) : searchResults.length === 0 ? (
              <p style={{ textAlign: 'center', color: C.textMuted, fontWeight: 600, fontSize: 13, padding: 20 }}>No profiles found</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <p style={{ fontSize: 11, fontWeight: 700, color: C.textMuted, textTransform: 'uppercase', letterSpacing: 1, marginLeft: 4 }}>People</p>
                {searchResults.map(p => (
                  <div key={p.email} onClick={() => navigate(`/deals/${p.email}`)} style={threadCardStyle(false)}>
                    <Avatar name={p.name} profileUrl={p.profileImageUrl} size={48} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <p style={{ fontWeight: 800, color: C.textDark, fontSize: 15, margin: 0 }}>{p.name}</p>
                      <p style={{ fontSize: 12, color: C.textMuted, margin: '2px 0 0', textTransform: 'capitalize' }}>{p.role}</p>
                    </div>
                  </div>
                ))}
              </div>
            )
          ) : isLoadingThreads ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 200 }}>
              <div style={{ width: 36, height: 36, border: `3px solid ${C.terracotta}`, borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
              <p style={{ color: C.terracotta, fontWeight: 700, marginTop: 12, fontSize: 13 }}>Syncing market deals...</p>
              <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
            </div>
          ) : isEmptyState ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 280, textAlign: 'center', padding: 24 }}>
              <div style={{ width: 96, height: 96, borderRadius: '50%', background: 'white', border: `2px solid ${C.golden}`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 20 }}>
                <span style={{ fontSize: 42 }}>💬</span>
              </div>
              <p style={{ fontWeight: 900, fontSize: 20, color: C.terracotta, margin: 0 }}>No Active Deals</p>
              <p style={{ fontSize: 13, color: C.textMuted, marginTop: 8, lineHeight: 1.6 }}>Your negotiation history and<br />live chats will appear here.</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {filteredThreads.map(thread => {
                const otherEmail = isFarmer ? thread.userEmail : thread.farmerEmail;
                if (!otherEmail) return null;
                const name = isFarmer ? (thread.userName || nameFromEmail(otherEmail)) : (thread.farmerName || nameFromEmail(otherEmail));
                const unread = isFarmer ? (thread.unreadCountFarmer || 0) : (thread.unreadCountUser || 0);
                const isSelected = activeChatId === thread.id;
                const picUrl = profileCache[otherEmail] || '';
                return (
                  <div key={thread.id} onClick={() => navigate(`/deals/${thread.id}`)} style={threadCardStyle(isSelected)}>
                    <Avatar name={name} profileUrl={picUrl} size={50} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 3 }}>
                        <span style={{ fontWeight: 800, fontSize: 15, color: C.textDark, textTransform: 'capitalize' }}>{name}</span>
                        <span style={{ fontSize: 10, fontWeight: 600, color: C.textMuted, flexShrink: 0, marginLeft: 8 }}>{formatTimestamp(thread.timestamp)}</span>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontSize: 13, color: unread > 0 ? C.textDark : C.textMuted, fontWeight: unread > 0 ? 700 : 400, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '80%' }}>
                          {thread.lastMessage || 'New Deal Started'}
                        </span>
                        {unread > 0 && (
                          <div style={{ width: 20, height: 20, borderRadius: '50%', background: '#4CAF50', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                            <span style={{ fontSize: 10, fontWeight: 800, color: 'white' }}>{unread}</span>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* ── RIGHT PANE: Conversation ── */}
      {!isEmptyState && (
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, height: '100%', background: '#fdfbfb', position: 'relative' }} className="deals-right-pane">
          {activeChatEmail ? (
            <>
              {/* Chat Header */}
              <div style={{ height: 68, background: C.peach, borderBottom: `1px solid #f0e0d0`, display: 'flex', alignItems: 'center', padding: '0 16px', gap: 12, flexShrink: 0, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}>
                <button onClick={() => navigate('/deals')} className="mobile-only-back" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 6, borderRadius: '50%', display: 'flex', alignItems: 'center', color: C.terracotta }}>
                  <ArrowLeft size={22} />
                </button>
                <Avatar name={otherName} profileUrl={otherProfileUrl} size={40} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ fontWeight: 900, fontSize: 17, color: C.textDark, margin: 0, textTransform: 'capitalize' }}>{otherName}</p>
                </div>
              </div>

              {/* Gemini AI Banner */}
              <GeminiInsightBanner
                showPanel={showAIPanel} setShowPanel={setShowAIPanel}
                aiCrop={aiCrop} setAiCrop={setAiCrop}
                aiLoc={aiLoc} setAiLoc={setAiLoc}
                aiResult={aiResult} setAiResult={setAiResult}
                aiLoading={aiLoading} setAiLoading={setAiLoading}
              />

              {/* Messages Container (Smooth Scroll & No Horizontal Scroll) */}
              <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', padding: '16px', display: 'flex', flexDirection: 'column', background: '#fafaf7', scrollBehavior: 'smooth' }}>
                {messages.map(msg => (
                  <MessageRenderer key={msg.id} msg={msg} isMe={msg.senderId === user?.email} isFarmer={isFarmer} onPay={(m) => { setPendingInvoice(m); setShowPayment(true); }} onAccept={handleAcceptDeal} onDeclineDonation={handleDeclineDonation} onCounter={handleOpenCounter} onDecline={(m) => declineDeal(m.id)} onWithdraw={(m) => withdrawDeal(m.id)} onLocation={handleOpenLocation} onConfirmDelivery={handleConfirmDelivery} />
                ))}
                <div ref={messagesEndRef} style={{ height: 1 }} />
              </div>

              {/* Location Popup Drawer */}
              {showLocationForm && (
                <LocationPopup onClose={() => setShowLocationForm(false)} onSubmit={submitLocation}
                  locAddress={locAddress} setLocAddress={setLocAddress}
                  locLat={locLat} setLocLat={setLocLat}
                  locLng={locLng} setLocLng={setLocLng}
                  fetchLocation={fetchLocation} isFarmer={isFarmer}
                />
              )}
              
              {/* Counter Form Drawer */}
              {showCounterForm && (
                <CounterForm onClose={() => setShowCounterForm(false)} onSubmit={handleSubmitCounter}
                  targetPrice={counterPrice} setTargetPrice={setCounterPrice}
                  reason={counterReason} setReason={setCounterReason} baseMsg={counterTarget}
                />
              )}

              {/* Input Area (No Image/Audio) */}
              <div style={{ padding: '10px 16px', background: '#f0e2d5', display: 'flex', alignItems: 'flex-end', gap: 8, flexShrink: 0 }}>
                <div style={{ display: 'flex', flex: 1, background: 'white', borderRadius: 24, padding: '6px 12px', alignItems: 'flex-end', boxShadow: '0 1px 2px rgba(0,0,0,0.1)' }}>
                  <form onSubmit={handleSendMessage} style={{ flex: 1, display: 'flex', alignItems: 'center' }}>
                    <textarea
                      value={messageInput}
                      onChange={e => { setMessageInput(e.target.value); e.target.style.height = 'auto'; e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`; }}
                      onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendMessage(); } }}
                      placeholder="Type a message"
                      rows={1}
                      style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', fontSize: 15, color: '#333', resize: 'none', padding: '10px 10px', fontFamily: 'inherit', overflowY: 'auto' }}
                    />
                  </form>
                </div>
                <button onClick={handleSendMessage} disabled={!messageInput.trim()} style={{ width: 44, height: 44, borderRadius: '50%', background: messageInput.trim() ? '#D85A38' : '#ccc', border: 'none', cursor: messageInput.trim() ? 'pointer' : 'not-allowed', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, boxShadow: '0 1px 2px rgba(0,0,0,0.15)', transition: 'background 0.2s' }}>
                  <Send size={20} color="white" style={{ marginLeft: -2, marginTop: 2 }} />
                </button>
              </div>
            </>
          ) : (
            /* Desktop placeholder when no chat selected */
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 16, opacity: 0.6 }}>
              <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#f2ebe7', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Search size={36} color={C.terracottaLight} />
              </div>
              <p style={{ fontWeight: 700, color: C.terracotta, fontSize: 15 }}>Select a deal to view</p>
            </div>
          )}
        </div>
      )}

      {/* ── Responsive CSS ── */}
      <style>{`
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }
        @keyframes spin { to { transform: rotate(360deg); } }

        /* Mobile default: left pane full width, right pane hidden */
        @media (max-width: 767px) {
          .deals-left-pane { display: flex !important; flex: 1 !important; width: 100% !important; }
          .deals-right-pane { display: none !important; }
          /* When a chat is open (has class chat-open on left pane): hide left, show right */
          .deals-left-pane.chat-open { display: none !important; }
          .deals-left-pane.chat-open ~ .deals-right-pane { display: flex !important; }
        }

        /* Desktop: always show both side by side */
        @media (min-width: 768px) {
          .deals-left-pane { display: flex !important; flex: 0 0 340px !important; }
          .deals-right-pane { display: flex !important; }
          .deals-left-pane.chat-open { display: flex !important; }
          .mobile-only-back { display: none !important; }
        }
      `}</style>
    </div>
  );
};

// ── Thread card style helper ──
function threadCardStyle(isSelected) {
  return {
    display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px',
    background: 'white', borderRadius: 16, cursor: 'pointer',
    border: isSelected ? `1.5px solid ${C.golden}` : '1.5px solid transparent',
    boxShadow: isSelected ? '0 2px 12px rgba(253,185,49,0.2)' : '0 1px 4px rgba(0,0,0,0.06)',
    transition: 'all 0.15s ease',
  };
}

export default Deals;