import React, { useEffect, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate, useParams } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import gsap from 'gsap';
import { ArrowLeft, MessageCircle, Star, Filter, Handshake, ChevronRight, X, Loader2, Edit, Trash2, Heart, Clock } from 'lucide-react';
import { calculateExpiryStatus } from '../../utils/inventoryHelpers';
import { collection, query, orderBy, onSnapshot, addDoc, setDoc, doc, deleteDoc, updateDoc } from 'firebase/firestore';
import { db } from '../../utils/firebase';
import { getEffectivePrice } from '../../utils/pricing';
import { formatCurrency } from '../../utils/constants';

const FullScreenLoader = () => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', width: '100%', position: 'fixed', top: 0, left: 0, backgroundColor: '#fff', zIndex: 9999 }}>
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" style={{ width: '80px', height: '80px', animation: 'spin 1.5s linear infinite' }}>
      <path
          d="M12,2C6.48,2 2,6.48 2,12C2,17.52 6.48,22 12,22C17.52,22 22,17.52 22,12C22,6.48 17.52,2 12,2ZM12,18C11.45,18 11,17.55 11,17C11,15.2 12.2,13.6 13.8,12.4C12.4,12.2 10.9,12.7 9.8,13.8C9.4,14.2 8.8,14.2 8.4,13.8C8,13.4 8,12.8 8.4,12.4C10.1,10.7 12.6,10.1 14.8,10.7C14.4,8.5 13,6.6 11,5.6C10.5,5.35 10.3,4.75 10.55,4.25C10.8,3.75 11.4,3.55 11.9,3.8C14.7,5.2 16.5,7.95 16.9,11C18.6,12.5 19.6,14.7 19.6,17C19.6,17.55 19.15,18 18.6,18L12,18Z"
          fill="#F2A33A"
      />
      <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
    </svg>
  </div>
);

// Fallback GroWise logo from Android's app_logo.xml
const appLogoDataUri = '/app_logo.svg';

const FarmerMenu = () => {
  const navigate = useNavigate();
  const { farmerEmail } = useParams();
  const { user } = useAuth();
  
  const [farmerProfile, setFarmerProfile] = useState(null);
  const [inventory, setInventory] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  
  const [reviewsList, setReviewsList] = useState([]);
  const [averageRating, setAverageRating] = useState(0);
  const [isReviewsLoading, setIsReviewsLoading] = useState(true);
  
  const categories = ["All", "Vegetables", "Fruits", "Grains", "Flowers"];
  const [selectedCategory, setSelectedCategory] = useState("All");
  
  // Negotiation Modal State
  const [showNegotiationDialog, setShowNegotiationDialog] = useState(false);
  const [selectedItemForDeal, setSelectedItemForDeal] = useState(null);
  const [inputKgStr, setInputKgStr] = useState("");
  const [inputTargetPriceStr, setInputTargetPriceStr] = useState("");
  const [negotiationStep, setNegotiationStep] = useState(1);
  const [isSendingInquiry, setIsSendingInquiry] = useState(false);

  // Review Modal State
  const [showReviewDialog, setShowReviewDialog] = useState(false);
  const [reviewTextState, setReviewTextState] = useState("");
  const [ratingState, setRatingState] = useState(0);
  const [isSubmittingReview, setIsSubmittingReview] = useState(false);
  const [editingReviewId, setEditingReviewId] = useState(null);

  const [isFavorite, setIsFavorite] = useState(false);

  useEffect(() => {
    let isMounted = true;
    if (!user?.email || !farmerEmail) return;
    
    // Check initial favorite status
    const checkFavorite = async () => {
      try {
        const { data } = await apiClient.post('/api/profile/get-favorites', { email: user.email });
        if (data && Array.isArray(data) && isMounted) {
          setIsFavorite(data.some(f => f.farmerEmail === farmerEmail));
        }
      } catch (err) {
        console.error("Failed to check favorite status", err);
      }
    };
    
    checkFavorite();
    
    return () => {
      isMounted = false;
    };
  }, [user?.email, farmerEmail]);

  const toggleFavorite = async () => {
    if (!user?.email || !farmerEmail) return;
    
    // Optimistic UI update
    const previousState = isFavorite;
    setIsFavorite(!isFavorite);
    
    try {
      await apiClient.post('/api/profile/toggle-favorite', {
        email: user.email,
        farmerEmail: farmerEmail,
        farmerName: farmerProfile?.name || farmerEmail.split('@')[0],
        profileImageUrl: farmerProfile?.profile_image_url || ''
      });
    } catch (error) {
      console.error("Error toggling favorite:", error);
      setIsFavorite(previousState);
      alert("Failed to update favorites. Please check your connection.");
    }
  };

  const containerRef = useRef(null);

  useEffect(() => {
    if (!farmerEmail) return;

    if (containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' }
      );
    }
    
    fetchFarmerData();
    fetchReviews();
  }, [farmerEmail]);

  const fetchFarmerData = async () => {
    setIsLoading(true);
    try {
      const profileRes = await apiClient.post('/api/profile/fetch-details', { email: farmerEmail });
      if (profileRes.data && profileRes.data.name) {
        setFarmerProfile(profileRes.data);
      } else {
        setFarmerProfile({ name: farmerEmail.split('@')[0] }); // robust fallback
      }

      const inventoryRes = await apiClient.get('/api/inventory/market');
      if (inventoryRes.data) {
        const validItems = inventoryRes.data
          .map(item => ({ ...item, expiryStatus: calculateExpiryStatus(item) }))
          .filter(item => {
            if (item.email !== farmerEmail || item.availableKg <= 0 || item.donatedToNgo) return false;
            return item.expiryStatus.isVisibleToUser;
          });
        setInventory(validItems);
      }
    } catch (e) {
      console.error("Error fetching farmer data:", e);
      setFarmerProfile({ name: farmerEmail?.split('@')[0] || 'Farmer' }); // fallback
    } finally {
      setIsLoading(false);
    }
  };

  const fetchReviews = async () => {
    setIsReviewsLoading(true);
    try {
      const { data } = await apiClient.post('/api/reviews/fetch', { farmerEmail });
      const reviews = Array.isArray(data) ? data : (data.reviews || []);
      setReviewsList(reviews);
      if (reviews.length > 0) {
        const avg = reviews.reduce((acc, curr) => acc + (curr.rating || 5), 0) / reviews.length;
        setAverageRating(avg);
      } else {
        setAverageRating(0);
      }
    } catch (e) {
      console.error("Error fetching reviews:", e);
    } finally {
      setIsReviewsLoading(false);
    }
  };

  const submitReview = async () => {
    if (!user?.email || !reviewTextState.trim() || ratingState === 0) return;
    setIsSubmittingReview(true);
    try {
      if (editingReviewId) {
        await apiClient.post('/api/reviews/update', {
          id: editingReviewId,
          rating: ratingState,
          text: reviewTextState,
          timestamp: Date.now()
        });
      } else {
        await apiClient.post('/api/reviews/add', {
          farmerEmail,
          userEmail: user.email,
          userName: user.name || user.displayName || localStorage.getItem('USER_NAME') || 'User',
          rating: ratingState,
          text: reviewTextState,
          timestamp: Date.now()
        });
      }
      setShowReviewDialog(false);
      setReviewTextState("");
      setRatingState(0);
      setEditingReviewId(null);
      fetchReviews();
    } catch (e) {
      console.error(e);
      alert("Error submitting review.");
    } finally {
      setIsSubmittingReview(false);
    }
  };

  const deleteReview = async (id) => {
    try {
      await apiClient.post('/api/reviews/delete', { id });
      fetchReviews();
    } catch (e) {
      alert("Error deleting review.");
    }
  };

  const openEditReview = (rev) => {
    setEditingReviewId(rev.id);
    setReviewTextState(rev.text || rev.comment || '');
    setRatingState(rev.rating || 0);
    setShowReviewDialog(true);
  };

  const handleNegotiationSubmit = async () => {
    if (!user?.email || !farmerEmail || !inputKgStr || !inputTargetPriceStr) return;
    setIsSendingInquiry(true);
    try {
      const chatId = user.email < farmerEmail ? `${user.email}_${farmerEmail}` : `${farmerEmail}_${user.email}`;
      const messageData = {
        senderId: user?.email || "",
        receiverId: farmerEmail || "",
        type: "INQUIRY_CARD",
        cropName: selectedItemForDeal.productName || selectedItemForDeal.cropName || "",
        kg: parseFloat(inputKgStr),
        basePrice: getEffectivePrice(selectedItemForDeal),
        targetPrice: parseFloat(inputTargetPriceStr),
        imageUrl: selectedItemForDeal.imageUrl || "",
        itemId: selectedItemForDeal.id || "",
        status: "PENDING",
        timestamp: Date.now()
      };
      
      await addDoc(collection(db, "chats", chatId, "messages"), messageData);
      await setDoc(doc(db, "chats", chatId), {
        userEmail: user?.email || "",
        farmerEmail: farmerEmail || "",
        userName: user?.name || user?.displayName || localStorage.getItem('USER_NAME') || "User",
        farmerName: farmerProfile?.name || farmerEmail?.split('@')[0] || "Farmer",
        userImage: user?.profile_image_url || "",
        farmerImage: farmerProfile?.profile_image_url || "",
        lastMessage: "Sent Deal Request",
        lastMessageTime: messageData.timestamp || Date.now(),
        unreadCountFarmer: 1,
        unreadCountUser: 0
      }, { merge: true });
      
      setShowNegotiationDialog(false);
      setNegotiationStep(1);
      setInputKgStr("");
      setInputTargetPriceStr("");
      navigate(`/deals/${farmerEmail}`);
    } catch (e) {
      console.error(e);
      alert("Failed to send inquiry.");
    } finally {
      setIsSendingInquiry(false);
    }
  };

  const extractFilename = (pathStr) => {
    if (!pathStr) return null;
    return pathStr.split(/[\/\\]/).pop();
  };

  const handleImageError = (e, fallbackText) => {
    e.target.style.display = 'none';
    e.target.nextSibling.style.display = 'flex';
  };

  const filteredInventory = inventory.filter(item => selectedCategory === "All" || item.category === selectedCategory);

  if (isLoading) {
    return <FullScreenLoader />;
  }

  // Exact database concatenation matching backend storage routes
  const profileImageSrc = farmerProfile?.profile_image_url ? `http://localhost:5000${farmerProfile.profile_image_url}` : '/app_logo.svg';

  return (
    <div ref={containerRef} style={{ maxWidth: '1000px', margin: '0 auto', paddingBottom: '80px' }}>
      {/* Navbar */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
        <button onClick={() => navigate(-1)} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <h1 style={{ margin: 0, fontSize: '20px', fontWeight: 800, color: '#222' }}>Farmer Store</h1>
      </div>

      {/* Hero Profile Banner */}
      <div style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', border: '1px solid #eee', boxShadow: '0 8px 32px rgba(0,0,0,0.03)', display: 'flex', flexDirection: 'column', alignItems: 'center', position: 'relative', marginBottom: '32px', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '120px', background: 'linear-gradient(135deg, var(--golden-yellow) 0%, var(--terracotta-primary) 100%)', opacity: 0.1 }}></div>
        
        {/* Heart Icon Button */}
        <button 
          onClick={toggleFavorite}
          style={{ position: 'absolute', top: '20px', right: '20px', zIndex: 10, background: 'rgba(255,255,255,0.8)', border: 'none', borderRadius: '50%', width: '44px', height: '44px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', backdropFilter: 'blur(4px)', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}
          className="active:scale-75 transition-transform duration-200 hover:scale-110"
        >
          <Heart size={24} fill={isFavorite ? "#F59E0B" : "none"} color={isFavorite ? "#F59E0B" : "#666"} />
        </button>
        
        <div style={{ width: '100px', height: '100px', borderRadius: '50%', backgroundColor: '#fff', border: '4px solid #fff', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10, marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
          <img src={profileImageSrc} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="Profile" onError={(e) => handleImageError(e, farmerProfile?.name)} />
          <div style={{ display: 'none', width: '100%', height: '100%', backgroundColor: 'var(--peach-background)', alignItems: 'center', justifyContent: 'center', color: 'var(--terracotta-primary)', fontWeight: 'bold', fontSize: '36px' }}>
            {farmerProfile?.name ? farmerProfile.name.charAt(0).toUpperCase() : 'F'}
          </div>
        </div>
        
        <h2 style={{ margin: '0 0 8px 0', fontSize: '24px', fontWeight: 800, color: '#222', zIndex: 10 }}>{farmerProfile?.name || 'Farmer'}</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', zIndex: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: '#fdfbfb', padding: '6px 12px', borderRadius: '12px', border: '1px solid #eee' }}>
            <Star size={16} fill="var(--golden-yellow)" color="var(--golden-yellow)" />
            <span style={{ fontWeight: 700, fontSize: '14px', color: '#333' }}>{averageRating > 0 ? averageRating.toFixed(1) : 'New'} ({reviewsList.length} reviews)</span>
          </div>
          <button 
            onClick={() => { setEditingReviewId(null); setReviewTextState(""); setRatingState(0); setShowReviewDialog(true); }}
            style={{ backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', border: 'none', padding: '6px 12px', borderRadius: '12px', fontSize: '13px', fontWeight: 700, cursor: 'pointer' }}
          >
            Leave Review
          </button>
        </div>
      </div>

      {/* Categories Filter */}
      <div style={{ display: 'flex', gap: '12px', overflowX: 'auto', paddingBottom: '16px', marginBottom: '16px', msOverflowStyle: 'none', scrollbarWidth: 'none' }}>
        {categories.map(cat => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            style={{
              padding: '10px 20px',
              borderRadius: '20px',
              border: 'none',
              backgroundColor: selectedCategory === cat ? 'var(--terracotta-primary)' : '#fff',
              color: selectedCategory === cat ? '#fff' : '#666',
              fontWeight: 700,
              fontSize: '14px',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              boxShadow: selectedCategory === cat ? '0 4px 12px rgba(220, 88, 66, 0.3)' : '0 2px 8px rgba(0,0,0,0.02)'
            }}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Inventory Masonry Grid */}
      <h3 style={{ fontSize: '20px', fontWeight: 800, color: '#111', margin: '0 0 16px 0' }}>Available Inventory</h3>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: '20px', marginBottom: '32px' }}>
        {filteredInventory.map((item, idx) => {
          const filename = extractFilename(item.imageUrl || item.image);
          const imageSrc = filename ? `http://localhost:5000/uploads/product_photos/${filename}` : '/app_logo.svg';
          const finalPrice = getEffectivePrice(item);

          return (
            <div key={idx} style={{ backgroundColor: '#fff', borderRadius: '20px', border: '1px solid #eee', overflow: 'hidden', boxShadow: '0 4px 16px rgba(0,0,0,0.03)', display: 'flex', flexDirection: 'column' }}>
              <div style={{ width: '100%', height: '180px', backgroundColor: '#f9f9f9', position: 'relative' }}>
                <img src={imageSrc} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt={item.productName || item.cropName} onError={(e) => handleImageError(e, item.productName || item.cropName)} />
                <div style={{ display: 'none', width: '100%', height: '100%', backgroundColor: '#eee', alignItems: 'center', justifyContent: 'center', color: '#888', fontWeight: 'bold', fontSize: '32px' }}>
                  {(item.productName || item.cropName || 'P').charAt(0).toUpperCase()}
                </div>
                {item.expiryStatus?.discountPercent > 0 && (
                  <div style={{ position: 'absolute', top: '12px', left: '12px', backgroundColor: '#ef4444', color: '#fff', padding: '6px 12px', borderRadius: '12px', fontSize: '13px', fontWeight: 800, boxShadow: '0 4px 12px rgba(239, 68, 68, 0.4)', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <span>🔥 {item.expiryStatus.badge}</span>
                    <span style={{ fontSize: '10px', opacity: 0.9, marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <Clock size={10} /> Expiring in {item.expiryStatus.timeString}
                    </span>
                  </div>
                )}
              </div>
              
              <div style={{ padding: '20px', flex: 1, display: 'flex', flexDirection: 'column' }}>
                <h4 style={{ margin: '0 0 8px 0', fontSize: '18px', fontWeight: 800, color: '#222' }}>{item.productName || item.cropName}</h4>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginBottom: '16px' }}>
                  <span style={{ fontSize: '22px', fontWeight: 800, color: item.expiryStatus?.discountPercent > 0 ? '#10b981' : 'var(--terracotta-primary)' }}>
                    {formatCurrency(finalPrice.toFixed(2))}<span style={{ fontSize: '14px', color: '#666' }}>/kg</span>
                  </span>
                  {item.expiryStatus?.discountPercent > 0 && (
                    <span style={{ fontSize: '14px', fontWeight: 600, color: '#999', textDecoration: 'line-through' }}>{formatCurrency(item.pricePerKg)}</span>
                  )}
                </div>
                
                <div style={{ marginTop: 'auto', display: 'flex', gap: '12px' }}>
                  <div style={{ flex: 1, backgroundColor: '#fdfbfb', border: '1px solid #eee', borderRadius: '12px', padding: '10px', textAlign: 'center' }}>
                    <span style={{ display: 'block', fontSize: '11px', color: '#888', fontWeight: 700, textTransform: 'uppercase' }}>Available</span>
                    <span style={{ display: 'block', fontSize: '16px', fontWeight: 800, color: '#333' }}>{item.availableKg} kg remaining</span>
                  </div>
                  <button 
                    onClick={() => { setSelectedItemForDeal(item); setShowNegotiationDialog(true); setNegotiationStep(1); }}
                    style={{ flex: 1, backgroundColor: 'var(--golden-yellow)', color: '#fff', border: 'none', borderRadius: '12px', fontSize: '14px', fontWeight: 800, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                  >
                    <Handshake size={16} /> Deal
                  </button>
                </div>
                
                {user?.email === farmerEmail && item.expiryStatus?.status === 'NEAR_EXPIRY' && (
                  <button 
                    style={{ width: '100%', backgroundColor: '#fef3c7', color: '#d97706', border: '1px solid #fde68a', padding: '10px 0', borderRadius: '16px', fontSize: '14px', fontWeight: 800, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginTop: '12px' }}
                    className="hover:bg-yellow-200 transition-colors"
                  >
                    Donate to NGO
                  </button>
                )}
              </div>
            </div>
          );
        })}
        {filteredInventory.length === 0 && (
          <div style={{ gridColumn: '1 / -1', padding: '60px 20px', textAlign: 'center', backgroundColor: '#fdfbfb', borderRadius: '24px', border: '1px dashed #ccc' }}>
            <Filter size={48} color="#ccc" style={{ margin: '0 auto 16px auto', display: 'block' }} />
            <h3 style={{ margin: '0 0 8px 0', color: '#333' }}>No Items Found</h3>
            <p style={{ margin: 0, color: '#888' }}>Try a different category filter.</p>
          </div>
        )}
      </div>

      {/* Reviews Section */}
      <h3 style={{ fontSize: '20px', fontWeight: 800, color: '#111', margin: '0 0 16px 0' }}>Customer Reviews</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {reviewsList.length === 0 ? (
          <p style={{ color: '#888', fontStyle: 'italic' }}>No reviews yet. Be the first to review!</p>
        ) : (
          reviewsList.map((rev, idx) => (
            <div key={rev.id || idx} style={{ backgroundColor: '#fff', borderRadius: '16px', padding: '20px', border: '1px solid #eee', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: '16px', fontWeight: 700, color: '#222' }}>{rev.userName || 'Customer'}</h4>
                  <div style={{ display: 'flex', gap: '2px' }}>
                    {[1, 2, 3, 4, 5].map(star => (
                      <Star key={star} size={14} fill={star <= (rev.rating || 5) ? "var(--golden-yellow)" : "none"} color={star <= (rev.rating || 5) ? "var(--golden-yellow)" : "#ccc"} />
                    ))}
                  </div>
                </div>
                <span style={{ fontSize: '12px', color: '#888' }}>
                  {rev.timestamp ? new Date(rev.timestamp).toLocaleDateString() : ''}
                </span>
              </div>
              <p style={{ margin: '0 0 16px 0', fontSize: '14px', color: '#555', lineHeight: 1.5 }}>
                {rev.text || rev.comment}
              </p>
              {user?.email === rev.userEmail && (
                <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
                  <button onClick={() => openEditReview(rev)} style={{ display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: 'none', color: '#3b82f6', fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                    <Edit size={14} /> Edit
                  </button>
                  <button onClick={() => deleteReview(rev.id)} style={{ display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: 'none', color: '#ef4444', fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                    <Trash2 size={14} /> Delete
                  </button>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* Review Dialog Modal */}
      {showReviewDialog && createPortal(
        <div 
          style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 99999, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(4px)' }}
          onClick={() => setShowReviewDialog(false)}
        >
          <div 
            style={{ backgroundColor: '#ffffff', borderRadius: '24px', boxShadow: '0 10px 25px rgba(0,0,0,0.2)', padding: '24px', width: '90%', maxWidth: '400px', position: 'relative', display: 'flex', flexDirection: 'column' }}
            onClick={(e) => e.stopPropagation()}
          >
            <button 
              onClick={() => setShowReviewDialog(false)} 
              style={{ position: 'absolute', top: '16px', right: '16px', width: '32px', height: '32px', padding: 0, backgroundColor: '#f3f4f6', color: '#6b7280', border: 'none', borderRadius: '50%', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
              <X size={16} />
            </button>
            
            <h2 style={{ margin: '0 0 24px 0', fontSize: '24px', fontWeight: 800, color: '#111', textAlign: 'center' }}>
              {editingReviewId ? 'Edit Review' : 'Rate this Farmer'}
            </h2>
            
            <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', marginBottom: '24px' }}>
              {[1, 2, 3, 4, 5].map(star => (
                <Star 
                  key={star} 
                  size={42} 
                  fill={star <= ratingState ? "#F59E0B" : "none"} 
                  color={star <= ratingState ? "#F59E0B" : "#D1D5DB"} 
                  onClick={() => setRatingState(star)}
                  style={{ cursor: 'pointer', transition: 'all 0.2s' }}
                />
              ))}
            </div>
            
            <textarea 
              value={reviewTextState}
              onChange={(e) => setReviewTextState(e.target.value)}
              placeholder="Tell us about your experience..."
              style={{ width: '100%', height: '120px', padding: '16px', marginBottom: '24px', fontSize: '14px', color: '#333', backgroundColor: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '16px', outline: 'none', resize: 'none', boxSizing: 'border-box' }}
            />
            
            <button 
              onClick={submitReview}
              disabled={isSubmittingReview || !reviewTextState.trim() || ratingState === 0}
              style={{
                width: '100%', padding: '16px', display: 'flex', justifyContent: 'center', alignItems: 'center', borderRadius: '16px', fontSize: '16px', fontWeight: 'bold', color: '#fff', border: 'none', cursor: (isSubmittingReview || !reviewTextState.trim() || ratingState === 0) ? 'not-allowed' : 'pointer',
                background: (isSubmittingReview || !reviewTextState.trim() || ratingState === 0) ? '#9ca3af' : 'linear-gradient(to right, #fb923c, #f97316)',
                transition: 'all 0.3s'
              }}
            >
              {isSubmittingReview ? <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> : 'Submit Review'}
            </button>
          </div>
        </div>,
        document.body
      )}

      {showNegotiationDialog && selectedItemForDeal && createPortal(
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 99999, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ backgroundColor: '#ffffff', borderRadius: '24px', boxShadow: '0 10px 25px rgba(0,0,0,0.2)', padding: '24px', width: '90%', maxWidth: '400px', position: 'relative', display: 'flex', flexDirection: 'column' }} onClick={(e) => e.stopPropagation()}>
            <h2 style={{ margin: '0 0 8px 0', fontSize: '24px', fontWeight: 800, color: '#111827', textAlign: 'center' }}>Send Inquiry</h2>
            <p style={{ margin: '0 0 24px 0', fontSize: '14px', color: '#4b5563', textAlign: 'center' }}>
              {selectedItemForDeal.productName || selectedItemForDeal.cropName}
            </p>
            
            {negotiationStep === 1 ? (() => {
              const kgValue = parseFloat(inputKgStr) || 0;
              const moq = selectedItemForDeal.moq || 1;
              const available = selectedItemForDeal.availableKg || 0;
              const isValidQty = kgValue >= moq && kgValue <= available;
              
              const finalBasePrice = getEffectivePrice(selectedItemForDeal);

              const updateQty = (delta) => {
                const newVal = kgValue + delta;
                if (newVal >= 0 && newVal <= available) {
                  setInputKgStr(newVal.toString());
                }
              };

              const fullUrl = selectedItemForDeal.imageUrl?.startsWith("http") ? selectedItemForDeal.imageUrl : `http://localhost:5000${selectedItemForDeal.imageUrl}`;

              return (
                <>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '20px' }}>
                    <img src={fullUrl} alt="Crop" style={{ width: '60px', height: '60px', borderRadius: '12px', objectFit: 'cover' }} />
                    <div style={{ marginLeft: '12px' }}>
                      <div style={{ fontWeight: 'bold', fontSize: '18px', color: '#333' }}>{selectedItemForDeal.productName || selectedItemForDeal.cropName}</div>
                      <div style={{ display: 'flex', alignItems: 'center', color: '#2E7D32', fontWeight: 'bold' }}>
                        <span style={{ fontSize: '14px' }}>Market Rate: {formatCurrency(finalBasePrice.toFixed(2))}/kg</span>
                      </div>
                    </div>
                  </div>

                  <div style={{ fontWeight: 'bold', color: 'var(--terracotta-primary)', fontSize: '14px', marginBottom: '8px' }}>
                    Step 1: Required Quantity
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '100%', marginBottom: '8px' }}>
                    <button onClick={() => { if (kgValue > moq) setInputKgStr((kgValue - 1).toFixed(1)) }} style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: '#fff3e0', color: 'var(--terracotta-primary)', border: 'none', fontSize: '20px', fontWeight: 'bold', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>-</button>
                    <input 
                      type="number"
                      value={inputKgStr}
                      onChange={(e) => setInputKgStr(e.target.value)}
                      placeholder="Kg"
                      style={{ width: '120px', margin: '0 8px', padding: '12px', textAlign: 'center', fontWeight: 'bold', fontSize: '16px', borderRadius: '16px', border: '1px solid var(--golden-yellow)', outline: 'none' }}
                    />
                    <button onClick={() => { if (kgValue < available) setInputKgStr((kgValue + 1).toFixed(1)) }} style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: '#fff3e0', color: 'var(--terracotta-primary)', border: 'none', fontSize: '20px', fontWeight: 'bold', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>+</button>
                  </div>
                  
                  {inputKgStr && !isValidQty && (
                    <div style={{ display: 'flex', alignItems: 'center', color: 'red', fontSize: '12px', fontWeight: 'bold', marginTop: '8px' }}>
                      <span style={{ marginRight: '4px' }}>⚠️</span>
                      {kgValue < moq ? `Minimum order is ${moq} kg` : `Only ${available} kg available`}
                    </div>
                  )}

                  {isValidQty && (
                    <div style={{ width: '100%', marginTop: '16px', backgroundColor: '#E8F5E9', borderRadius: '8px', border: '1px solid #2E7D32', padding: '12px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                      <span style={{ fontWeight: 800, fontSize: '18px', color: '#2E7D32' }}>
                        Market Total: {formatCurrency((kgValue * finalBasePrice).toFixed(2))}
                      </span>
                    </div>
                  )}
                  
                  <div style={{ marginTop: '24px', display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                    <button 
                      onClick={() => { setShowNegotiationDialog(false); setInputKgStr(""); }}
                      style={{ background: 'transparent', border: 'none', color: 'gray', cursor: 'pointer', padding: '8px 16px' }}
                    >
                      Cancel
                    </button>
                    <button 
                      onClick={() => setNegotiationStep(2)}
                      disabled={!isValidQty}
                      style={{ backgroundColor: isValidQty ? 'var(--terracotta-primary)' : 'lightgray', color: 'white', fontWeight: 'bold', padding: '12px 24px', borderRadius: '12px', border: 'none', cursor: isValidQty ? 'pointer' : 'not-allowed' }}
                    >
                      Next
                    </button>
                  </div>
                </>
              );
            })() : (() => {
              const kgValue = parseFloat(inputKgStr) || 0;
              const targetPrice = parseFloat(inputTargetPriceStr) || 0;
              
              const finalBasePrice = getEffectivePrice(selectedItemForDeal);
              const diff = finalBasePrice - targetPrice;
              const total = targetPrice * kgValue;
              
              return (
                <>
                  <div style={{ fontWeight: 'bold', color: 'var(--terracotta-primary)', fontSize: '14px', marginBottom: '8px' }}>
                    Step 2: Price Negotiation
                  </div>

                  <div style={{ width: '100%', backgroundColor: '#fff3e0', borderRadius: '8px', padding: '12px', marginBottom: '16px', display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontSize: '13px', color: '#333' }}>Required: {kgValue.toFixed(1)} kg</span>
                    <span style={{ fontSize: '13px', color: '#333' }}>Market Rate: {formatCurrency(finalBasePrice.toFixed(2))}/kg</span>
                    <span style={{ fontWeight: 'bold', color: 'var(--terracotta-primary)' }}>Total Market Value: {formatCurrency((kgValue * finalBasePrice).toFixed(2))}</span>
                  </div>
                  
                  <input 
                    type="number"
                    value={inputTargetPriceStr}
                    onChange={(e) => setInputTargetPriceStr(e.target.value)}
                    placeholder="Your Target Price (per kg)"
                    style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid var(--golden-yellow)', outline: 'none', textAlign: 'center', fontSize: '16px' }}
                  />
                  
                  {targetPrice > 0 && (
                    <div style={{ width: '100%', marginTop: '12px', backgroundColor: '#E8F5E9', borderRadius: '8px', border: '1px solid #2E7D32', padding: '12px', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <span style={{ color: '#2E7D32', fontSize: '12px', fontWeight: 500, marginBottom: '4px' }}>
                        {diff > 0 ? `Requesting a discount of ${formatCurrency(diff.toFixed(2))}/kg` : 'Offering above market rate'}
                      </span>
                      <span style={{ fontWeight: 800, fontSize: '18px', color: '#2E7D32' }}>
                        Your Expected Total: {formatCurrency(total.toFixed(2))}
                      </span>
                    </div>
                  )}
                  
                  <div style={{ display: 'flex', gap: '12px', marginTop: '24px', width: '100%' }}>
                    <button 
                      onClick={() => setNegotiationStep(1)}
                      style={{ flex: 1, padding: '16px', backgroundColor: '#f5f5f5', color: '#333', borderRadius: '16px', fontWeight: 'bold', border: 'none', cursor: 'pointer' }}
                    >
                      Back
                    </button>
                    <button 
                      onClick={handleNegotiationSubmit}
                      disabled={!targetPrice || isSendingInquiry}
                      style={{ flex: 2, padding: '16px', backgroundColor: (targetPrice && !isSendingInquiry) ? 'var(--terracotta-primary)' : 'lightgray', color: 'white', borderRadius: '16px', fontWeight: 'bold', border: 'none', cursor: (targetPrice && !isSendingInquiry) ? 'pointer' : 'not-allowed', display: 'flex', justifyContent: 'center', alignItems: 'center' }}
                    >
                      {isSendingInquiry ? <Loader2 size={20} className="animate-spin" /> : 'Send Deal'}
                    </button>
                  </div>
                </>
              );
            })()}
          </div>
        </div>
      , document.body)}

    </div>
  );
};

export default FarmerMenu;
