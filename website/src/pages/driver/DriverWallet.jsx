import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../utils/firebase';
import { collection, query, where, onSnapshot, doc, runTransaction, getDoc } from 'firebase/firestore';
import { 
  Wallet, ArrowDown, ArrowUp, Hourglass, RefreshCw, X, Car, Download, MapPin, 
  CheckCircle, Clock, Package, Truck, FileText, User as UserIcon, Heart
} from 'lucide-react';
import AgriLoading from '../../components/common/AgriLoading';
import { formatCurrency, getOrderType } from '../../utils/constants';
import UserAvatar from '../../components/common/UserAvatar';
import { motion, AnimatePresence } from 'framer-motion';

const DriverWallet = () => {
  const { user } = useAuth();
  const [walletBalance, setWalletBalance] = useState(0.0);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  const [withdrawAmount, setWithdrawAmount] = useState('');
  const [transactions, setTransactions] = useState([]);
  const [driverOrderHistory, setDriverOrderHistory] = useState([]);
  const [driverProfile, setDriverProfile] = useState(null);
  const [toastMsg, setToastMsg] = useState(null);
  
  // Fetch Driver Profile
  useEffect(() => {
    if (user?.email) {
      getDoc(doc(db, 'users', user.email)).then(d => {
        if (d.exists()) setDriverProfile(d.data());
      });
    }
  }, [user]);

  // Modal State
  const [selectedOrderForDetails, setSelectedOrderForDetails] = useState(null);
  const [hostDetails, setHostDetails] = useState(null);
  const [coLoaderDetails, setCoLoaderDetails] = useState(null);
  const [farmerDetails, setFarmerDetails] = useState(null);
  const [userDetails, setUserDetails] = useState(null);
  const [loadingProfiles, setLoadingProfiles] = useState(false);

  const showToast = (msg) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 3000);
  };
  
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [selectedTabIndex, setSelectedTabIndex] = useState(0);

  useEffect(() => {
    if (!user?.email) return;

    const walletUnsub = onSnapshot(doc(db, 'wallets', user.email), (snap) => {
      setWalletBalance(snap.data()?.balance || 0.0);
    });

    const txQuery = query(collection(db, 'transactions'), where('email', '==', user.email));
    const txUnsub = onSnapshot(txQuery, (snap) => {
      const list = snap.docs.map(d => ({
        id: d.id,
        ...d.data()
      }));
      setTransactions(list.sort((a, b) => b.timestamp - a.timestamp));
    });

    let standardOrders = [];
    let poolOrders = [];
    
    const updateHistory = () => {
      const combined = [...standardOrders, ...poolOrders].sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
      setDriverOrderHistory(combined);
    };

    const stdQuery = query(
      collection(db, 'orders'), 
      where('driverEmail', '==', user.email), 
      where('status', '==', 'DELIVERED')
    );
    const stdUnsub = onSnapshot(stdQuery, (snap) => {
      standardOrders = snap.docs.map(d => d.data());
      updateHistory();
    });

    const poolQuery = query(
      collection(db, 'crop_pools_master'), 
      where('driverEmail', '==', user.email), 
      where('status', '==', 'DELIVERED')
    );
    const poolUnsub = onSnapshot(poolQuery, (snap) => {
      poolOrders = snap.docs.map(d => d.data());
      updateHistory();
    });

    return () => {
      walletUnsub();
      txUnsub();
      stdUnsub();
      poolUnsub();
    };
  }, [user]);

  const handleRefresh = useCallback(() => {
    setIsRefreshing(true);
    setTimeout(() => setIsRefreshing(false), 800);
  }, []);

  const handleWithdrawClick = () => {
    if (walletBalance <= 0) return;
    setWithdrawAmount('');
    setShowWithdrawModal(true);
  };

  const executeWithdrawal = async () => {
    const amount = parseFloat(withdrawAmount);
    if (isNaN(amount) || amount <= 0) {
      showToast("Please enter a valid positive amount.");
      return;
    }
    if (amount > walletBalance) {
      showToast("Insufficient funds.");
      return;
    }

    setIsWithdrawing(true);
    try {
      await runTransaction(db, async (transaction) => {
        const walletRef = doc(db, 'wallets', user.email);
        const walletDoc = await transaction.get(walletRef);
        if (!walletDoc.exists()) throw new Error("Wallet not found!");
        const currentBal = walletDoc.data().balance || 0.0;
        if (currentBal < amount) throw new Error("Insufficient funds during transaction.");

        transaction.update(walletRef, { balance: currentBal - amount });

        const newTxRef = doc(collection(db, 'transactions'));
        transaction.set(newTxRef, {
          email: user.email,
          type: "WITHDRAWAL",
          title: "Wallet Withdrawal",
          amount: amount,
          isCredit: false,
          timestamp: Date.now()
        });
      });

      setShowWithdrawModal(false);
      showToast('Withdrawal request submitted successfully.');
    } catch (error) {
      showToast("Failed to process withdrawal: " + error.message);
    } finally {
      setIsWithdrawing(false);
    }
  };

  const handleViewDetails = async (order) => {
    setSelectedOrderForDetails(order);
    setHostDetails(null);
    setCoLoaderDetails(null);
    setFarmerDetails(null);
    setUserDetails(null);
    setLoadingProfiles(true);
    
    const oType = getOrderType(order.orderId);
    
    try {
      if (oType === 'POOL') {
        const hEmail = order.hostEmail || order.farmerEmail;
        if (hEmail) {
          const hDoc = await getDoc(doc(db, 'users', hEmail));
          if (hDoc.exists()) setHostDetails(hDoc.data());
        }
        if (order.coLoaderEmail) {
          const cDoc = await getDoc(doc(db, 'users', order.coLoaderEmail));
          if (cDoc.exists()) setCoLoaderDetails(cDoc.data());
        }
      } else {
        if (order.farmerEmail) {
          const fDoc = await getDoc(doc(db, 'users', order.farmerEmail));
          if (fDoc.exists()) setFarmerDetails(fDoc.data());
        }
        if (order.userEmail) {
          const uDoc = await getDoc(doc(db, 'users', order.userEmail));
          if (uDoc.exists()) setUserDetails(uDoc.data());
        }
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingProfiles(false);
    }
  };

  return (
    <div style={{ paddingBottom: '40px', maxWidth: '1000px', margin: '0 auto', padding: '24px 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h1 style={{ margin: 0, fontSize: '28px', fontWeight: 900, color: '#1a1a1a', letterSpacing: '-0.5px' }}>
          Driver Wallet
        </h1>
        <button 
          onClick={handleRefresh} disabled={isRefreshing}
          style={{
            padding: '10px', borderRadius: '12px', backgroundColor: 'var(--peach-background)',
            color: 'var(--terracotta-primary)', border: 'none', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            opacity: isRefreshing ? 0.6 : 1, transition: 'transform 0.2s ease',
            transform: isRefreshing ? 'scale(0.95)' : 'scale(1)'
          }}
        >
          {isRefreshing ? <AgriLoading size={20} /> : <RefreshCw size={20} />}
        </button>
      </div>

      {/* Premium Top Card */}
      <div style={{
        background: 'linear-gradient(135deg, var(--terracotta-primary) 0%, #d84315 100%)',
        borderRadius: '24px', padding: '32px', color: '#fff',
        boxShadow: '0 16px 32px rgba(230, 81, 0, 0.25)', marginBottom: '32px', position: 'relative', overflow: 'hidden'
      }}>
        <div style={{
          position: 'absolute', top: '-50%', right: '-10%', width: '300px', height: '300px',
          background: 'radial-gradient(circle, rgba(255,255,255,0.15) 0%, rgba(255,255,255,0) 70%)', borderRadius: '50%'
        }} />

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <span style={{ fontSize: '15px', fontWeight: 600, color: 'rgba(255,255,255,0.85)', letterSpacing: '0.5px', textTransform: 'uppercase' }}>
              Wallet Balance
            </span>
            <div style={{ fontSize: '48px', fontWeight: 900, marginTop: '8px', letterSpacing: '-1px' }}>
              {formatCurrency(walletBalance)}
            </div>
          </div>
          <Wallet size={48} color="rgba(255,255,255,0.2)" />
        </div>

        <div style={{ marginTop: '32px', borderTop: '1px solid rgba(255,255,255,0.15)', paddingTop: '24px' }}>
          <motion.button
            whileHover={{ scale: walletBalance > 0 ? 1.02 : 1 }}
            whileTap={{ scale: walletBalance > 0 ? 0.98 : 1 }}
            onClick={handleWithdrawClick} disabled={walletBalance <= 0}
            style={{
              width: '100%', padding: '16px', borderRadius: '16px',
              backgroundColor: '#fff', color: 'var(--terracotta-primary)',
              border: 'none', cursor: (walletBalance <= 0) ? 'not-allowed' : 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
              fontSize: '16px', fontWeight: 800, opacity: (walletBalance <= 0) ? 0.6 : 1,
              boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
            }}
          >
            <Download size={24} /> <span>Withdraw Money</span>
          </motion.button>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '16px', marginBottom: '24px', borderBottom: '2px solid #f3f4f6' }}>
        {['Wallet Logs', 'Trip History'].map((tab, idx) => (
          <button key={idx} onClick={() => setSelectedTabIndex(idx)}
            style={{
              padding: '12px 24px', backgroundColor: 'transparent', border: 'none',
              borderBottom: selectedTabIndex === idx ? '3px solid var(--terracotta-primary)' : '3px solid transparent',
              color: selectedTabIndex === idx ? 'var(--terracotta-primary)' : '#6b7280',
              fontSize: '16px', fontWeight: selectedTabIndex === idx ? 800 : 600,
              cursor: 'pointer', transition: 'all 0.2s ease', marginBottom: '-2px'
            }}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <AnimatePresence mode="wait">
        <motion.div key={selectedTabIndex} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.2 }}>
          {selectedTabIndex === 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {transactions.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '60px 0' }}>
                  <Wallet size={64} color="#e5e7eb" style={{ margin: '0 auto' }} />
                  <h3 style={{ color: '#4b5563', marginTop: '16px' }}>No Transactions Yet</h3>
                  <p style={{ color: '#9ca3af' }}>Your wallet is fresh and clean.</p>
                </div>
              ) : (
                transactions.map((txn, idx) => {
                  const isEscrow = txn.type === "ESCROW_LOCK";
                  const isIncome = txn.isCredit === true;
                  
                  let label = 'Wallet Transaction';
                  let isPool = false;
                  
                  if (txn.type === 'WITHDRAWAL') {
                    label = 'Wallet Withdrawal';
                  } else if (isEscrow) {
                    label = 'Escrow Hold';
                  } else {
                    const v = (driverProfile?.vehicleType || '').toLowerCase();
                    const isStandardVehicle = v.includes('bike') || v.includes('scooter') || v.includes('auto') || v.includes('two');
                    const isStandardOrder = txn.orderId?.startsWith('GW-');
                    const isDonationOrder = txn.orderId?.startsWith('DEAL-DON-') || txn.orderId?.startsWith('GW-DON-');
                    
                    if (isDonationOrder) {
                      label = 'Donation Order';
                    } else if (isStandardVehicle || isStandardOrder) {
                      label = 'Standard Order';
                    } else {
                      label = 'Crop Pool';
                    }
                  }

                  return (
                    <motion.div key={txn.id || idx} whileHover={{ y: -2, boxShadow: '0 8px 16px rgba(0,0,0,0.06)' }}
                      style={{
                        display: 'flex', alignItems: 'center', padding: '20px',
                        backgroundColor: '#fff', borderRadius: '20px',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid #f3f4f6', transition: 'box-shadow 0.2s'
                      }}
                    >
                      <div style={{
                        width: '52px', height: '52px', borderRadius: '50%',
                        backgroundColor: isEscrow ? 'var(--peach-background)' : (isIncome ? '#e8f5e9' : '#ffebee'),
                        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                      }}>
                        {isEscrow ? <Hourglass size={24} color="var(--golden-yellow)" /> 
                         : txn.type === 'WITHDRAWAL' ? <ArrowUp size={24} color="#d32f2f" />
                         : <ArrowDown size={24} color="#2e7d32" />}
                      </div>
                      
                      <div style={{ flex: 1, marginLeft: '16px' }}>
                        <h4 style={{ margin: '0 0 4px 0', fontSize: '16px', fontWeight: 800, color: '#1f2937' }}>
                          {label}
                        </h4>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#6b7280', fontSize: '13px', fontWeight: 500 }}>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Clock size={14} /> {new Date(txn.timestamp).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })}
                          </span>
                        </div>
                      </div>
                      
                      <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                        <span style={{ fontSize: '20px', fontWeight: 900, color: isEscrow ? 'var(--golden-yellow)' : (isIncome ? '#2e7d32' : '#d32f2f') }}>
                          {isIncome ? '+' : '-'}{formatCurrency(txn.amount)}
                        </span>
                        <div style={{
                          padding: '4px 8px', borderRadius: '8px', fontSize: '11px', fontWeight: 800, textTransform: 'uppercase',
                          backgroundColor: isEscrow ? '#fef3c7' : '#f3f4f6', color: isEscrow ? '#d97706' : '#6b7280'
                        }}>
                          {isEscrow ? 'Pending' : 'Completed'}
                        </div>
                      </div>
                    </motion.div>
                  );
                })
              )}
            </div>
          )}

          {selectedTabIndex === 1 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {driverOrderHistory.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '60px 0' }}>
                  <Car size={64} color="#e5e7eb" style={{ margin: '0 auto' }} />
                  <h3 style={{ color: '#4b5563', marginTop: '16px' }}>No Trips Yet</h3>
                  <p style={{ color: '#9ca3af' }}>Your completed trips will appear here.</p>
                </div>
              ) : (
                driverOrderHistory.map((order, idx) => {
                  const orderType = getOrderType(order.orderId);
                  const isPool = orderType === 'POOL';
                  const isDonation = orderType === 'DONATION';
                  return (
                    <motion.div key={order.orderId || idx} whileHover={{ y: -4, boxShadow: '0 12px 24px rgba(0,0,0,0.08)' }}
                      style={{
                        backgroundColor: '#fff', borderRadius: '24px',
                        boxShadow: '0 4px 12px rgba(0,0,0,0.03)', border: '1px solid #f3f4f6',
                        borderTop: '4px solid var(--terracotta-primary)', overflow: 'hidden', transition: 'all 0.2s'
                      }}
                    >
                      <div style={{ padding: '24px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
                          <div>
                            <div style={{ 
                              display: 'inline-flex', alignItems: 'center', gap: '6px',
                              padding: '6px 12px', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', 
                              borderRadius: '20px', fontSize: '12px', fontWeight: 800, marginBottom: '12px' 
                            }}>
                              {isDonation ? <Heart size={14} /> : (isPool ? <Truck size={14} /> : <Car size={14} />)} {isDonation ? 'Donation Rescue' : (isPool ? 'Crop Pool' : 'Standard Order')}
                            </div>
                            <h4 style={{ margin: 0, fontSize: '15px', color: '#6b7280', fontWeight: 600 }}>
                              Order ID: <span style={{ color: '#111827', fontWeight: 800 }}>{order.orderId || 'N/A'}</span>
                            </h4>
                          </div>
                          <span style={{ 
                            display: 'flex', alignItems: 'center', gap: '4px',
                            padding: '6px 12px', backgroundColor: '#e8f5e9', color: '#2e7d32', 
                            borderRadius: '20px', fontSize: '12px', fontWeight: 800 
                          }}>
                            <CheckCircle size={14} /> DELIVERED
                          </span>
                        </div>
                        
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '12px', marginBottom: '24px', background: '#f9fafb', padding: '16px', borderRadius: '16px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '14px', color: '#374151', fontWeight: 600 }}>
                            <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: '#e0e7ff', color: '#4f46e5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                              {isPool ? 'H' : 'F'}
                            </div>
                            {isPool ? (order.hostEmail || order.farmerEmail || 'N/A') : (order.farmerEmail || 'N/A')}
                          </div>
                          {isPool && order.coLoaderEmail && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '14px', color: '#374151', fontWeight: 600 }}>
                              <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: '#fce7f3', color: '#db2777', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>C</div>
                              {order.coLoaderEmail}
                            </div>
                          )}
                        </div>

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid #f3f4f6', paddingTop: '20px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#6b7280', fontSize: '13px', fontWeight: 600 }}>
                            <Clock size={16} />
                            {new Date(order.timestamp).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })}
                          </div>
                          <motion.button 
                            whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }} onClick={() => handleViewDetails(order)} 
                            style={{ 
                              padding: '10px 20px', borderRadius: '24px', 
                              background: 'linear-gradient(135deg, var(--terracotta-primary) 0%, #d84315 100%)', 
                              color: '#fff', border: 'none', fontSize: '14px', fontWeight: 800, cursor: 'pointer',
                              display: 'flex', alignItems: 'center', gap: '6px', boxShadow: '0 4px 12px rgba(230, 81, 0, 0.2)'
                            }}
                          >
                            <FileText size={16} /> View Details
                          </motion.button>
                        </div>
                      </div>
                    </motion.div>
                  );
                })
              )}
            </div>
          )}
        </motion.div>
      </AnimatePresence>

      {/* Withdraw Modal... (Unchanged from before logic) */}
      <AnimatePresence>
        {showWithdrawModal && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '24px' }}>
             {/* Simple logic wrapper */}
             <div style={{ backgroundColor: '#fff', borderRadius: '28px', padding: '32px', width: '100%', maxWidth: '400px' }}>
                <h2 style={{ margin: '0 0 24px 0', fontSize: '24px', fontWeight: 900 }}>Withdraw</h2>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <button onClick={() => setShowWithdrawModal(false)} style={{ flex: 1, padding: '16px', borderRadius: '16px', border: 'none', cursor: 'pointer', fontWeight: 700 }}>Cancel</button>
                  <button onClick={executeWithdrawal} style={{ flex: 1, padding: '16px', borderRadius: '16px', backgroundColor: 'var(--terracotta-primary)', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 800 }}>Confirm</button>
                </div>
             </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Premium View Details Modal */}
      <AnimatePresence>
        {selectedOrderForDetails && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            style={{
              position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
              backgroundColor: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '24px'
            }}
          >
            <motion.div initial={{ scale: 0.9, opacity: 0, y: 20 }} animate={{ scale: 1, opacity: 1, y: 0 }} exit={{ scale: 0.9, opacity: 0, y: 20 }}
              style={{
                backgroundColor: '#fff', borderRadius: '28px', padding: '32px',
                width: '100%', maxWidth: '540px', maxHeight: '90vh', overflowY: 'auto',
                boxShadow: '0 24px 48px rgba(0,0,0,0.2)', position: 'relative'
              }}
            >
              <button onClick={() => setSelectedOrderForDetails(null)}
                style={{
                  position: 'absolute', top: '24px', right: '24px', background: '#f3f4f6', border: 'none', borderRadius: '50%',
                  width: '40px', height: '40px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'background 0.2s'
                }}
              >
                <X size={20} color="#4b5563" />
              </button>

              <div style={{ marginBottom: '32px', paddingRight: '48px' }}>
                <h2 style={{ margin: '0 0 8px 0', fontSize: '28px', fontWeight: 900, color: '#111827', letterSpacing: '-0.5px' }}>
                  Transport Details
                </h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <span style={{ fontSize: '14px', color: '#6b7280', fontWeight: 700 }}>
                    ID: {selectedOrderForDetails.orderId || 'N/A'}
                  </span>
                  <span style={{ padding: '4px 10px', backgroundColor: '#e8f5e9', color: '#2e7d32', borderRadius: '20px', fontSize: '11px', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <CheckCircle size={12} /> DELIVERED
                  </span>
                </div>
              </div>
              
              {loadingProfiles ? (
                <div style={{ padding: '40px 0', display: 'flex', justifyContent: 'center' }}><AgriLoading size={32} /></div>
              ) : getOrderType(selectedOrderForDetails.orderId) === 'POOL' ? (
                <>
                  {/* Crop Pool Layout */}
                  <div style={{ marginBottom: '24px', border: '1px solid #f3f4f6', borderRadius: '24px', padding: '24px', background: '#fff', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
                    <h3 style={{ margin: '0 0 20px', fontSize: '13px', fontWeight: 800, color: 'var(--terracotta-primary)', textTransform: 'uppercase', letterSpacing: '1px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ width: '24px', height: '2px', background: 'var(--terracotta-primary)', borderRadius: '2px' }} />
                      Host Farmer
                    </h3>
                    
                    <div style={{ display: 'flex', gap: '20px', marginBottom: '24px', alignItems: 'center' }}>
                      <UserAvatar src={hostDetails?.profileImageUrl} alt="Host" size={72} style={{ borderRadius: '50%', boxShadow: '0 8px 16px rgba(0,0,0,0.08)' }} />
                      <div>
                        <div style={{ fontSize: '18px', fontWeight: 900, color: '#111' }}>{hostDetails?.name && hostDetails?.name !== 'Farmer' ? hostDetails.name : 'Unknown Farmer'}</div>
                        <div style={{ fontSize: '14px', color: '#4b5563', fontWeight: 600, marginTop: '2px' }}>{hostDetails?.phone || 'N/A'}</div>
                        <div style={{ fontSize: '13px', color: '#9ca3af', fontWeight: 500, marginTop: '2px' }}>{selectedOrderForDetails.hostEmail || selectedOrderForDetails.farmerEmail}</div>
                      </div>
                    </div>

                    <div style={{ background: '#f9fafb', borderRadius: '16px', padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '12px', alignItems: 'start' }}>
                        <MapPin size={16} color="#6b7280" style={{ marginTop: '2px' }} />
                        <div style={{ fontSize: '14px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>
                          <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', marginBottom: '2px' }}>Pickup</div>
                          {selectedOrderForDetails.pickupAddress}
                        </div>
                      </div>
                      <div style={{ width: '2px', height: '12px', background: '#e5e7eb', marginLeft: '7px', marginTop: '-8px', marginBottom: '-8px' }} />
                      <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '12px', alignItems: 'start' }}>
                        <MapPin size={16} color="var(--terracotta-primary)" style={{ marginTop: '2px' }} />
                        <div style={{ fontSize: '14px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>
                          <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', marginBottom: '2px' }}>Drop</div>
                          {selectedOrderForDetails.dropAddress}
                        </div>
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginTop: '16px', padding: '0 8px' }}>
                      <div>
                        <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Crop</div>
                        <div style={{ fontSize: '15px', color: '#111', fontWeight: 800 }}>{selectedOrderForDetails.cropName} <span style={{ color: '#6b7280', fontSize: '13px', fontWeight: 600 }}>({selectedOrderForDetails.weightKg} kg)</span></div>
                      </div>
                      <div>
                        <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Host Transport Payment</div>
                        <div style={{ fontSize: '16px', color: 'var(--terracotta-primary)', fontWeight: 900 }}>{formatCurrency(selectedOrderForDetails.totalPayment)}</div>
                      </div>
                    </div>
                  </div>

                  {selectedOrderForDetails.coLoaderEmail && (
                    <div style={{ marginBottom: '24px', border: '1px solid #f3f4f6', borderRadius: '24px', padding: '24px', background: '#fff', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
                      <h3 style={{ margin: '0 0 20px', fontSize: '13px', fontWeight: 800, color: 'var(--golden-yellow)', textTransform: 'uppercase', letterSpacing: '1px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ width: '24px', height: '2px', background: 'var(--golden-yellow)', borderRadius: '2px' }} />
                        Co-loader Farmer
                      </h3>
                      <div style={{ display: 'flex', gap: '20px', marginBottom: '24px', alignItems: 'center' }}>
                        <UserAvatar src={coLoaderDetails?.profileImageUrl} alt="Co-loader" size={72} style={{ borderRadius: '50%', boxShadow: '0 8px 16px rgba(0,0,0,0.08)' }} />
                        <div>
                          <div style={{ fontSize: '18px', fontWeight: 900, color: '#111' }}>{coLoaderDetails?.name && coLoaderDetails?.name !== 'Farmer' ? coLoaderDetails.name : 'Unknown Farmer'}</div>
                          <div style={{ fontSize: '14px', color: '#4b5563', fontWeight: 600, marginTop: '2px' }}>{coLoaderDetails?.phone || 'N/A'}</div>
                          <div style={{ fontSize: '13px', color: '#9ca3af', fontWeight: 500, marginTop: '2px' }}>{selectedOrderForDetails.coLoaderEmail}</div>
                        </div>
                      </div>

                      <div style={{ background: '#f9fafb', borderRadius: '16px', padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '12px', alignItems: 'start' }}>
                          <MapPin size={16} color="#6b7280" style={{ marginTop: '2px' }} />
                          <div style={{ fontSize: '14px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>
                            <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', marginBottom: '2px' }}>Pickup</div>
                            {selectedOrderForDetails.coLoaderPickupAddress}
                          </div>
                        </div>
                        <div style={{ width: '2px', height: '12px', background: '#e5e7eb', marginLeft: '7px', marginTop: '-8px', marginBottom: '-8px' }} />
                        <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '12px', alignItems: 'start' }}>
                          <MapPin size={16} color="var(--terracotta-primary)" style={{ marginTop: '2px' }} />
                          <div style={{ fontSize: '14px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>
                            <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', marginBottom: '2px' }}>Drop</div>
                            {selectedOrderForDetails.coLoaderDropAddress}
                          </div>
                        </div>
                      </div>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginTop: '16px', padding: '0 8px' }}>
                        <div>
                          <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Crop</div>
                          <div style={{ fontSize: '15px', color: '#111', fontWeight: 800 }}>{selectedOrderForDetails.coLoaderCropName} <span style={{ color: '#6b7280', fontSize: '13px', fontWeight: 600 }}>({selectedOrderForDetails.coLoaderWeightKg} kg)</span></div>
                        </div>
                        <div>
                          <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Co-loader Transport Payment</div>
                          <div style={{ fontSize: '16px', color: 'var(--terracotta-primary)', fontWeight: 900 }}>{formatCurrency(selectedOrderForDetails.coLoaderPayment)}</div>
                        </div>
                      </div>
                    </div>
                  )}

                  <div style={{ borderTop: '2px dashed #e5e7eb', paddingTop: '24px' }}>
                    <h3 style={{ margin: '0 0 16px', fontSize: '14px', fontWeight: 800, color: '#374151', textTransform: 'uppercase' }}>Summary</h3>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                      <span style={{ color: '#6b7280', fontWeight: 600 }}>Vehicle</span>
                      <span style={{ color: '#111', fontWeight: 800 }}>{selectedOrderForDetails.vehicleType || 'Crop Pool'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                      <span style={{ color: '#6b7280', fontWeight: 600 }}>Completion Time</span>
                      <span style={{ color: '#111', fontWeight: 700 }}>{new Date(selectedOrderForDetails.timestamp).toLocaleString()}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '16px', paddingTop: '16px', borderTop: '1px solid #f3f4f6' }}>
                      <span style={{ fontSize: '16px', color: '#111', fontWeight: 800 }}>Total Transport Cost<br/><span style={{fontSize: '12px', color: '#6b7280'}}>(Driver Earnings)</span></span>
                      <span style={{ fontSize: '20px', color: '#2e7d32', fontWeight: 900 }}>
                        {formatCurrency((selectedOrderForDetails.totalPayment || 0) + (selectedOrderForDetails.coLoaderPayment || 0))}
                      </span>
                    </div>
                  </div>
                </>
              ) : (
                <>
                  {/* Standard Order Layout */}
                  <div style={{ marginBottom: '24px', border: '1px solid #f3f4f6', borderRadius: '24px', padding: '24px', background: '#fff', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
                    <h3 style={{ margin: '0 0 20px', fontSize: '13px', fontWeight: 800, color: 'var(--terracotta-primary)', textTransform: 'uppercase', letterSpacing: '1px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ width: '24px', height: '2px', background: 'var(--terracotta-primary)', borderRadius: '2px' }} />
                      Farmer
                    </h3>
                    
                    <div style={{ display: 'flex', gap: '20px', marginBottom: '24px', alignItems: 'center' }}>
                      <UserAvatar src={farmerDetails?.profileImageUrl} alt="Farmer" size={72} style={{ borderRadius: '50%', boxShadow: '0 8px 16px rgba(0,0,0,0.08)' }} />
                      <div>
                        <div style={{ fontSize: '18px', fontWeight: 900, color: '#111' }}>{farmerDetails?.name && farmerDetails?.name !== 'Farmer' ? farmerDetails.name : 'Unknown Farmer'}</div>
                        <div style={{ fontSize: '14px', color: '#4b5563', fontWeight: 600, marginTop: '2px' }}>{farmerDetails?.phone || 'N/A'}</div>
                        <div style={{ fontSize: '13px', color: '#9ca3af', fontWeight: 500, marginTop: '2px' }}>{selectedOrderForDetails.farmerEmail}</div>
                      </div>
                    </div>
                    
                    <div style={{ background: '#f9fafb', borderRadius: '16px', padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '12px', alignItems: 'start' }}>
                        <MapPin size={16} color="#6b7280" style={{ marginTop: '2px' }} />
                        <div style={{ fontSize: '14px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>
                          <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', marginBottom: '2px' }}>Pickup</div>
                          {selectedOrderForDetails.pickupAddress}
                        </div>
                      </div>
                    </div>
                    
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginTop: '16px', padding: '0 8px' }}>
                      <div>
                        <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Crop</div>
                        <div style={{ fontSize: '15px', color: '#111', fontWeight: 800 }}>{selectedOrderForDetails.cropName} <span style={{ color: '#6b7280', fontSize: '13px', fontWeight: 600 }}>({selectedOrderForDetails.weightKg} kg)</span></div>
                      </div>
                      <div>
                        <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 600, marginBottom: '4px' }}>Farmer Earnings</div>
                        <div style={{ fontSize: '16px', color: '#2e7d32', fontWeight: 900 }}>{formatCurrency(selectedOrderForDetails.cropValue)}</div>
                      </div>
                    </div>
                  </div>

                  <div style={{ marginBottom: '24px', border: '1px solid #f3f4f6', borderRadius: '24px', padding: '24px', background: '#fff', boxShadow: '0 4px 12px rgba(0,0,0,0.02)' }}>
                    <h3 style={{ margin: '0 0 20px', fontSize: '13px', fontWeight: 800, color: 'var(--golden-yellow)', textTransform: 'uppercase', letterSpacing: '1px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ width: '24px', height: '2px', background: 'var(--golden-yellow)', borderRadius: '2px' }} />
                      User (Buyer)
                    </h3>
                    <div style={{ display: 'flex', gap: '20px', marginBottom: '24px', alignItems: 'center' }}>
                      <UserAvatar src={userDetails?.profileImageUrl} alt="User" size={72} style={{ borderRadius: '50%', boxShadow: '0 8px 16px rgba(0,0,0,0.08)' }} />
                      <div>
                        <div style={{ fontSize: '18px', fontWeight: 900, color: '#111' }}>{userDetails?.name || 'Unknown User'}</div>
                        <div style={{ fontSize: '14px', color: '#4b5563', fontWeight: 600, marginTop: '2px' }}>{userDetails?.phone || 'N/A'}</div>
                        <div style={{ fontSize: '13px', color: '#9ca3af', fontWeight: 500, marginTop: '2px' }}>{selectedOrderForDetails.userEmail}</div>
                      </div>
                    </div>
                    <div style={{ background: '#f9fafb', borderRadius: '16px', padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '12px', alignItems: 'start' }}>
                        <MapPin size={16} color="var(--terracotta-primary)" style={{ marginTop: '2px' }} />
                        <div style={{ fontSize: '14px', color: '#374151', fontWeight: 600, lineHeight: 1.4 }}>
                          <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 700, textTransform: 'uppercase', marginBottom: '2px' }}>Drop</div>
                          {selectedOrderForDetails.dropAddress}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div style={{ borderTop: '2px dashed #e5e7eb', paddingTop: '24px' }}>
                    <h3 style={{ margin: '0 0 16px', fontSize: '14px', fontWeight: 800, color: '#374151', textTransform: 'uppercase' }}>Driver Summary</h3>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                      <span style={{ color: '#6b7280', fontWeight: 600 }}>Vehicle</span>
                      <span style={{ color: '#111', fontWeight: 800 }}>{selectedOrderForDetails.vehicleType || 'N/A'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                      <span style={{ color: '#6b7280', fontWeight: 600 }}>Completion Time</span>
                      <span style={{ color: '#111', fontWeight: 700 }}>{new Date(selectedOrderForDetails.timestamp).toLocaleString()}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '16px', paddingTop: '16px', borderTop: '1px solid #f3f4f6' }}>
                      <span style={{ fontSize: '16px', color: '#111', fontWeight: 800 }}>Transport Bill<br/><span style={{fontSize: '12px', color: '#6b7280'}}>(Driver Earnings)</span></span>
                      <span style={{ fontSize: '20px', color: 'var(--terracotta-primary)', fontWeight: 900 }}>
                        {formatCurrency(selectedOrderForDetails.transportFare)}
                      </span>
                    </div>
                  </div>
                </>
              )}
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {toastMsg && (
          <motion.div initial={{ opacity: 0, y: 50, x: '-50%' }} animate={{ opacity: 1, y: 0, x: '-50%' }} exit={{ opacity: 0, y: 50, x: '-50%' }}
            style={{ position: 'fixed', bottom: '24px', left: '50%', backgroundColor: '#111827', color: '#fff', padding: '12px 24px', borderRadius: '12px', boxShadow: '0 8px 16px rgba(0,0,0,0.2)', zIndex: 9999, fontSize: '14px', fontWeight: 700 }}
          >
            {toastMsg}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default DriverWallet;
