import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import gsap from 'gsap';
import { Navigation, MapPin, CloudRain, Clock, User, Image as ImageIcon, Heart, CheckCircle } from 'lucide-react';
import { calculateExpiryStatus } from '../../utils/inventoryHelpers';
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

const UHomeScreen = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  // Immediately hydrate with the user object if available for instant render
  const [currentUser, setCurrentUser] = useState({ name: user?.name || user?.displayName || localStorage.getItem('USER_NAME') || 'User' });
  const [activeFarmers, setActiveFarmers] = useState([]);
  const [freshProducts, setFreshProducts] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  
  const [greetingLine, setGreetingLine] = useState("Hello");
  const [isLocating, setIsLocating] = useState(false);
  const [favoriteFarmers, setFavoriteFarmers] = useState([]);

  const containerRef = useRef(null);
  const cardsRef = useRef([]);

  useEffect(() => {
      let isMounted = true;
      if (!user?.email) return;
      
      apiClient.post('/api/profile/get-favorites', { email: user.email })
          .then(res => {
              if (isMounted) {
                  setFavoriteFarmers(res.data || []);
                  setIsLoading(false);
              }
          })
          .catch(err => {
              if (isMounted) setIsLoading(false);
          });
          
      return () => { isMounted = false; };
  }, [user?.email]);

  useEffect(() => {
    let isMounted = true;

    // Dynamic Time-based Greeting
    const hour = new Date().getHours();
    if (hour < 12) setGreetingLine("Good Morning");
    else if (hour < 17) setGreetingLine("Good Afternoon");
    else if (hour < 21) setGreetingLine("Good Evening");
    else setGreetingLine("Good Night");

    const fetchDashboardData = async () => {
      const isCached = sessionStorage.getItem('home_cached') === 'true';
      if (!isCached && isMounted) setIsLoading(true);
      
      try {
        if (user?.email) {
          const uRes = await apiClient.post('/api/profile/fetch-details', { email: user.email });
          if (uRes.data && uRes.data.name && isMounted) {
            setCurrentUser(uRes.data);
            localStorage.setItem('USER_NAME', uRes.data.name);
          }
        }

        const invRes = await apiClient.get('/api/inventory/market');
        let products = [];
        if (invRes.data) {
          products = invRes.data
            .map(i => ({ ...i, expiryStatus: calculateExpiryStatus(i) }))
            .filter(i => i.availableKg > 0 && i.expiryStatus.isVisibleToUser);
          if (isMounted) setFreshProducts(products);
        }

        const uniqueEmails = [...new Set(products.map(p => p.email))];
        const farmersData = [];
        for (const email of uniqueEmails) {
          try {
            const profileRes = await apiClient.post('/api/profile/fetch-details', { email });
            if (profileRes.data && profileRes.data.name) {
              farmersData.push({ email, ...profileRes.data });
            } else {
              farmersData.push({ email, name: email.split('@')[0] });
            }
          } catch (e) {
            farmersData.push({ email, name: email.split('@')[0] });
          }
        }
        
        if (isMounted) {
          setActiveFarmers(farmersData);
          sessionStorage.setItem('home_cached', 'true');
        }
      } catch (e) {
        console.error(e);
      } finally {
        if (!isCached && isMounted) setIsLoading(false);
      }
    };

    fetchDashboardData();

    return () => {
      isMounted = false;
    };
  }, [user?.email]);

  useEffect(() => {
    if (containerRef.current && !isLoading) {
      gsap.fromTo(containerRef.current.children, { opacity: 0, y: 15 }, { opacity: 1, y: 0, duration: 0.4, stagger: 0.1, ease: 'power2.out' });
    }
  }, [isLoading]);

  useEffect(() => {
    if (cardsRef.current.length > 0 && !isLoading) {
      gsap.fromTo(cardsRef.current, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' }
      );
    }
  }, [freshProducts, isLoading]);

  const handleLocationSync = () => {
    setIsLocating(true);
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const lat = position.coords.latitude;
          const lon = position.coords.longitude;
          localStorage.setItem('LAT', lat);
          localStorage.setItem('LON', lon);
          
          try {
            const { data } = await apiClient.get(`/api/weather?lat=${lat}&lon=${lon}`);
            if (data.success) {
              navigate('/weather-details', { state: { weatherData: data, lat, lon } });
            } else {
              alert("Weather data unavailable.");
            }
          } catch (e) {
            alert("Error fetching weather.");
          } finally {
            setIsLocating(false);
          }
        },
        () => {
          alert("Please enable GPS permissions to sync location.");
          setIsLocating(false);
        }
      );
    } else {
      alert("Geolocation is not supported by your browser.");
      setIsLocating(false);
    }
  };

  const handleImageError = (e, fallbackText) => {
    e.target.style.display = 'none'; // hide broken image
    e.target.nextSibling.style.display = 'flex'; // show fallback div
  };

  const removeFavorite = async (e, email) => {
    e.stopPropagation();
    if (!user?.email) return;
    
    // Optimistic UI update
    const previousFavs = [...favoriteFarmers];
    setFavoriteFarmers(favoriteFarmers.filter(f => f.farmerEmail !== email));
    
    try {
      await apiClient.post('/api/profile/toggle-favorite', { 
        email: user.email, 
        farmerEmail: email 
      });
    } catch (err) {
      console.error(err);
      setFavoriteFarmers(previousFavs);
      alert("Failed to update favorites. Please check your connection.");
    }
  };

  const extractFilename = (pathStr) => {
    if (!pathStr) return null;
    return pathStr.split(/[\/\\]/).pop();
  };

  if (isLoading) {
    return <FullScreenLoader />;
  }

  // Use exact auth context if available, fallback to fetched profile, fallback to 'User'
  const finalUserName = user?.name || currentUser?.name || 'User';

  return (
    <div ref={containerRef} className="opacity-0 transition-opacity duration-500 ease-in" style={{ maxWidth: '1000px', margin: '0 auto', paddingBottom: '80px', opacity: 1 }}>
      
      {/* Dynamic Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: '28px', fontWeight: 800, color: '#222' }}>{greetingLine}, {finalUserName}</h1>
        </div>
      </div>

      <section 
        onClick={handleLocationSync}
        style={{
          background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
          borderRadius: '24px', padding: '24px', color: '#fff',
          boxShadow: '0 10px 24px rgba(16, 185, 129, 0.3)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          cursor: 'pointer', marginBottom: '32px', transition: 'transform 0.2s ease'
        }}
        onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.02)'}
        onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
      >
        <div>
          <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 800 }}>
            {isLocating ? 'Locating...' : 'Sync Current Location'}
          </h2>
          <p style={{ margin: '4px 0 0 0', fontSize: '14px', opacity: 0.9, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Navigation size={14} /> Tap to view detailed live forecast
          </p>
        </div>
        <div style={{ width: '56px', height: '56px', borderRadius: '50%', backgroundColor: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {isLocating ? <CloudRain size={28} /> : <MapPin size={28} />}
        </div>
      </section>

      {/* Premium NGO Donation Hub Card */}
      {user?.role === 'ngo' && (
        <section 
          onClick={() => navigate('/ngo/donation-hub')}
          style={{
            background: 'linear-gradient(135deg, var(--terracotta-primary) 0%, #d84315 100%)',
            borderRadius: '24px', padding: '24px', color: '#fff',
            boxShadow: '0 10px 24px rgba(230, 81, 0, 0.3)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            cursor: 'pointer', marginBottom: '32px', transition: 'transform 0.2s ease, box-shadow 0.2s'
          }}
          onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.02)'; e.currentTarget.style.boxShadow = '0 16px 32px rgba(230, 81, 0, 0.4)'; }}
          onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.boxShadow = '0 10px 24px rgba(230, 81, 0, 0.3)'; }}
        >
          <div>
            <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 900 }}>Donation Hub</h2>
            <p style={{ margin: '4px 0 0 0', fontSize: '14px', opacity: 0.9, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Heart size={14} fill="#fff" /> View rescued produce from farmers
            </p>
          </div>
          <div style={{ width: '56px', height: '56px', borderRadius: '50%', backgroundColor: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(4px)' }}>
            <ImageIcon size={28} style={{ display: 'none' }} />
            <Heart size={28} fill="rgba(255,255,255,0.8)" />
          </div>
        </section>
      )}

      <section style={{ marginBottom: '32px' }}>
        <h3 style={{ fontSize: '18px', fontWeight: 800, color: '#111', margin: '0 0 16px 0' }}>Active Local Farmers</h3>
        <div style={{ display: 'flex', gap: '16px', overflowX: 'auto', paddingBottom: '16px', msOverflowStyle: 'none', scrollbarWidth: 'none' }}>
          {activeFarmers.map((farmer, idx) => {
            // Fix: Direct database concatenation mapping
            const imageSrc = farmer?.profile_image_url ? `http://localhost:5000${farmer.profile_image_url}` : '/app_logo.svg';
            
            return (
              <div key={idx} onClick={() => navigate(`/user/farmer/${encodeURIComponent(farmer.email)}`)} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', minWidth: '80px', cursor: 'pointer' }}>
                <div style={{ width: '70px', height: '70px', borderRadius: '50%', backgroundColor: '#eee', border: '3px solid var(--golden-yellow)', padding: '2px', overflow: 'hidden' }}>
                  <div style={{ width: '100%', height: '100%', borderRadius: '50%', overflow: 'hidden', backgroundColor: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
                    <img 
                      src={imageSrc} 
                      alt={farmer.name} 
                      onError={(e) => handleImageError(e, farmer.name)} 
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                    />
                    <div style={{ display: 'none', width: '100%', height: '100%', backgroundColor: 'var(--peach-background)', alignItems: 'center', justifyContent: 'center', color: 'var(--terracotta-primary)', fontWeight: 'bold', fontSize: '24px' }}>
                      {farmer.name.charAt(0).toUpperCase()}
                    </div>
                  </div>
                </div>
                <span style={{ fontSize: '12px', fontWeight: 700, color: '#333' }}>{farmer.name}</span>
              </div>
            )
          })}
          {activeFarmers.length === 0 && <div style={{ color: '#888', fontSize: '14px', fontStyle: 'italic' }}>No farmers active nearby.</div>}
        </div>
      </section>

      <section>
        <h3 style={{ fontSize: '18px', fontWeight: 800, color: '#111', margin: '0 0 16px 0' }}>Fresh Produce Available</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '20px' }}>
          {freshProducts.map((item, idx) => {
            const filename = extractFilename(item.imageUrl || item.image);
            const imageSrc = filename ? `http://localhost:5000/uploads/product_photos/${filename}` : '/app_logo.svg';
            const farmer = activeFarmers.find(f => f.email === item.email);
            const farmerName = farmer?.name || item.email.split('@')[0];
            const finalPrice = item.pricePerKg * (1 - (item.expiryStatus?.discountPercent || 0) / 100);

            return (
              <div 
                key={idx} 
                ref={el => cardsRef.current[idx] = el}
                onClick={() => navigate(`/user/farmer/${encodeURIComponent(item.email)}`)} 
                style={{ backgroundColor: '#fff', borderRadius: '20px', border: '1px solid #eee', overflow: 'hidden', boxShadow: '0 4px 16px rgba(0,0,0,0.03)', cursor: 'pointer', transition: 'transform 0.2s, box-shadow 0.2s' }}
                onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-4px)'; e.currentTarget.style.boxShadow = '0 12px 24px rgba(0,0,0,0.08)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.03)'; }}
              >
                <div style={{ width: '100%', height: '160px', backgroundColor: '#f9f9f9', position: 'relative' }}>
                  <img 
                    src={imageSrc} 
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                    alt={item.productName || item.cropName} 
                    onError={(e) => handleImageError(e, item.productName || item.cropName)} 
                  />
                  <div style={{ display: 'none', width: '100%', height: '100%', backgroundColor: '#eee', alignItems: 'center', justifyContent: 'center', color: '#888', fontWeight: 'bold', fontSize: '24px' }}>
                    {(item.productName || item.cropName || 'P').charAt(0).toUpperCase()}
                  </div>
                  {item.expiryStatus?.status && (
                    <div style={{ 
                      position: 'absolute', top: '12px', left: '12px', 
                      backgroundColor: item.expiryStatus.badgeColor === 'Red' ? '#ef4444' : 
                                      item.expiryStatus.badgeColor === 'Orange' ? '#f97316' : 
                                      item.expiryStatus.badgeColor === 'Yellow' ? '#f59e0b' : 
                                      item.expiryStatus.badgeColor === 'Grey' ? '#6b7280' : '#10b981', 
                      color: '#fff', padding: '6px 12px', borderRadius: '12px', fontSize: '13px', 
                      fontWeight: 800, 
                      boxShadow: `0 4px 12px ${item.expiryStatus.badgeColor === 'Red' ? 'rgba(239, 68, 68, 0.4)' : 
                                          item.expiryStatus.badgeColor === 'Orange' ? 'rgba(249, 115, 22, 0.4)' : 
                                          item.expiryStatus.badgeColor === 'Yellow' ? 'rgba(245, 158, 11, 0.4)' : 
                                          item.expiryStatus.badgeColor === 'Grey' ? 'rgba(107, 114, 128, 0.4)' : 'rgba(16, 185, 129, 0.4)'}`, 
                      display: 'flex', flexDirection: 'column', alignItems: 'center' 
                    }}>
                      <span>{item.expiryStatus.status}</span>
                      {item.expiryStatus.timeString && (
                        <span style={{ fontSize: '10px', opacity: 0.9, marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Clock size={10} /> {item.expiryStatus.timeString} Left
                        </span>
                      )}
                    </div>
                  )}
                </div>
                
                <div style={{ padding: '16px' }}>
                  <h4 style={{ margin: '0 0 4px 0', fontSize: '18px', fontWeight: 800, color: '#222' }}>{item.productName || item.cropName}</h4>
                  
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '12px' }}>
                    <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <User size={12} color="var(--terracotta-primary)" />
                    </div>
                    <span style={{ fontSize: '13px', fontWeight: 600, color: '#555' }}>Farmer: {farmerName}</span>
                  </div>

                  <p style={{ margin: '0 0 12px 0', fontSize: '12px', color: '#666', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Clock size={12} /> {item.availableKg} kg remaining
                  </p>
                  
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
                    <span style={{ fontSize: '20px', fontWeight: 800, color: item.expiryStatus?.discountPercent > 0 ? '#10b981' : 'var(--terracotta-primary)' }}>
                      {formatCurrency(finalPrice.toFixed(2))}<span style={{ fontSize: '14px', color: '#666' }}>/kg</span>
                    </span>
                    {item.expiryStatus?.discountPercent > 0 && (
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#999', textDecoration: 'line-through' }}>{formatCurrency(item.pricePerKg)}</span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
        {freshProducts.length === 0 && (
          <div style={{ padding: '60px 20px', textAlign: 'center', backgroundColor: '#fdfbfb', borderRadius: '24px', border: '1px dashed #ccc' }}>
            <h3 style={{ margin: '0 0 8px 0', color: '#333' }}>No Items Found</h3>
            <p style={{ margin: 0, color: '#888' }}>Try a different category or check back later.</p>
          </div>
        )}
      </section>

      {/* Favorite Farmers Section */}
      <section style={{ marginTop: '40px', marginBottom: '40px' }}>
        <h3 style={{ fontSize: '18px', fontWeight: 800, color: '#111', margin: '0 0 16px 0', paddingLeft: '4px' }}>Favorite Farmers</h3>
        
        {favoriteFarmers.length === 0 ? (
          <div style={{ backgroundColor: '#f5f5f5', borderRadius: '16px', padding: '24px', textAlign: 'center', border: '1px solid #e0e0e0', color: '#888', fontStyle: 'italic', fontSize: '14px', cursor: 'default' }}>
            No Favorite Farmers Added
          </div>
        ) : (
          <div style={{ display: 'flex', gap: '16px', overflowX: 'auto', paddingBottom: '16px', scrollSnapType: 'x mandatory', msOverflowStyle: 'none', scrollbarWidth: 'none' }}>
            {favoriteFarmers.map((fav, index) => {
              const imageSrc = fav.profileImageUrl ? (fav.profileImageUrl.startsWith('http') ? fav.profileImageUrl : `http://localhost:5000${fav.profileImageUrl}`) : null;
              
              return (
              <div 
                key={index} 
                style={{ 
                  backgroundColor: '#fff', 
                  borderRadius: '16px', 
                  padding: '16px', 
                  display: 'flex', 
                  flexDirection: 'column', 
                  alignItems: 'center', 
                  justifyContent: 'center',
                  width: '130px',
                  minWidth: '130px', 
                  border: '1px solid #eee', 
                  boxShadow: '0 4px 12px rgba(0,0,0,0.05)', 
                  cursor: 'pointer',
                  flexShrink: 0 
                }}
                onClick={() => navigate(`/user/farmer/${encodeURIComponent(fav.farmerEmail)}`)}
              >
                {/* Profile Image with Golden Circle Outline */}
                <div style={{ 
                  width: '68px', 
                  height: '68px', 
                  borderRadius: '50%', 
                  border: '3px solid var(--golden-yellow, #F2A33A)', 
                  padding: '2px', 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center', 
                  overflow: 'hidden',
                  marginBottom: '12px'
                }}>
                  {imageSrc ? (
                    <img 
                      src={imageSrc} 
                      alt={fav.farmerName} 
                      onError={(e) => handleImageError(e, fav.farmerName)}
                      style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
                    />
                  ) : (
                    <div style={{ width: '100%', height: '100%', borderRadius: '50%', backgroundColor: '#fffaf0', display: 'flex', alignItems: 'center', justifyItems: 'center', color: '#F2A33A', fontSize: '24px', fontWeight: 'bold' }}>
                      <span style={{ margin: 'auto' }}>
                        {fav.farmerName ? fav.farmerName.charAt(0).toUpperCase() : 'F'}
                      </span>
                    </div>
                  )}
                </div>

                {/* Farmer Name */}
                <span style={{ fontSize: '14px', fontWeight: 700, color: '#333', textAlign: 'center', marginBottom: '12px', width: '100%', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {fav.farmerName}
                </span>

                {/* Remove Button */}
                <button 
                  onClick={(e) => removeFavorite(e, fav.farmerEmail)}
                  style={{ 
                    width: '100%', 
                    padding: '6px 0', 
                    backgroundColor: '#fff0f0', 
                    color: '#ef4444', 
                    border: 'none', 
                    borderRadius: '20px', 
                    fontSize: '12px', 
                    fontWeight: 700, 
                    cursor: 'pointer',
                    transition: 'background-color 0.2s'
                  }}
                  onMouseEnter={(e) => e.target.style.backgroundColor = '#ffe0e0'}
                  onMouseLeave={(e) => e.target.style.backgroundColor = '#fff0f0'}
                >
                  Remove
                </button>
              </div>
            )})}
          </div>
        )}
      </section>
    </div>
  );
};

export default UHomeScreen;
