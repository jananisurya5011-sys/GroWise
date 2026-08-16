import React from 'react';
import { CheckCircle, Truck, Leaf, Handshake, AlertTriangle, ArrowRight, Play, Pause, Download, MapPin } from 'lucide-react';
import { motion } from 'framer-motion';
import { formatCurrency } from '../../utils/constants';

// --- Shared Generic Components ---

export const DealHeader = ({ title, isSent }) => (
  <h4 style={{ margin: '0 0 16px 0', fontSize: '15px', fontWeight: 800, color: 'var(--terracotta-primary)' }}>
    {isSent ? `Sent ${title}` : `Received ${title}`}
  </h4>
);

export const ProductPreview = ({ imageUrl, cropName, children }) => {
  let fullUrl = imageUrl ? (imageUrl.startsWith("http") ? imageUrl : `http://localhost:5000${imageUrl}`) : 'https://placehold.co/100x100/FFF9F5/D85A38?text=Crop';
  
  return (
    <div style={{ display: 'flex', gap: '16px', marginBottom: '16px' }}>
      <img 
        src={fullUrl} 
        alt={cropName || "Crop"} 
        style={{ width: '80px', height: '80px', borderRadius: '12px', objectFit: 'cover', border: '1px solid rgba(253, 185, 49, 0.4)' }} 
        onError={(e) => { e.target.onerror = null; e.target.src = 'https://placehold.co/100x100/FFF9F5/D85A38?text=Crop'; }}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <p style={{ margin: '0 0 4px 0', fontWeight: 900, fontSize: '24px', color: '#1A1A1A', textTransform: 'capitalize' }}>{cropName}</p>
        {children}
      </div>
    </div>
  );
};

export const QuantitySection = ({ kg }) => (
  <p style={{ margin: '0 0 4px 0', fontSize: '13px', color: '#666' }}>Required Quantity: {kg} kg</p>
);

export const PriceSection = ({ msg }) => {
  if (msg?.dealType === 'DONATION' || msg?.type === 'DONATION_REQUEST') return null;
  return (
    <>
      <p style={{ margin: '0 0 2px 0', fontSize: '13px', color: '#666' }}>Market Rate: {formatCurrency((parseFloat(msg?.basePrice) || 0).toFixed(2))}/kg</p>
      <p style={{ margin: '0 0 2px 0', fontSize: '13px', color: '#666' }}>
        {msg?.type === 'COUNTER_CARD' ? 'Counter Price: ' : 'Offer: '} 
        <span style={{ color: 'var(--terracotta-primary)', fontWeight: 700 }}>{formatCurrency((parseFloat(msg?.targetPrice) || 0).toFixed(2))}/kg</span>
      </p>
      <p style={{ margin: 0, fontSize: '14px', fontWeight: 800, color: '#2E7D32' }}>Final Value: {formatCurrency((parseFloat(msg?.targetPrice || 0) * parseFloat(msg?.kg || 0)).toFixed(2))}</p>
      {msg?.reason && <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: '#999', fontStyle: 'italic' }}>"{msg?.reason}"</p>}
    </>
  );
};

export const StatusBadge = ({ msg, isSent }) => {
  if (!msg) return null;
  const isDonation = msg.dealType === 'DONATION' || msg.type === 'DONATION_REQUEST';
  let statusText = msg.status;
  
  if (isDonation) {
    if (msg.status === 'PENDING') {
      statusText = 'Waiting for Farmer Response';
    } else if (msg.status === 'ACCEPTED') {
      statusText = 'Accepted';
    } else if (msg.status === 'PICKUP_ADDRESS_SUBMITTED') {
      statusText = isSent ? 'Pickup Address Submitted' : 'Pickup Address Received';
    } else if (msg.status === 'TRANSPORT_SELECTED_DELIVERY') {
      statusText = 'Delivery Partner Selected';
    } else if (msg.status === 'TRANSPORT_SELECTED_SELF') {
      statusText = isSent ? 'Self Service Selected' : 'Mode Selected (Self Service)';
    } else if (msg.status === 'INVOICE_GENERATED') {
      statusText = 'Invoice Generated';
    } else if (msg.status === 'PAYMENT_COMPLETED') {
      statusText = 'Payment Completed';
    } else if (msg.status === 'ORDER_CREATED') {
      statusText = 'Order Created';
    } else if (msg.status === 'PICKED_UP') {
      statusText = 'Picked Up';
    } else if (msg.status === 'COMPLETED') {
      statusText = 'Completed';
    }
  } else {
    if (msg.status === 'ACCEPTED') {
      statusText = 'Accepted';
    } else if (msg.status === 'COUNTERED') {
      statusText = 'Countered';
    } else if (msg.status === 'DECLINED') {
      statusText = 'Declined';
    }
  }

  const statusColorMap = {
    'ACCEPTED': '#4CAF50',
    'COUNTERED': '#F57C00',
    'DECLINED': '#9e9e9e',
    'PENDING': 'var(--terracotta-primary, #D85A38)',
    'PAID': '#4CAF50',
    'WITHDRAWN': '#f44336',
    'INVOICED': '#4CAF50',
    'SELECTED': '#2196F3',
    'PICKUP_ADDRESS_SUBMITTED': '#FF9800',
    'TRANSPORT_SELECTED_DELIVERY': '#2196F3',
    'TRANSPORT_SELECTED_SELF': '#2196F3',
    'INVOICE_GENERATED': '#4CAF50',
    'PAYMENT_COMPLETED': '#4CAF50',
    'ORDER_CREATED': '#4CAF50',
    'PICKED_UP': '#4CAF50',
    'COMPLETED': '#4CAF50'
  };
  const statusColor = statusColorMap[msg.status] || statusColorMap['PENDING'];
  
  return msg.status !== 'PENDING' || isDonation ? (
    <p style={{ margin: '12px 0 0 0', fontSize: '13px', fontWeight: 800, color: statusColor }}>
      Status: {statusText}
    </p>
  ) : null;
};

export const PrimaryActions = ({ msg, isSent, isFarmer, onAccept, onCounter, onDecline, onWithdraw }) => {
  if (msg?.status !== 'PENDING') return null;
  
  if (isSent) {
    return (
      <div style={{ marginTop: '16px' }}>
        <button onClick={() => onWithdraw(msg)} style={{ background: 'none', border: 'none', color: '#f44336', fontWeight: 700, fontSize: '14px', cursor: 'pointer', padding: 0 }}>Withdraw</button>
      </div>
    );
  }
  
  const isDonation = msg?.dealType === 'DONATION' || msg?.type === 'DONATION_REQUEST';

  return (
    <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
      <button onClick={() => onAccept(msg)} style={{ flex: 1, padding: '12px', background: '#4CAF50', color: 'white', fontWeight: 700, borderRadius: '12px', border: 'none', cursor: 'pointer' }}>Accept</button>
      {!isDonation && (
        <button onClick={() => onCounter(msg)} style={{ flex: 1, padding: '12px', background: 'var(--terracotta-primary)', color: 'white', fontWeight: 700, borderRadius: '12px', border: 'none', cursor: 'pointer' }}>Counter</button>
      )}
      <button onClick={() => onDecline(msg)} style={{ flex: 1, padding: '12px', background: '#f5f5f5', color: '#666', fontWeight: 700, borderRadius: '12px', border: 'none', cursor: 'pointer' }}>Decline</button>
    </div>
  );
};

export const LocationRequestSection = ({ msg, isAccepted, isFarmer, onLocation }) => {
  const isDonation = msg?.dealType === 'DONATION' || msg?.type === 'DONATION_REQUEST';

  if (isDonation) {
    const needsPickup = isAccepted && isFarmer && !msg?.pickupAddress;
    return (
      <>
        {needsPickup && (
          <div style={{ marginTop: '16px' }}>
            <button onClick={() => onLocation(msg)} style={{ width: '100%', padding: '12px', background: 'var(--golden-yellow)', color: '#333', fontWeight: 800, borderRadius: '12px', border: 'none', cursor: 'pointer' }}>
              Set Pickup Address
            </button>
          </div>
        )}
      </>
    );
  }

  // Marketplace Logic
  const needsLocation = isAccepted && ((isFarmer && !msg?.pickupAddress) || (!isFarmer && !msg?.dropAddress));
  const waitingOnOther = isAccepted && ((isFarmer && msg?.pickupAddress && !msg?.dropAddress) || (!isFarmer && msg?.dropAddress && !msg?.pickupAddress));
  
  return (
    <>
      {needsLocation && (
        <div style={{ marginTop: '16px' }}>
          <button onClick={() => onLocation(msg)} style={{ width: '100%', padding: '12px', background: 'var(--golden-yellow)', color: '#333', fontWeight: 800, borderRadius: '12px', border: 'none', cursor: 'pointer' }}>
            Set {isFarmer ? 'Pickup' : 'Drop'} Location
          </button>
        </div>
      )}
      {waitingOnOther && (
        <p style={{ margin: '12px 0 0 0', fontSize: '12px', color: '#F57C00', fontWeight: 600 }}>
          Waiting for {isFarmer ? 'buyer' : 'farmer'} to set location...
        </p>
      )}
    </>
  );
};

// --- Deal Cards ---

export const DealRequestCard = ({ msg, isMe, isFarmer, onAccept, onCounter, onDecline, onWithdraw, onLocation }) => {
  const isDonation = msg?.dealType === 'DONATION' || msg?.type === 'DONATION_REQUEST';
  let typeText = msg?.type === 'COUNTER_CARD' ? 'Counter-Offer' : 'Deal Request';
  if (isDonation) typeText = 'Donation Request';

  return (
    <div className={`w-full max-w-sm my-4 rounded-[18px] bg-white shadow-sm overflow-hidden ${isMe ? 'self-end' : 'self-start'}`} style={{ border: '1px solid var(--golden-yellow)', padding: '16px' }}>
      <DealHeader title={typeText} isSent={isMe} />
      
      <ProductPreview imageUrl={msg?.imageUrl} cropName={msg?.cropName}>
        <PriceSection msg={msg} />
        <QuantitySection kg={msg?.kg} />
      </ProductPreview>
      
      <StatusBadge msg={msg} isSent={isMe} />
      <PrimaryActions msg={msg} isSent={isMe} isFarmer={isFarmer} onAccept={onAccept} onCounter={onCounter} onDecline={onDecline} onWithdraw={onWithdraw} />
      <LocationRequestSection msg={msg} isAccepted={msg?.status === 'ACCEPTED' || msg?.status === 'LOCATION_UPDATED'} isFarmer={isFarmer} onLocation={onLocation} />
    </div>
  );
};

// Aliases for compatibility
export const CounterOfferCard = DealRequestCard;
export const DonationRequestCard = DealRequestCard;

export const LogisticsChoiceCard = ({ msg, isMe, onSelectLogistics }) => {
  return (
    <div className={`w-full max-w-sm my-4 rounded-[18px] shadow-sm bg-white overflow-hidden ${isMe ? 'self-end' : 'self-start'}`} style={{ border: '1px solid var(--golden-yellow)', padding: '16px' }}>
      <DealHeader title="Logistics Pending" isSent={isMe} />
      <div style={{ fontSize: '14px', color: '#333' }}>
        <p style={{ marginBottom: '16px' }}>How would you like to transport <strong>{msg?.kg} kg</strong> of <strong style={{ textTransform: 'capitalize' }}>{msg?.cropName}</strong>?</p>
        
        {!isMe && msg?.status !== 'SELECTED' && msg?.status !== 'INVOICED' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
             <button onClick={() => onSelectLogistics(msg, 'GROWISE')} style={{ width: '100%', padding: '12px', background: 'var(--terracotta-primary)', color: 'white', fontWeight: 800, borderRadius: '12px', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
               <Truck size={18}/> GroWise Delivery (Paid)
             </button>
             <button onClick={() => onSelectLogistics(msg, 'SELF')} style={{ width: '100%', padding: '12px', background: 'white', color: '#333', fontWeight: 800, borderRadius: '12px', border: '2px solid var(--golden-yellow)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
               <Handshake size={18}/> Self Service (Free)
             </button>
          </div>
        )}
        {(msg?.status === 'SELECTED' || msg?.status === 'INVOICED') && (
          <div style={{ padding: '12px', background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '12px', color: '#065f46', fontWeight: 800, textAlign: 'center', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
            <CheckCircle size={18}/> Logistics Selected
          </div>
        )}
      </div>
    </div>
  );
};

export const AudioMessageCard = ({ msg, isMe }) => {
  const [isPlaying, setIsPlaying] = React.useState(false);
  const audioRef = React.useRef(null);

  const togglePlay = () => {
    if (audioRef.current) {
      if (isPlaying) {
        audioRef.current.pause();
      } else {
        audioRef.current.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  return (
    <div className={`flex flex-col max-w-[250px] ${isMe ? 'self-end' : 'self-start'} my-2`}>
      <div className={`flex items-center gap-3 px-4 py-3 rounded-[24px] shadow-sm ${isMe ? 'bg-[#c2845c] text-white rounded-br-sm' : 'bg-white text-gray-800 rounded-bl-sm border border-gray-100'}`}>
        <button onClick={togglePlay} className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${isMe ? 'bg-white/20 hover:bg-white/30 text-white' : 'bg-[#F2A33A]/10 hover:bg-[#F2A33A]/20 text-[#F2A33A]'}`}>
          {isPlaying ? <Pause size={18} fill="currentColor"/> : <Play size={18} fill="currentColor" className="ml-1"/>}
        </button>
        <div className="flex-1">
           <div className={`h-1.5 w-full rounded-full ${isMe ? 'bg-white/30' : 'bg-gray-200'} relative`}>
              <div className={`absolute left-0 top-0 bottom-0 rounded-full ${isMe ? 'bg-white' : 'bg-[#F2A33A]'} w-0`}></div>
           </div>
        </div>
        <audio 
          ref={audioRef} 
          src={msg.audioUrl} 
          onEnded={() => setIsPlaying(false)} 
          onPause={() => setIsPlaying(false)} 
          className="hidden" 
        />
      </div>
      <div className={`flex items-center gap-1 mt-1 px-2 text-[10px] font-bold text-gray-400 ${isMe ? 'justify-end' : 'justify-start'}`}>
        {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
      </div>
    </div>
  );
};

export const SystemMessageCard = ({ msg }) => {
  return (
    <div className="w-full flex justify-center my-2">
      <div className="bg-gray-100 border border-gray-200 px-4 py-2 rounded-xl text-xs font-semibold text-gray-500 flex items-center gap-2">
        <AlertTriangle size={14} color="#9ca3af" />
        {msg.text || msg.event || "System Update"}
      </div>
    </div>
  );
};

export const DonationOrderCard = ({ msg, isMe, isFarmer, onAcceptDonation, onDeclineDonation, onSelectLogistics, onLocation }) => {
  const status = msg?.status ?? 'PENDING';
  let statusText = status;
  if (status === 'PENDING_FARMER_APPROVAL' || status === 'PENDING') statusText = 'Pending Farmer Approval';
  else if (status === 'WAITING_FOR_TRANSPORT_SELECTION') statusText = 'Choose Transport';
  else if (status === 'INVOICED') statusText = 'Ready for Payment';
  
  const statusColorMap = {
    'PENDING_FARMER_APPROVAL': '#8B4513',
    'PENDING': '#8B4513',
    'WAITING_FOR_TRANSPORT_SELECTION': '#C96A16',
    'INVOICED': '#C96A16',
    'READY_FOR_PICKUP': '#2E7D32',
    'COMPLETED': '#2E7D32',
    'CANCELLED': '#D32F2F'
  };
  const statusColor = statusColorMap[status] || '#8B4513';
  const ngoName = msg?.userName ?? msg?.userEmail?.split('@')[0] ?? 'NGO';

  return (
    <motion.div 
      initial={{ opacity: 0, y: 15 }} 
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className={`w-full max-w-sm my-4 rounded-[18px] bg-white shadow-sm overflow-hidden ${isMe ? 'self-end' : 'self-start'}`} 
      style={{ border: '1px solid rgba(253, 185, 49, 0.4)', padding: '16px' }}
    >
      <DealHeader title="Donation Request" isSent={isMe} />
      {msg?.orderId && <p style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 700, color: '#7A7A7A', textAlign: 'center' }}>Order ID: {msg.orderId}</p>}
      
      <ProductPreview imageUrl={msg?.imageUrl ?? ""} cropName={msg?.cropName ?? "Crop"}>
        <QuantitySection kg={msg?.kg ?? msg?.weightKg ?? 0} />
      </ProductPreview>
      
      {msg?.pickupAddress && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '12px 0 0 0', padding: '12px', background: '#F9FAF8', borderRadius: '12px', border: '1px solid #EEEEEE' }}>
          <MapPin size={16} color="#C96A16" />
          <p style={{ margin: 0, fontSize: '13px', color: '#333', fontWeight: 600, flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{msg.pickupAddress}</p>
        </div>
      )}
      
      <div style={{ background: '#FFF9F5', padding: '8px 12px', borderRadius: '8px', border: '1px dashed rgba(253, 185, 49, 0.4)', marginTop: '12px' }}>
        <p style={{ margin: 0, fontSize: '14px', fontWeight: 800, color: statusColor }}>
          Status: {statusText}
        </p>
      </div>

      {(status === 'PENDING_FARMER_APPROVAL' || status === 'PENDING') && isFarmer && (
        <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
          <button onClick={() => onDeclineDonation(msg)} style={{ flex: 1, padding: '12px', background: 'transparent', color: '#D32F2F', fontWeight: 700, borderRadius: '12px', border: '2px solid #D32F2F', cursor: 'pointer' }}>Decline</button>
          <button onClick={() => onAcceptDonation(msg)} style={{ flex: 1.5, padding: '12px', background: 'var(--terracotta-primary)', color: 'white', fontWeight: 700, borderRadius: '12px', border: 'none', cursor: 'pointer', boxShadow: '0 4px 12px rgba(216, 90, 56, 0.2)' }}>Accept Donation</button>
        </div>
      )}

      {status === 'WAITING_FOR_TRANSPORT_SELECTION' && (
         <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '16px' }}>
            {isFarmer ? (
                (!msg?.pickupAddress || msg.pickupLat === 0) ? (
                    <button onClick={() => onLocation(msg)} style={{ width: '100%', padding: '12px', background: 'var(--terracotta-primary)', color: 'white', fontWeight: 800, borderRadius: '12px', border: 'none', cursor: 'pointer' }}>Set Pickup Location</button>
                ) : (
                    <>
                        <button onClick={() => onLocation(msg)} style={{ width: '100%', padding: '12px', background: 'white', color: 'var(--terracotta-primary)', fontWeight: 800, borderRadius: '12px', border: '1px solid var(--terracotta-primary)', cursor: 'pointer' }}>Edit Pickup Location</button>
                        <p style={{ margin: 0, fontSize: '13px', color: '#666', textAlign: 'center' }}>Waiting for NGO to choose transport...</p>
                    </>
                )
            ) : (
                (!msg?.pickupAddress || msg.pickupLat === 0) ? (
                    <p style={{ margin: 0, fontSize: '13px', color: '#666', textAlign: 'center' }}>Waiting for Farmer to set Pickup Location...</p>
                ) : (
                    <>
                       <button onClick={() => onSelectLogistics(msg, 'GROWISE')} style={{ width: '100%', padding: '12px', background: 'var(--terracotta-primary)', color: 'white', fontWeight: 800, borderRadius: '12px', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', boxShadow: '0 4px 12px rgba(216, 90, 56, 0.2)' }}>
                         <Truck size={18}/> GroWise Delivery (Paid)
                       </button>
                       <button onClick={() => onSelectLogistics(msg, 'SELF')} style={{ width: '100%', padding: '12px', background: 'white', color: '#333', fontWeight: 800, borderRadius: '12px', border: '2px solid var(--golden-yellow)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                         <Handshake size={18}/> Self Service (Free)
                       </button>
                    </>
                )
            )}
         </div>
      )}

      {status === 'READY_FOR_PICKUP' && msg?.pickupOtp && (
        <div style={{ marginTop: '16px', background: isFarmer ? '#FFF3E0' : '#E8F5E9', borderRadius: '8px', padding: '12px', textAlign: 'center', border: `2px solid ${isFarmer ? '#FF9800' : '#4CAF50'}` }}>
          {!isFarmer ? (
            <>
              <p style={{ margin: 0, fontSize: '11px', fontWeight: 800, color: '#2E7D32', textTransform: 'uppercase' }}>Your Pickup OTP</p>
              <h1 style={{ margin: '8px 0', fontSize: '32px', fontWeight: 900, color: '#2E7D32', letterSpacing: '4px' }}>{msg.pickupOtp}</h1>
              <p style={{ margin: 0, fontSize: '12px', color: '#666' }}>Share with Farmer on arrival</p>
            </>
          ) : (
            <>
              <p style={{ margin: 0, fontSize: '11px', fontWeight: 800, color: '#E65100', textTransform: 'uppercase' }}>OTP Generated</p>
              <h3 style={{ margin: '8px 0', fontSize: '16px', fontWeight: 700, color: '#E65100' }}>Pickup OTP has been generated for the NGO.</h3>
              <p style={{ margin: 0, fontSize: '12px', color: '#666' }}>The NGO will share it with you on arrival.</p>
            </>
          )}
        </div>
      )}
    </motion.div>
  );
};

