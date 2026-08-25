import React, { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { collection, query, where, or, onSnapshot, doc, getDoc, setDoc, updateDoc, increment, addDoc } from 'firebase/firestore';
import { useNavigate, useLocation } from 'react-router-dom';
import { Truck, Wallet as WalletIcon, History, Search, MapPin, Navigation, Package, ArrowRight, User, Clock, CheckCircle, ArrowUpRight, ArrowDownLeft, Calendar, Plus } from 'lucide-react';
import UserAvatar from '../../components/common/UserAvatar';
import { motion, AnimatePresence } from 'framer-motion';
import { formatCurrency, getOrderType } from '../../utils/constants';
import notify from '../../services/NotificationService';


const C = {
  terracotta: '#7C3D12',
  terracottaLight: '#c2845c',
  golden: '#FDB931',
  peach: '#FDF0E8',
  textDark: '#1A1A1A',
  textMuted: '#7A7A7A',
  green: '#2E7D32',
  blue: '#1976D2',
  red: '#D32F2F',
  surface: '#FFFFFF',
  background: '#F9FAFB'
};

const TrackModule = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const initialTab = queryParams.get('tab') === 'wallet' ? 'WALLET' : 'ACTIVE';
  const [activeTab, setActiveTab] = useState(initialTab); // ACTIVE, WALLET, HISTORY

  useEffect(() => {
    if (queryParams.get('tab') === 'wallet') {
      setActiveTab('WALLET');
    }
  }, [location.search]);

  const isFarmer = user?.role === 'farmer';

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', paddingBottom: 60 }}>
      {/* Header */}
      <div style={{ marginBottom: 32 }}>
        <h1 style={{ fontSize: 28, fontWeight: 900, color: C.textDark, margin: '0 0 8px' }}>Track & Manage</h1>
        <p style={{ color: C.textMuted, fontSize: 15, margin: 0 }}>Monitor your active deliveries and manage your wallet seamlessly.</p>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 24, overflowX: 'auto', paddingBottom: 8, scrollbarWidth: 'none' }}>
        {[
          { id: 'ACTIVE', label: 'Active Orders', icon: <Truck size={18} /> },
          { id: 'WALLET', label: 'Wallet', icon: <WalletIcon size={18} /> },
          { id: 'HISTORY', label: 'Order History', icon: <History size={18} /> }
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{
              display: 'flex', alignItems: 'center', gap: 8, padding: '12px 20px', borderRadius: 100, border: 'none', cursor: 'pointer',
              fontWeight: 700, fontSize: 14, whiteSpace: 'nowrap', transition: 'all 0.2s',
              background: activeTab === tab.id ? C.terracotta : '#FFF',
              color: activeTab === tab.id ? '#FFF' : C.textMuted,
              boxShadow: activeTab === tab.id ? '0 4px 12px rgba(124, 61, 18, 0.2)' : '0 1px 3px rgba(0,0,0,0.05)',
            }}
          >
            {tab.icon} {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <AnimatePresence mode="wait">
        <motion.div
          key={activeTab}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -10 }}
          transition={{ duration: 0.2 }}
        >
          {activeTab === 'ACTIVE' && <ActiveOrders isFarmer={isFarmer} userEmail={user?.email} navigate={navigate} />}
          {activeTab === 'WALLET' && <WalletSection userEmail={user?.email} />}
          {activeTab === 'HISTORY' && <OrderHistory isFarmer={isFarmer} userEmail={user?.email} navigate={navigate} />}
        </motion.div>
      </AnimatePresence>
    </div>
  );
};

// -----------------------------------------
// ACTIVE ORDERS
// -----------------------------------------
const ActiveOrders = ({ isFarmer, userEmail, navigate }) => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!userEmail) return;
    const field = isFarmer ? 'farmerEmail' : 'userEmail';
    const q = query(collection(db, 'orders'), where(field, '==', userEmail));
    const unsub = onSnapshot(q, (snap) => {
      const active = [];
      snap.forEach(doc => {
        const d = doc.data();
        if (d.status !== 'DELIVERED' && d.status !== 'COMPLETED' && d.status !== 'CANCELLED_BY_USER' && d.status !== 'CANCELLED_FARMER_FAULT') {
          active.push(d);
        }
      });
      active.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
      setOrders(active);
      setLoading(false);
    });
    return () => unsub();
  }, [userEmail, isFarmer]);

  if (loading) return <SkeletonList />;
  if (orders.length === 0) return <EmptyState title="No Active Orders" desc="You don't have any orders in transit right now." icon={<Truck size={40} color={C.terracottaLight} />} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {orders.map(order => (
        <OrderCard key={order.orderId} order={order} navigate={navigate} />
      ))}
    </div>
  );
};

// -----------------------------------------
// ORDER HISTORY
// -----------------------------------------
const OrderHistory = ({ isFarmer, userEmail, navigate }) => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('STANDARD'); // STANDARD, CROP_POOL
  const [detailsOrder, setDetailsOrder] = useState(null); // The order to show in popup
  const [driverInfo, setDriverInfo] = useState(null); // Backwards compatibility for older orders
  const [loadingDriver, setLoadingDriver] = useState(false);

  // Fetch driver profile to always get the latest profile photo
  useEffect(() => {
    if (detailsOrder?.driverEmail) {
      const fetchDriver = async () => {
        setLoadingDriver(true);
        try {
          const docSnap = await getDoc(doc(db, 'users', detailsOrder.driverEmail));
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
  }, [detailsOrder]);

  useEffect(() => {
    if (!userEmail) return;
    
    // Support Crop Pools by checking all participant fields
    const q = query(
      collection(db, 'orders'), 
      or(
        where('farmerEmail', '==', userEmail),
        where('userEmail', '==', userEmail),
        where('hostEmail', '==', userEmail),
        where('coLoaderEmail', '==', userEmail)
      )
    );
    
    const unsub = onSnapshot(q, (snap) => {
      const hist = [];
      snap.forEach(doc => {
        const d = doc.data();
        if (d.status === 'DELIVERED' || d.status === 'COMPLETED' || d.status?.startsWith('CANCELLED')) {
          hist.push(d);
        }
      });
      hist.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
      setOrders(hist);
      setLoading(false);
    });
    return () => unsub();
  }, [userEmail, isFarmer]);

  if (loading) return <SkeletonList />;

  return (
    <div>
      {isFarmer && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          <button onClick={() => setFilter('STANDARD')} style={{ padding: '8px 16px', borderRadius: 8, border: 'none', background: filter === 'STANDARD' ? C.peach : '#FFF', color: filter === 'STANDARD' ? C.terracotta : C.textMuted, fontWeight: 700, cursor: 'pointer' }}>Standard Orders</button>
          <button onClick={() => setFilter('CROP_POOL')} style={{ padding: '8px 16px', borderRadius: 8, border: 'none', background: filter === 'CROP_POOL' ? C.peach : '#FFF', color: filter === 'CROP_POOL' ? C.terracotta : C.textMuted, fontWeight: 700, cursor: 'pointer' }}>Crop Pool Orders</button>
        </div>
      )}

      {filter === 'CROP_POOL' && isFarmer ? (
        orders.filter(o => o.orderId?.includes('POOL')).length === 0 ? (
          <EmptyState title="No Crop Pool Orders" desc="You haven't participated in any crop pools yet." icon={<History size={40} color={C.terracottaLight} />} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {orders.filter(o => o.orderId?.includes('POOL')).map(order => (
              <OrderCard key={order.orderId} order={order} navigate={navigate} isHistory userEmail={userEmail} onViewDetails={() => setDetailsOrder(order)} />
            ))}
          </div>
        )
      ) : filter === 'STANDARD' && orders.filter(o => !o.orderId?.includes('POOL')).length === 0 ? (
        <EmptyState title="No Standard Orders" desc="Your completed orders will appear here." icon={<History size={40} color={C.terracottaLight} />} />
      ) : filter === 'STANDARD' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {orders.filter(o => !o.orderId?.includes('POOL')).map(order => (
            <OrderCard key={order.orderId} order={order} navigate={navigate} isHistory userEmail={userEmail} onViewDetails={() => setDetailsOrder(order)} />
          ))}
        </div>
      ) : orders.length === 0 ? (
        <EmptyState title="No Order History" desc="Your completed orders will appear here." icon={<History size={40} color={C.terracottaLight} />} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {orders.map(order => (
            <OrderCard key={order.orderId} order={order} navigate={navigate} isHistory userEmail={userEmail} onViewDetails={() => setDetailsOrder(order)} />
          ))}
        </div>
      )}

      {/* View Details Popup */}
      <AnimatePresence>
        {detailsOrder && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.6)', padding: 16 }}>
            <motion.div initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }} style={{ background: '#FFF', borderRadius: 24, width: '100%', maxWidth: 500, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 40px rgba(0,0,0,0.2)' }}>
              
              <div style={{ padding: '24px 24px 16px', borderBottom: '1px solid #EEE', display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'sticky', top: 0, background: '#FFF', zIndex: 10 }}>
                <h3 style={{ margin: 0, fontSize: 20, fontWeight: 900, color: C.textDark }}>Order Summary</h3>
                <button onClick={() => setDetailsOrder(null)} style={{ background: '#F5F5F5', border: 'none', width: 36, height: 36, borderRadius: '50%', cursor: 'pointer', fontWeight: 800, color: C.textMuted }}>X</button>
              </div>

              <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 24 }}>
                {/* Order Info */}
                <div style={{ background: C.peach, borderRadius: 16, padding: 16, border: `1px solid ${C.golden}` }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <span style={{ fontSize: 13, color: C.terracotta, fontWeight: 700 }}>Order ID</span>
                    <span style={{ fontSize: 14, fontWeight: 900, color: C.textDark }}>{detailsOrder.orderId}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <span style={{ fontSize: 13, color: C.terracotta, fontWeight: 700 }}>Handover Date</span>
                    <span style={{ fontSize: 14, fontWeight: 800, color: C.textDark }}>{new Date(detailsOrder.timestamp).toLocaleDateString()}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: 13, color: C.terracotta, fontWeight: 700 }}>Handover Time</span>
                    <span style={{ fontSize: 14, fontWeight: 800, color: C.textDark }}>{new Date(detailsOrder.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                  </div>
                </div>

                {/* Delivery Partner */}
                {!((detailsOrder.isDonation || detailsOrder.dealType === 'DONATION' || (detailsOrder.orderId && detailsOrder.orderId.includes('DON'))) && (detailsOrder.vehicleType === 'Self Pickup' || detailsOrder.vehicleType === 'SELF')) && (
                <div>
                  <h4 style={{ margin: '0 0 12px', fontSize: 14, color: C.textMuted, textTransform: 'uppercase', letterSpacing: 1, fontWeight: 800 }}>Delivery Partner</h4>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, background: '#FAFAFA', padding: 16, borderRadius: 12, border: '1px solid #EEE' }}>
                    <div style={{ width: 48, height: 48, borderRadius: '50%', background: '#E0E0E0', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', flexShrink: 0 }}>
                      {loadingDriver ? (
                        <div style={{ width: '100%', height: '100%', background: '#d1d5db', animation: 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite' }} />
                      ) : (
                        <UserAvatar src={detailsOrder.driverProfilePic || driverInfo?.profileImageUrl || detailsOrder.profileImageUrl} alt="Driver" size={48} />
                      )}
                    </div>
                    <div>
                      <h5 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 800, color: C.textDark }}>{detailsOrder.driverName || driverInfo?.name || driverInfo?.username || 'Assigning Driver...'}</h5>
                      <p style={{ margin: '0 0 2px', fontSize: 12, color: C.blue, fontWeight: 700 }}>
                        {(() => {
                          const id = detailsOrder.driverId || driverInfo?.driverId || '';
                          if (!id) return 'GW-D0000';
                          const numMatch = String(id).match(/\d+/);
                          return numMatch ? `GW-D${numMatch[0].padStart(4, '0')}` : id;
                        })()}
                      </p>
                      <p style={{ margin: '0 0 4px', fontSize: 12, color: C.textMuted, fontWeight: 600 }}>{detailsOrder.driverPhone || driverInfo?.phone || ''}</p>
                    </div>
                  </div>
                </div>
                )}

                {/* Participants */}
                <div>
                  <h4 style={{ margin: '0 0 12px', fontSize: 14, color: C.textMuted, textTransform: 'uppercase', letterSpacing: 1, fontWeight: 800 }}>Participants</h4>
                  <div style={{ background: '#FAFAFA', padding: '12px 16px', borderRadius: 12, border: '1px solid #EEE', display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: 13, color: C.textMuted, fontWeight: 600 }}>{detailsOrder.coLoaderEmail ? 'Host Email' : 'Farmer Email'}</span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: C.textDark }}>{detailsOrder.hostEmail || detailsOrder.farmerEmail || 'N/A'}</span>
                    </div>
                    {detailsOrder.coLoaderEmail ? (
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: 13, color: C.textMuted, fontWeight: 600 }}>Co-loader Email</span>
                        <span style={{ fontSize: 13, fontWeight: 700, color: C.textDark }}>{detailsOrder.coLoaderEmail}</span>
                      </div>
                    ) : (
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: 13, color: C.textMuted, fontWeight: 600 }}>Buyer Email</span>
                        <span style={{ fontSize: 13, fontWeight: 700, color: C.textDark }}>{detailsOrder.userEmail || 'N/A'}</span>
                      </div>
                    )}
                  </div>
                </div>

                {/* Locations */}
                <div>
                  <h4 style={{ margin: '0 0 12px', fontSize: 14, color: C.textMuted, textTransform: 'uppercase', letterSpacing: 1, fontWeight: 800 }}>Locations</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    <div style={{ background: '#FFF9F5', padding: 12, borderRadius: 12, border: '1px solid #FDF0E8' }}>
                      <span style={{ fontSize: 11, fontWeight: 800, color: C.terracotta, textTransform: 'uppercase' }}>Pickup</span>
                      <p style={{ margin: '4px 0 0', fontSize: 13, fontWeight: 600, color: C.textDark }}>
                        {detailsOrder.orderId?.includes('POOL') 
                          ? (detailsOrder.hostEmail === userEmail ? detailsOrder.pickupAddress : detailsOrder.coLoaderPickupAddress)
                          : detailsOrder.pickupAddress || 'N/A'}
                      </p>
                    </div>
                    <div style={{ background: '#F5F9FF', padding: 12, borderRadius: 12, border: '1px solid #E3F2FD' }}>
                      <span style={{ fontSize: 11, fontWeight: 800, color: C.blue, textTransform: 'uppercase' }}>Drop</span>
                      <p style={{ margin: '4px 0 0', fontSize: 13, fontWeight: 600, color: C.textDark }}>
                        {detailsOrder.orderId?.includes('POOL')
                          ? (detailsOrder.hostEmail === userEmail ? detailsOrder.dropAddress : detailsOrder.coLoaderDropAddress)
                          : detailsOrder.dropAddress || 'N/A'}
                      </p>
                    </div>
                  </div>
                </div>

                {/* Bill Summary */}
                <div>
                  <h4 style={{ margin: '0 0 12px', fontSize: 14, color: C.textMuted, textTransform: 'uppercase', letterSpacing: 1, fontWeight: 800 }}>Bill Summary</h4>
                  <div style={{ background: '#FAFAFA', borderRadius: 12, border: '1px solid #EEE', overflow: 'hidden' }}>
                    <div style={{ padding: '12px 16px', display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #EEE' }}>
                      <span style={{ fontSize: 13, color: C.textMuted, fontWeight: 600 }}>
                        {detailsOrder.orderId?.includes('POOL') 
                          ? (detailsOrder.hostEmail === userEmail ? `${detailsOrder.cropName} (${detailsOrder.weightKg} KG)` : `${detailsOrder.coLoaderCropName} (${detailsOrder.coLoaderWeightKg} KG)`)
                          : `${detailsOrder.cropName} (${detailsOrder.weightKg} KG)`}
                      </span>
                      <span style={{ fontSize: 14, fontWeight: 800, color: C.textDark }}>
                        {detailsOrder.dealType === 'DONATION' || detailsOrder.isDonation ? 'Donation' : detailsOrder.orderId?.includes('POOL') ? 'Shared Trip' : `${formatCurrency(parseFloat(detailsOrder.cropValue || 0).toFixed(2))}`}
                      </span>
                    </div>
                    <div style={{ padding: '12px 16px', display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #EEE' }}>
                      <span style={{ fontSize: 13, color: C.textMuted, fontWeight: 600 }}>Transport Fare</span>
                      <span style={{ fontSize: 14, fontWeight: 800, color: C.textDark }}>
                        {formatCurrency(detailsOrder.orderId?.includes('POOL') 
                          ? parseFloat((detailsOrder.hostEmail === userEmail ? detailsOrder.totalPayment : detailsOrder.coLoaderPayment) || 0).toFixed(2)
                          : parseFloat(detailsOrder.transportFare || 0).toFixed(2))}
                      </span>
                    </div>
                    <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', background: '#FFF' }}>
                      <span style={{ fontSize: 16, fontWeight: 900, color: C.textDark }}>Grand Total</span>
                      <span style={{ fontSize: 18, fontWeight: 900, color: C.green }}>
                        {formatCurrency(detailsOrder.orderId?.includes('POOL') 
                          ? parseFloat((detailsOrder.hostEmail === userEmail ? detailsOrder.totalPayment : detailsOrder.coLoaderPayment) || 0).toFixed(2)
                          : parseFloat(detailsOrder.totalPaid || 0).toFixed(2))}
                      </span>
                    </div>
                  </div>
                </div>
                
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

const OrderCard = ({ order, navigate, isHistory, onViewDetails, userEmail }) => {
  const isCancelled = order.status?.startsWith('CANCELLED');
  const isDelivered = order.status === 'COMPLETED' || order.status === 'DELIVERED';
  const isDonation = getOrderType(order.orderId) === 'DONATION' || order.dealType === 'DONATION' || order.isDonation === true;
  
  const badgeColor = isCancelled ? C.red : isDelivered ? C.green : (isDonation ? '#9333ea' : C.golden);
  const badgeText = order.status?.replace(/_/g, ' ') || 'UNKNOWN';
  
  const isPool = getOrderType(order.orderId) === 'POOL';
  const isHost = isPool && order.hostEmail === userEmail;
  const displayCropName = isPool ? (isHost ? order.cropName : order.coLoaderCropName) : order.cropName;
  const displayWeight = isPool ? (isHost ? order.weightKg : order.coLoaderWeightKg) : order.weightKg;
  const displayPaid = isPool ? (isHost ? order.totalPayment : order.coLoaderPayment) : order.totalPaid;

  const isSelfPickupDonation = isDonation && (order.vehicleType === 'Self Pickup' || order.vehicleType === 'SELF');

  return (
    <div style={{ background: isDonation ? '#fdf4ff' : '#FFF', borderRadius: 16, padding: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: isDonation ? '1px solid #e9d5ff' : '1px solid #F0F0F0', flexWrap: 'wrap', gap: 16 }}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <div style={{ width: 64, height: 64, borderRadius: 12, background: isDonation ? '#f3e8ff' : C.peach, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <span style={{ fontSize: 24 }}>{isDonation ? '❤️' : '🌱'}</span>
        </div>
        <div>
          <h3 style={{ margin: '0 0 4px', fontSize: 18, fontWeight: 800, color: C.textDark }}>
            {displayCropName} <span style={{ color: C.textMuted, fontWeight: 600, fontSize: 14 }}>({displayWeight} KG)</span>
          </h3>
          <p style={{ margin: '0 0 6px', fontSize: 13, color: C.blue, fontWeight: 700 }}>{order.orderId}</p>
          
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {!(isSelfPickupDonation && badgeText === 'PENDING DRIVER') && (
              <div style={{ display: 'inline-flex', padding: '4px 8px', borderRadius: 4, background: `${badgeColor}15`, color: badgeColor, fontSize: 11, fontWeight: 800 }}>
                {badgeText}
              </div>
            )}
            {isDonation && (
              <div style={{ display: 'inline-flex', padding: '4px 8px', borderRadius: 4, background: '#E8F5E9', color: '#2E7D32', fontSize: 11, fontWeight: 800 }}>
                DONATION
              </div>
            )}
            {isSelfPickupDonation && (
              <div style={{ display: 'inline-flex', padding: '4px 8px', borderRadius: 4, background: '#FFF9F5', color: '#D4AF37', border: '1px solid rgba(212, 175, 55, 0.35)', fontSize: 11, fontWeight: 800 }}>
                SELF PICKUP
              </div>
            )}
          </div>

          {isDonation && order.pickupOtp && !isDelivered && userEmail === order.userEmail && (
             <div style={{ marginTop: '6px', fontSize: 12, color: '#9333ea', fontWeight: 'bold' }}>Pickup OTP: {order.pickupOtp}</div>
          )}
        </div>
      </div>

      <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
        {isSelfPickupDonation ? (
           <div style={{ marginBottom: 12, textAlign: 'right' }}>
              <p style={{ margin: 0, fontSize: 14, color: '#2E7D32', fontWeight: 800 }}>Total: ₹0</p>
              <p style={{ margin: 0, fontSize: 12, color: '#666', fontWeight: 600 }}>Transport Fare: ₹0</p>
              <p style={{ margin: 0, fontSize: 12, color: '#666', fontWeight: 600 }}>Crop Value: ₹0</p>
           </div>
        ) : isDonation ? (
           <div style={{ marginBottom: 12, textAlign: 'right' }}>
              <p style={{ margin: 0, fontSize: 14, color: '#9333ea', fontWeight: 800 }}>Crop Value: ₹0</p>
              {parseFloat(order.transportFare || 0) > 0 && (
                 <p style={{ margin: 0, fontSize: 12, color: '#666', fontWeight: 600 }}>Transport: {formatCurrency(order.transportFare)}</p>
              )}
           </div>
        ) : (
           <p style={{ margin: '0 0 12px', fontSize: 20, fontWeight: 900, color: C.textDark }}>{formatCurrency(parseFloat(displayPaid || 0).toFixed(2))}</p>
        )}
        <button 
          onClick={() => {
            if (isHistory) {
              onViewDetails();
            } else {
              if (isDonation && (order.vehicleType === 'Self Pickup' || order.vehicleType === 'SELF')) {
                navigate(`/track-self-pickup/${order.orderId}`);
              } else {
                navigate(`/track/${order.orderId}`);
              }
            }
          }} 
          style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', borderRadius: 8, background: isDonation ? '#9333ea' : C.terracotta, color: '#FFF', border: 'none', fontWeight: 700, cursor: 'pointer', transition: '0.2s', boxShadow: '0 2px 6px rgba(124, 61, 18, 0.2)' }}
        >
          {isHistory ? 'View Details' : 'Track Order'} <Navigation size={14} />
        </button>
      </div>
    </div>
  );
};

// -----------------------------------------
// WALLET SECTION
// -----------------------------------------
const WalletSection = ({ userEmail }) => {
  const [balance, setBalance] = useState(0);
  const [transactions, setTransactions] = useState([]);
  
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const initialModal = queryParams.get('action') === 'topup' ? 'ADD' : null;
  
  const [modal, setModal] = useState(initialModal); // 'ADD' or 'WITHDRAW'
  const [amountInput, setAmountInput] = useState('');
  const [processing, setProcessing] = useState(false);
  const [filter, setFilter] = useState('ALL');

  useEffect(() => {
    if (!userEmail) return;
    const unsub = onSnapshot(doc(db, 'wallets', userEmail), (docSnap) => {
      if (docSnap.exists()) {
        setBalance(docSnap.data().balance || 0);
      }
    });
    return () => unsub();
  }, [userEmail]);

  useEffect(() => {
    if (!userEmail) return;
    const q = query(collection(db, 'transactions'), where('email', '==', userEmail));
    const unsub = onSnapshot(q, (snap) => {
      const txs = [];
      snap.forEach(d => txs.push({ id: d.id, ...d.data() }));
      txs.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
      setTransactions(txs);
    });
    return () => unsub();
  }, [userEmail]);

  const handleWalletAction = async () => {
    const amt = parseFloat(amountInput);
    if (isNaN(amt) || amt <= 0) return;
    if (modal === 'WITHDRAW' && amt > balance) {
      notify.info("Insufficient balance to withdraw!");
      return;
    }
    setProcessing(true);
    try {
      const isAdd = modal === 'ADD';
      const ref = doc(db, 'wallets', userEmail);
      await setDoc(ref, { balance: increment(isAdd ? amt : -amt) }, { merge: true });
      
      await addDoc(collection(db, 'transactions'), {
        email: userEmail,
        type: isAdd ? 'TOPUP' : 'WITHDRAWAL',
        title: isAdd ? 'Wallet Top-up' : 'Bank Withdrawal',
        amount: amt,
        isCredit: isAdd,
        timestamp: Date.now()
      });
      
      setModal(null);
      setAmountInput('');
    } catch (e) {
      console.error(e);
      notify.error("Transaction failed");
    } finally {
      setProcessing(false);
    }
  };

  const filteredTx = transactions.filter(tx => {
    if (filter === 'CREDIT') return tx.isCredit;
    if (filter === 'DEBIT') return !tx.isCredit;
    return true;
  });

  return (
    <div>
      {/* Wallet Card */}
      <div style={{ background: `linear-gradient(135deg, ${C.terracotta}, #5A2A0B)`, borderRadius: 24, padding: 32, color: '#FFF', position: 'relative', overflow: 'hidden', boxShadow: '0 10px 30px rgba(124, 61, 18, 0.3)', marginBottom: 32 }}>
        <div style={{ position: 'absolute', top: -50, right: -50, width: 200, height: 200, borderRadius: '50%', background: 'rgba(255,255,255,0.1)' }} />
        <div style={{ position: 'absolute', bottom: -20, left: -20, width: 100, height: 100, borderRadius: '50%', background: 'rgba(255,255,255,0.05)' }} />
        
        <div style={{ position: 'relative', zIndex: 1 }}>
          <p style={{ margin: '0 0 8px', fontSize: 16, color: 'rgba(255,255,255,0.8)', fontWeight: 600 }}>Available Balance</p>
          <h2 style={{ margin: '0 0 24px', fontSize: 48, fontWeight: 900 }}>{formatCurrency(balance.toFixed(2))}</h2>
          
          <div style={{ display: 'flex', gap: 12 }}>
            <button onClick={() => setModal('ADD')} style={{ padding: '12px 24px', borderRadius: 12, border: 'none', background: '#FFF', color: C.terracotta, fontWeight: 800, fontSize: 15, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}><Plus size={18} /> Add Money</button>
            <button onClick={() => setModal('WITHDRAW')} style={{ padding: '12px 24px', borderRadius: 12, border: '1px solid rgba(255,255,255,0.3)', background: 'rgba(255,255,255,0.1)', color: '#FFF', fontWeight: 800, fontSize: 15, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>Withdraw</button>
          </div>
        </div>
      </div>

      {/* Transactions */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h3 style={{ fontSize: 20, fontWeight: 800, margin: 0, color: C.textDark }}>Transaction History</h3>
          <div style={{ display: 'flex', gap: 8 }}>
            {['ALL', 'CREDIT', 'DEBIT'].map(f => (
              <span key={f} onClick={() => setFilter(f)} style={{ fontSize: 12, fontWeight: 700, padding: '4px 10px', borderRadius: 6, background: filter === f ? C.peach : '#EEE', color: filter === f ? C.terracotta : C.textMuted, cursor: 'pointer' }}>{f}</span>
            ))}
          </div>
        </div>
        
        {filteredTx.length === 0 ? (
          <EmptyState title="No Transactions" desc="Your wallet history is empty." icon={<WalletIcon size={40} color={C.terracottaLight} />} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {filteredTx.map(tx => {
              const isEscrow = tx.type === 'ESCROW_LOCK';
              return (
                <div key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: isEscrow ? C.peach : '#FFF', padding: '16px 20px', borderRadius: 16, border: '1px solid #F0F0F0' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                    <div style={{ width: 44, height: 44, borderRadius: '50%', background: isEscrow ? 'rgba(253, 185, 49, 0.2)' : (tx.isCredit ? '#E8F5E9' : '#FFEBEE'), display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      {tx.isCredit ? <ArrowDownLeft size={20} color={isEscrow ? C.golden : C.green} /> : <ArrowUpRight size={20} color={isEscrow ? C.terracotta : C.red} />}
                    </div>
                    <div>
                      <h4 style={{ margin: '0 0 4px', fontSize: 15, fontWeight: 800, color: C.textDark }}>{tx.type === 'ESCROW_REFUND' ? 'Refunded - Escrow Released' : (isEscrow ? "Escrow Hold - Pending" : tx.title)}</h4>
                      <p style={{ margin: 0, fontSize: 12, color: C.textMuted, fontWeight: 600 }}>{new Date(tx.timestamp).toLocaleString()} • {tx.type === 'ESCROW_REFUND' ? `Reason: ${tx.title.replace('Refunded - ', '')}` : (isEscrow ? "Pending" : tx.type)}</p>
                    </div>
                  </div>
                  <div style={{ fontWeight: 800, fontSize: 16, color: isEscrow ? C.terracotta : (tx.isCredit ? C.green : C.textDark) }}>
                    {tx.isCredit ? '+' : '-'}{formatCurrency(tx.amount.toFixed(2))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Modal */}
      <AnimatePresence>
        {modal && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.5)' }}>
            <motion.div initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.9, opacity: 0 }} style={{ background: '#FFF', borderRadius: 24, padding: 32, width: '90%', maxWidth: 400, boxShadow: '0 20px 40px rgba(0,0,0,0.2)' }}>
              <h3 style={{ margin: '0 0 20px', fontSize: 24, fontWeight: 900, color: C.textDark }}>{modal === 'ADD' ? 'Add Money to Wallet' : 'Withdraw from Wallet'}</h3>
              <input 
                type="number" 
                placeholder="Enter amount (₹)" 
                value={amountInput} 
                onChange={e => setAmountInput(e.target.value)} 
                style={{ width: '100%', padding: 16, borderRadius: 12, border: '2px solid #EEE', fontSize: 20, fontWeight: 800, marginBottom: 24, outline: 'none' }}
                autoFocus
              />
              <div style={{ display: 'flex', gap: 12 }}>
                <button onClick={() => setModal(null)} style={{ flex: 1, padding: 16, borderRadius: 12, border: 'none', background: '#F5F5F5', color: C.textMuted, fontWeight: 800, fontSize: 16, cursor: 'pointer' }}>Cancel</button>
                <button onClick={handleWalletAction} disabled={processing || !amountInput} style={{ flex: 1, padding: 16, borderRadius: 12, border: 'none', background: C.terracotta, color: '#FFF', fontWeight: 800, fontSize: 16, cursor: 'pointer', opacity: (processing || !amountInput) ? 0.7 : 1 }}>
                  {processing ? 'Processing...' : 'Confirm'}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

// -----------------------------------------
// HELPERS
// -----------------------------------------
const SkeletonList = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
    {[1, 2, 3].map(i => (
      <div key={i} style={{ height: 100, background: '#F5F5F5', borderRadius: 16, animation: 'pulse 1.5s infinite' }} />
    ))}
    <style>{`@keyframes pulse { 0% { opacity: 0.6; } 50% { opacity: 1; } 100% { opacity: 0.6; } }`}</style>
  </div>
);

const EmptyState = ({ title, desc, icon }) => (
  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 20px', textAlign: 'center', background: '#FFF', borderRadius: 16, border: '1px dashed #DDD' }}>
    <div style={{ width: 80, height: 80, borderRadius: '50%', background: C.peach, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 20 }}>
      {icon}
    </div>
    <h3 style={{ margin: '0 0 8px', fontSize: 20, fontWeight: 800, color: C.textDark }}>{title}</h3>
    <p style={{ margin: 0, fontSize: 15, color: C.textMuted, maxWidth: 300 }}>{desc}</p>
  </div>
);

export default TrackModule;
