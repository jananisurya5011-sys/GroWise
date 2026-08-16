import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { ArrowLeft, Tractor, Plus, MapPin, Loader2, UploadCloud, Phone, Navigation } from 'lucide-react';
import gsap from 'gsap';
import { getDistance } from '../../utils/geoUtils';
import { formatCurrency } from '../../utils/constants';



const FarmerRentalHub = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [activeTab, setActiveTab] = useState('search'); 
  const [rentals, setRentals] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState('All');
  
  const [profileCoords, setProfileCoords] = useState({ homeLat: 0, homeLon: 0, farmLat: 0, farmLon: 0 });

  const [formData, setFormData] = useState({
    equipmentName: '',
    category: 'Tractors',
    ratePerHour: '',
    ratePerDay: '',
    latitude: '',
    longitude: ''
  });
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLocating, setIsLocating] = useState(false);
  
  const fileInputRef = useRef(null);
  const containerRef = useRef(null);

  useEffect(() => {
    fetchProfileCoordinates();
    if (containerRef.current) {
      gsap.fromTo(containerRef.current, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }
      );
    }
  }, []);

  useEffect(() => {
    if (activeTab === 'search') fetchRentals();
    else if (activeTab === 'my-items') fetchMyItems();
  }, [activeTab, categoryFilter, profileCoords]);

  const fetchProfileCoordinates = async () => {
    try {
      if (!user?.email) return;
      const { data } = await apiClient.get(`/api/profile/me?email=${user.email}`);
      if (data.success && data.profile) {
        setProfileCoords({
          homeLat: parseFloat(data.profile.homeLat || 0),
          homeLon: parseFloat(data.profile.homeLon || 0),
          farmLat: parseFloat(data.profile.farmLat || 0),
          farmLon: parseFloat(data.profile.farmLon || 0)
        });
      }
    } catch (e) {
      console.error('Failed to fetch profile coords', e);
    }
  };

  const calculateDisplayDistance = (equipLat, equipLon) => {
    if (!equipLat || !equipLon || (equipLat === 0 && equipLon === 0)) return 'Location unknown';
    
    const hasHome = profileCoords.homeLat !== 0;
    const hasFarm = profileCoords.farmLat !== 0;

    if (hasHome && hasFarm) {
      const distHome = haversine(profileCoords.homeLat, profileCoords.homeLon, equipLat, equipLon).toFixed(1);
      const distFarm = haversine(profileCoords.farmLat, profileCoords.farmLon, equipLat, equipLon).toFixed(1);
      return `${distHome} km from Home • ${distFarm} km from Farm`;
    } else if (hasHome) {
      return `${haversine(profileCoords.homeLat, profileCoords.homeLon, equipLat, equipLon).toFixed(1)} km from your location`;
    } else if (hasFarm) {
      return `${haversine(profileCoords.farmLat, profileCoords.farmLon, equipLat, equipLon).toFixed(1)} km from your location`;
    }
    return 'Distance unavailable (Add address in Profile)';
  };

  const fetchRentals = async () => {
    setIsLoading(true);
    try {
      const response = await fetch(`/api/rental/search?latitude=${profileCoords.farmLat || 0}&longitude=${profileCoords.farmLon || 0}&category=${categoryFilter}`);
      const data = await response.json();
      // Filter out own equipment
      const filtered = (data || []).filter(item => item.email !== user?.email);
      setRentals(filtered);
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  const fetchMyItems = async () => {
    if (!user?.email) return;
    setIsLoading(true);
    try {
      const response = await fetch('/api/rental/my-items', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email })
      });
      const data = await response.json();
      setRentals(data || []);
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleFetchLocation = () => {
    setIsLocating(true);
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          setFormData(prev => ({
            ...prev,
            latitude: position.coords.latitude.toString(),
            longitude: position.coords.longitude.toString()
          }));
          setIsLocating(false);
        },
        (error) => {
          alert("Could not fetch location. Please enable GPS permissions.");
          setIsLocating(false);
        }
      );
    } else {
      alert("Geolocation is not supported by your browser.");
      setIsLocating(false);
    }
  };

  const handleAddSubmit = async (e) => {
    e.preventDefault();
    if (!formData.equipmentName || !formData.ratePerDay) {
      alert("Please fill required fields");
      return;
    }
    
    setIsSubmitting(true);
    const payload = new FormData();
    Object.keys(formData).forEach(key => payload.append(key, formData[key]));
    payload.append('email', user?.email || '');
    if (selectedFile) payload.append('image', selectedFile);

    try {
      const token = localStorage.getItem('token');
      const response = await fetch('/api/rental/add', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: payload
      });
      const data = await response.json();
      
      if (data.success) {
        alert("Equipment added!");
        setFormData({ equipmentName: '', category: 'Tractors', ratePerHour: '', ratePerDay: '', latitude: '', longitude: '' });
        setSelectedFile(null);
        setPreviewUrl(null);
        setActiveTab('my-items');
      } else {
        alert(data.message || "Failed to add.");
      }
    } catch (error) {
      console.error("Submission Error:", error);
      alert("Network error.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleLock = async (itemId, currentLockStatus) => {
    try {
      const { data } = await apiClient.post('/api/rental/toggle-lock', { itemId, isLocked: !currentLockStatus });
      if (data.success) fetchMyItems();
    } catch (error) {
      console.error(error);
    }
  };

  const deleteItem = async (itemId) => {
    if (!window.confirm("Are you sure you want to delete this equipment?")) return;
    try {
      const { data } = await apiClient.post('/api/rental/delete', { itemId });
      if (data.success) fetchMyItems();
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div ref={containerRef} style={{ maxWidth: '800px', margin: '0 auto', paddingBottom: '40px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '32px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <button onClick={() => navigate('/home/farmer')} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
            <ArrowLeft size={20} color="#333" />
          </button>
          <div>
            <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 800, color: '#222' }}>Rental Hub</h1>
            <p style={{ margin: 0, fontSize: '14px', color: '#666' }}>Share and rent farm equipment</p>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', marginBottom: '32px', backgroundColor: '#fff', padding: '8px', borderRadius: '16px', border: '1px solid #eee' }}>
        <button onClick={() => setActiveTab('search')} style={{ flex: 1, padding: '14px', backgroundColor: activeTab === 'search' ? 'var(--peach-background)' : 'transparent', color: activeTab === 'search' ? 'var(--terracotta-primary)' : '#666', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 800, cursor: 'pointer', transition: 'all 0.2s' }}>Find Equipment</button>
        <button onClick={() => setActiveTab('my-items')} style={{ flex: 1, padding: '14px', backgroundColor: activeTab === 'my-items' ? 'var(--peach-background)' : 'transparent', color: activeTab === 'my-items' ? 'var(--terracotta-primary)' : '#666', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 800, cursor: 'pointer', transition: 'all 0.2s' }}>My Equipment</button>
        <button onClick={() => setActiveTab('add')} style={{ flex: 1, padding: '14px', backgroundColor: activeTab === 'add' ? 'var(--terracotta-primary)' : 'transparent', color: activeTab === 'add' ? '#fff' : '#666', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 800, cursor: 'pointer', transition: 'all 0.2s' }}>+ Post Equipment</button>
      </div>

      {activeTab === 'search' && (
        <div>
          <select 
            value={categoryFilter} 
            onChange={(e) => setCategoryFilter(e.target.value)}
            style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #ddd', fontSize: '15px', marginBottom: '24px', backgroundColor: '#fff', outline: 'none' }}
          >
            <option value="All">All Categories</option>
            <option value="Tractors">Tractors</option>
            <option value="Harvesters">Harvesters</option>
            <option value="Tools">Tools & Implements</option>
          </select>
          
          {isLoading ? <div style={{ textAlign: 'center', padding: '40px' }}><Loader2 size={32} style={{ animation: 'spin 1s linear infinite', color: 'var(--terracotta-primary)' }} /></div> : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '24px' }}>
              {rentals.map((item, idx) => {
                const imgSource = item.imageUrl?.startsWith('/api') ? item.imageUrl : `/api/rental/routes/uploads/rental_photos/${item.imageUrl}`;
                return (
                <div key={idx} style={{ backgroundColor: '#fff', borderRadius: '24px', overflow: 'hidden', border: '1px solid #eee', boxShadow: '0 8px 24px rgba(0,0,0,0.04)', opacity: item.isLocked ? 0.6 : 1, transition: 'transform 0.2s' }} onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'} onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
                  <div style={{ height: '180px', backgroundColor: '#f9f9f9', position: 'relative' }}>
                    {item.imageUrl ? (
                      <img src={imgSource} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="Equipment" />
                    ) : <Tractor size={48} color="#ddd" style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }} />}
                    {item.isLocked && <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 800 }}>CURRENTLY RENTED</div>}
                  </div>
                  <div style={{ padding: '24px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                      <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#222' }}>{item.equipmentName}</h3>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-end' }}>
                        {item.ratePerDay && <div style={{ backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', padding: '6px 12px', borderRadius: '12px', fontSize: '14px', fontWeight: 800 }}>{formatCurrency(item.ratePerDay)}/Day</div>}
                        {item.ratePerHour && <div style={{ backgroundColor: '#eef2ff', color: '#4f46e5', padding: '6px 12px', borderRadius: '12px', fontSize: '14px', fontWeight: 800 }}>{formatCurrency(item.ratePerHour)}/Hour</div>}
                      </div>
                    </div>
                    
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '24px', fontSize: '14px', color: '#555' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '16px' }}>👤</span> 
                        <span style={{ fontWeight: 600 }}>Posted By:</span> {item.ownerName || 'Farmer'}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '16px' }}>📞</span> 
                        <span style={{ fontWeight: 600 }}>Phone:</span> <a href={`tel:${item.ownerPhone || ''}`} style={{ color: 'var(--terracotta-primary)', textDecoration: 'none', fontWeight: 'bold' }}>{item.ownerPhone || 'N/A'}</a>
                      </div>
                    </div>

                    <button 
                      onClick={() => {
                        if (item.latitude && item.longitude) {
                          if (navigator.geolocation) {
                            navigator.geolocation.getCurrentPosition((pos) => {
                              window.open(`https://www.google.com/maps/dir/${pos.coords.latitude},${pos.coords.longitude}/${item.latitude},${item.longitude}`, '_blank');
                            }, () => {
                              window.open(`https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}`, '_blank');
                            });
                          } else {
                            window.open(`https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}`, '_blank');
                          }
                        } else {
                          alert("Exact coordinates not available for this equipment.");
                        }
                      }}
                      style={{ width: '100%', padding: '14px', backgroundColor: '#e8f5e9', color: '#2e7d32', border: '1px solid #c8e6c9', borderRadius: '16px', fontSize: '15px', fontWeight: 800, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', transition: 'background 0.2s' }}
                      onMouseEnter={e => e.currentTarget.style.backgroundColor = '#c8e6c9'}
                      onMouseLeave={e => e.currentTarget.style.backgroundColor = '#e8f5e9'}
                    >
                      <MapPin size={18} /> Open in Google Maps
                    </button>
                  </div>
                </div>
              )})}
              {rentals.length === 0 && <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '40px', color: '#888' }}>No equipment available matching your criteria.</div>}
            </div>
          )}
        </div>
      )}

      {activeTab === 'my-items' && (
        <div>
          {isLoading ? <div style={{ textAlign: 'center', padding: '40px' }}><Loader2 size={32} style={{ animation: 'spin 1s linear infinite', color: 'var(--terracotta-primary)' }} /></div> : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '24px' }}>
              {rentals.map((item, idx) => {
                const imgSource = item.imageUrl?.startsWith('/api') ? item.imageUrl : `/api/rental/routes/uploads/rental_photos/${item.imageUrl}`;
                return (
                <div key={idx} style={{ backgroundColor: '#fff', borderRadius: '24px', overflow: 'hidden', border: '1px solid #eee', boxShadow: '0 8px 24px rgba(0,0,0,0.04)' }}>
                  <div style={{ height: '160px', backgroundColor: '#f9f9f9', position: 'relative' }}>
                    {item.imageUrl && <img src={imgSource} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="Equipment" />}
                    <div style={{ position: 'absolute', top: '16px', right: '16px', backgroundColor: item.isLocked ? '#ef4444' : '#10b981', color: '#fff', padding: '6px 16px', borderRadius: '12px', fontSize: '13px', fontWeight: 800, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                      {item.isLocked ? 'Rented Out' : 'Available'}
                    </div>
                  </div>
                  <div style={{ padding: '24px' }}>
                    <h3 style={{ margin: '0 0 20px 0', fontSize: '18px', fontWeight: 800 }}>{item.equipmentName}</h3>
                    <div style={{ display: 'flex', gap: '12px' }}>
                      <button onClick={() => toggleLock(item.id, item.isLocked)} style={{ flex: 1, padding: '12px', backgroundColor: '#f5f5f5', color: '#333', border: 'none', borderRadius: '12px', fontSize: '14px', fontWeight: 700, cursor: 'pointer' }}>
                        {item.isLocked ? 'Mark Available' : 'Mark Rented'}
                      </button>
                      <button onClick={() => deleteItem(item.id)} style={{ padding: '12px 20px', backgroundColor: '#fee2e2', color: '#ef4444', border: 'none', borderRadius: '12px', fontSize: '14px', fontWeight: 700, cursor: 'pointer' }}>Delete</button>
                    </div>
                  </div>
                </div>
              )})}
              {rentals.length === 0 && <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '40px', color: '#888' }}>You haven't listed any equipment yet.</div>}
            </div>
          )}
        </div>
      )}

      {activeTab === 'add' && (
        <form onSubmit={handleAddSubmit} style={{ backgroundColor: '#fff', borderRadius: '32px', padding: '40px', border: '1px solid #eee', boxShadow: '0 12px 40px rgba(0,0,0,0.04)' }}>
          <div 
            onClick={() => fileInputRef.current?.click()}
            style={{ width: '100%', height: '240px', backgroundColor: '#f8fafc', borderRadius: '24px', border: '2px dashed #cbd5e1', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', overflow: 'hidden', marginBottom: '32px', transition: 'border 0.2s' }}
            onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--terracotta-primary)'}
            onMouseLeave={e => e.currentTarget.style.borderColor = '#cbd5e1'}
          >
            {previewUrl ? <img src={previewUrl} style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : (
              <><UploadCloud size={48} color="#94a3b8" style={{ marginBottom: '16px' }} /><span style={{ fontSize: '15px', color: '#64748b', fontWeight: 700 }}>Upload Equipment Photo</span></>
            )}
          </div>
          <input type="file" accept="image/*" ref={fileInputRef} onChange={handleFileChange} style={{ display: 'none' }} />

          <div style={{ marginBottom: '24px' }}>
            <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Equipment Name *</label>
            <input type="text" value={formData.equipmentName} onChange={e => setFormData({...formData, equipmentName: e.target.value})} required style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="e.g. Mahindra Tractor 50HP" />
          </div>

          <div style={{ marginBottom: '24px' }}>
            <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Category</label>
            <select value={formData.category} onChange={e => setFormData({...formData, category: e.target.value})} style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }}>
              <option value="Tractors">Tractors</option>
              <option value="Harvesters">Harvesters</option>
              <option value="Tools">Tools & Implements</option>
            </select>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Rate/Hour (₹)</label>
              <input type="number" value={formData.ratePerHour} onChange={e => setFormData({...formData, ratePerHour: e.target.value})} style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="0" />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Rate/Day (₹) *</label>
              <input type="number" value={formData.ratePerDay} onChange={e => setFormData({...formData, ratePerDay: e.target.value})} required style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="0" />
            </div>
          </div>

          <div style={{ marginBottom: '40px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
               <label style={{ fontSize: '14px', fontWeight: 700, color: '#334155' }}>Location (Lat / Lon)</label>
               <button type="button" onClick={handleFetchLocation} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 12px', borderRadius: '8px', border: 'none', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', fontSize: '12px', fontWeight: 800, cursor: 'pointer' }}>
                 {isLocating ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <Navigation size={14} />}
                 {isLocating ? 'Locating...' : 'Auto-Fetch GPS'}
               </button>
            </div>
            <div style={{ display: 'flex', gap: '16px' }}>
              <input type="text" value={formData.latitude} onChange={e => setFormData({...formData, latitude: e.target.value})} placeholder="Latitude" style={{ flex: 1, padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} />
              <input type="text" value={formData.longitude} onChange={e => setFormData({...formData, longitude: e.target.value})} placeholder="Longitude" style={{ flex: 1, padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} />
            </div>
          </div>

          <button 
            type="submit" disabled={isSubmitting}
            style={{ width: '100%', padding: '18px', backgroundColor: 'var(--golden-yellow)', color: '#fff', border: 'none', borderRadius: '16px', fontSize: '18px', fontWeight: 800, cursor: isSubmitting ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px', boxShadow: '0 8px 24px rgba(242, 163, 58, 0.2)' }}
          >
            {isSubmitting ? <Loader2 size={24} style={{ animation: 'spin 1s linear infinite' }} /> : <Plus size={24} />}
            {isSubmitting ? 'Posting...' : 'Post Equipment for Rent'}
          </button>
        </form>
      )}
    </div>
  );
};

export default FarmerRentalHub;
