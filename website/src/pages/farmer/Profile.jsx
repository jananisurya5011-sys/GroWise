import React, { useEffect, useState, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import Logo from '../../components/Logo';
import { Mail, Phone, Sprout, Map, MapPin, Edit3, X, Save, Camera, Navigation, CreditCard } from 'lucide-react';
import { reverseGeocode } from '../../utils/geoUtils';
import notify from '../../services/NotificationService';


const AgriLoadingSpinner = () => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--terracotta-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ animation: 'spin 1.5s linear infinite' }}>
    <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
    <path d="M2 22l5-5" />
    <path d="M9 19c-2.8-2.8-2.8-7.2 0-10s7.2-2.8 10 0" />
    <path d="M12 2v2" />
    <path d="M22 12h-2" />
    <path d="M12 22v-2" />
    <path d="M2 12h2" />
    <path d="M4.93 4.93l1.41 1.41" />
    <path d="M19.07 19.07l-1.41-1.41" />
  </svg>
);

const isInvalidLat = (val) => val !== '' && val !== '-' && (parseFloat(val) < -90 || parseFloat(val) > 90 || isNaN(parseFloat(val)));
const isInvalidLon = (val) => val !== '' && val !== '-' && (parseFloat(val) < -180 || parseFloat(val) > 180 || isNaN(parseFloat(val)));

const Profile = () => {
  const { user } = useAuth();
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [editForm, setEditForm] = useState({});
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [gpsError, setGpsError] = useState('');

  const [isFetchingAddress, setIsFetchingAddress] = useState({ home: false, farm: false });
  const [addressFetchError, setAddressFetchError] = useState({ home: false, farm: false });

  const containerRef = useRef(null);
  const editContainerRef = useRef(null);
  const fileInputRef = useRef(null);
  
  const prevHomeCoords = useRef({ lat: null, lon: null });
  const prevFarmCoords = useRef({ lat: null, lon: null });

  const fetchProfile = async () => {
    try {
      const { data } = await apiClient.post('/api/profile/fetch-details', { email: user.email });
      setProfileData(data);
    } catch (error) {
      console.error("Failed to load profile", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, [user.email]);

  useEffect(() => {
    if (!loading) {
      if (isEditing && editContainerRef.current) {
        gsap.fromTo(editContainerRef.current, { opacity: 0, scale: 0.98 }, { opacity: 1, scale: 1, duration: 0.4, ease: 'power2.out' });
      } else if (!isEditing && containerRef.current) {
        gsap.fromTo(containerRef.current.children, { opacity: 0, y: 15 }, { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' });
      }
    }
  }, [loading, isEditing]);

  // Debounced reverse geocoding for Home Address
  useEffect(() => {
    if (!isEditing || editForm.homeLat === '' || editForm.homeLon === '') return;
    
    const lat = parseFloat(editForm.homeLat);
    const lon = parseFloat(editForm.homeLon);
    
    if (lat === 0 && lon === 0) return;
    if (isInvalidLat(editForm.homeLat) || isInvalidLon(editForm.homeLon)) return;
    if (lat === prevHomeCoords.current.lat && lon === prevHomeCoords.current.lon) return;

    const timer = setTimeout(async () => {
      setIsFetchingAddress(prev => ({ ...prev, home: true }));
      setAddressFetchError(prev => ({ ...prev, home: false }));
      
      const addr = await reverseGeocode(lat, lon);
      if (addr) {
        setEditForm(prev => {
          const next = { ...prev, homeAddress: addr };
          if (prev.isSameAddress) next.farmAddress = addr;
          return next;
        });
        prevHomeCoords.current = { lat, lon };
        if (editForm.isSameAddress) prevFarmCoords.current = { lat, lon };
      } else {
        setAddressFetchError(prev => ({ ...prev, home: true }));
      }
      setIsFetchingAddress(prev => ({ ...prev, home: false }));
    }, 500);

    return () => clearTimeout(timer);
  }, [editForm.homeLat, editForm.homeLon, isEditing, editForm.isSameAddress]);

  // Debounced reverse geocoding for Farm Address
  useEffect(() => {
    if (!isEditing || editForm.isSameAddress || editForm.farmLat === '' || editForm.farmLon === '') return;
    
    const lat = parseFloat(editForm.farmLat);
    const lon = parseFloat(editForm.farmLon);
    
    if (lat === 0 && lon === 0) return;
    if (isInvalidLat(editForm.farmLat) || isInvalidLon(editForm.farmLon)) return;
    if (lat === prevFarmCoords.current.lat && lon === prevFarmCoords.current.lon) return;

    const timer = setTimeout(async () => {
      setIsFetchingAddress(prev => ({ ...prev, farm: true }));
      setAddressFetchError(prev => ({ ...prev, farm: false }));
      
      const addr = await reverseGeocode(lat, lon);
      if (addr) {
        setEditForm(prev => ({ ...prev, farmAddress: addr }));
        prevFarmCoords.current = { lat, lon };
      } else {
        setAddressFetchError(prev => ({ ...prev, farm: true }));
      }
      setIsFetchingAddress(prev => ({ ...prev, farm: false }));
    }, 500);

    return () => clearTimeout(timer);
  }, [editForm.farmLat, editForm.farmLon, isEditing, editForm.isSameAddress]);

  const handleEditClick = () => {
    setEditForm({
      phone: profileData.phone || '',
      name: profileData.name || '',
      username: profileData.username || '',
      primaryCrop: profileData.primary_crop || '',
      soilType: profileData.soil_type || '',
      totalAcreage: profileData.total_acreage || '',
      homeAddress: profileData.home_address || '',
      homeLat: profileData.homeLat || 0.0,
      homeLon: profileData.homeLon || 0.0,
      farmAddress: profileData.farm_address || '',
      farmLat: profileData.farmLat || 0.0,
      farmLon: profileData.farmLon || 0.0,
      isSameAddress: profileData.is_same_address || false
    });
    
    prevHomeCoords.current = { lat: profileData.homeLat || 0.0, lon: profileData.homeLon || 0.0 };
    prevFarmCoords.current = { lat: profileData.farmLat || 0.0, lon: profileData.farmLon || 0.0 };
    
    setPreviewUrl(null);
    setSelectedFile(null);
    setGpsError('');
    setIsEditing(true);
  };

  const handleCancelEdit = () => setIsEditing(false);

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const fetchGPS = (target) => {
    setGpsError('');
    if (!navigator.geolocation) {
      setGpsError("Geolocation is not supported by your browser.");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = position.coords.latitude;
        const lon = position.coords.longitude;
        if (target === 'home') {
          setEditForm(prev => {
            const next = { ...prev, homeLat: lat, homeLon: lon };
            if (prev.isSameAddress) {
              next.farmLat = lat;
              next.farmLon = lon;
            }
            return next;
          });
        } else {
          setEditForm(prev => ({ ...prev, farmLat: lat, farmLon: lon }));
        }
      },
      () => setGpsError("Permission denied. (Lat/Lon will default to 0.0)")
    );
  };

  const handleCheckboxChange = (e) => {
    const isChecked = e.target.checked;
    setEditForm(prev => ({
      ...prev, isSameAddress: isChecked,
      farmAddress: isChecked ? prev.homeAddress : prev.farmAddress,
      farmLat: isChecked ? prev.homeLat : prev.farmLat,
      farmLon: isChecked ? prev.homeLon : prev.farmLon
    }));
  };

  const handleAddressChange = (e, type) => {
    const val = e.target.value;
    if (type === 'home') {
      setEditForm(prev => {
        const next = { ...prev, homeAddress: val, homeLat: 0.0, homeLon: 0.0 };
        if (prev.isSameAddress) {
          next.farmAddress = val;
          next.farmLat = 0.0;
          next.farmLon = 0.0;
        }
        return next;
      });
    } else {
      setEditForm(prev => ({ ...prev, farmAddress: val, farmLat: 0.0, farmLon: 0.0 }));
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    
    const formData = new FormData();
    formData.append('email', user.email);
    formData.append('phone', editForm.phone);
    formData.append('name', editForm.name);
    formData.append('username', editForm.username);
    formData.append('primaryCrop', editForm.primaryCrop);
    formData.append('soilType', editForm.soilType);
    formData.append('totalAcreage', editForm.totalAcreage);
    formData.append('homeAddress', editForm.homeAddress);
    formData.append('homeLat', editForm.homeLat || 0.0);
    formData.append('homeLon', editForm.homeLon || 0.0);
    formData.append('farmAddress', editForm.farmAddress);
    formData.append('farmLat', editForm.farmLat || 0.0);
    formData.append('farmLon', editForm.farmLon || 0.0);
    formData.append('isSameAddress', editForm.isSameAddress);
    
    if (selectedFile) formData.append('profile_image', selectedFile);

    try {
      await apiClient.post('/api/profile/update-details', formData, { headers: { 'Content-Type': 'multipart/form-data' }});
      await fetchProfile();
      setIsEditing(false);
    } catch (error) {
      console.error("Save failed", error);
      notify.error("Failed to update profile.");
    } finally {
      setIsSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '60vh' }}>
        <AgriLoadingSpinner />
        <p style={{ marginTop: '16px', color: 'var(--terracotta-primary)', fontWeight: 600 }}>Loading Profile...</p>
      </div>
    );
  }

  if (!profileData) return <div>Error loading profile.</div>;
  const currentAvatar = previewUrl || profileData.profile_image_url;

  if (isEditing) {
    return (
      <div ref={editContainerRef} style={{ maxWidth: '800px', margin: '0 auto', opacity: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
          <h1 style={{ fontSize: '28px', color: '#222', fontWeight: 800 }}>Edit Information</h1>
          <button onClick={handleCancelEdit} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#888' }} disabled={isSaving}>
            <X size={28} />
          </button>
        </div>

        <form onSubmit={handleSave} style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 24px rgba(0,0,0,0.06)' }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: '32px' }}>
            <div 
              onClick={() => fileInputRef.current?.click()}
              style={{ position: 'relative', width: '120px', height: '120px', borderRadius: '50%', overflow: 'hidden', border: '4px solid var(--peach-background)', cursor: 'pointer', backgroundColor: '#f9f9f9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
              {currentAvatar ? (
                <img src={currentAvatar} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <Logo size={80} />
              )}
              <div style={{ position: 'absolute', bottom: 0, left: 0, width: '100%', height: '30%', backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                <Camera size={18} color="#fff" />
              </div>
            </div>
            <input type="file" ref={fileInputRef} onChange={handleFileChange} accept="image/*" style={{ display: 'none' }} />
            <p style={{ marginTop: '8px', fontSize: '13px', color: 'var(--text-muted)' }}>Tap to change photo</p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '24px', marginBottom: '32px' }}>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Full Name</label>
              <input type="text" value={editForm.name} onChange={e => setEditForm({...editForm, name: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Username (No Spaces)</label>
              <input type="text" value={editForm.username} onChange={e => setEditForm({...editForm, username: e.target.value.replace(/\s/g, '')})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Phone Number</label>
              <input type="text" value={editForm.phone} onChange={e => setEditForm({...editForm, phone: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Primary Crop</label>
              <input type="text" value={editForm.primaryCrop} onChange={e => setEditForm({...editForm, primaryCrop: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Soil Type</label>
              <select value={editForm.soilType} onChange={e => setEditForm({...editForm, soilType: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px', backgroundColor: '#fff' }}>
                <option value="">Select Soil Type</option>
                <option value="Alluvial">Alluvial</option>
                <option value="Black">Black</option>
                <option value="Red">Red</option>
                <option value="Laterite">Laterite</option>
              </select>
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Total Acreage</label>
              <input type="number" value={editForm.totalAcreage} onChange={e => setEditForm({...editForm, totalAcreage: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid #eee', margin: '32px 0' }} />
          <h3 style={{ fontSize: '18px', marginBottom: '16px', color: '#333' }}>Location Details</h3>
          {gpsError && <div style={{ backgroundColor: '#ffebee', color: '#c62828', padding: '12px 16px', borderRadius: '8px', fontSize: '13px', marginBottom: '24px', fontWeight: 500 }}>{gpsError}</div>}

          <div style={{ marginBottom: '24px', padding: '24px', backgroundColor: '#fafafa', borderRadius: '16px', border: '1px solid #eee' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h4 style={{ margin: 0, color: 'var(--terracotta-primary)' }}>Home Address</h4>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                {isFetchingAddress.home && <span style={{ fontSize: '12px', color: '#888' }}>Fetching address...</span>}
                {addressFetchError.home && <span style={{ fontSize: '12px', color: '#c62828' }}>Unable to fetch address.</span>}
                <button type="button" onClick={() => fetchGPS('home')} style={{ display: 'flex', alignItems: 'center', gap: '6px', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', border: 'none', padding: '8px 16px', borderRadius: '20px', fontSize: '13px', fontWeight: 700, cursor: 'pointer' }}>
                  <Navigation size={14} /> Use GPS
                </button>
              </div>
            </div>
            <textarea value={editForm.homeAddress} onChange={(e) => handleAddressChange(e, 'home')} placeholder="Enter home address manually..." disabled={isFetchingAddress.home} style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #ddd', minHeight: '80px', marginBottom: '12px', fontFamily: 'inherit', fontSize: '14px', opacity: isFetchingAddress.home ? 0.6 : 1 }} />
            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{ flex: 1 }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>Latitude</label>
                <input type="number" step="any" value={editForm.homeLat} onChange={(e) => setEditForm({...editForm, homeLat: e.target.value})} style={{ width: '100%', padding: '10px', borderRadius: '8px', border: isInvalidLat(editForm.homeLat) ? '1px solid #ef4444' : '1px solid #ddd', backgroundColor: '#fff', color: '#333', fontSize: '13px' }} placeholder="Lat (-90 to 90)" />
                {isInvalidLat(editForm.homeLat) && <div style={{ color: '#ef4444', fontSize: '11px', marginTop: '4px' }}>Must be between -90 and 90</div>}
              </div>
              <div style={{ flex: 1 }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>Longitude</label>
                <input type="number" step="any" value={editForm.homeLon} onChange={(e) => setEditForm({...editForm, homeLon: e.target.value})} style={{ width: '100%', padding: '10px', borderRadius: '8px', border: isInvalidLon(editForm.homeLon) ? '1px solid #ef4444' : '1px solid #ddd', backgroundColor: '#fff', color: '#333', fontSize: '13px' }} placeholder="Lon (-180 to 180)" />
                {isInvalidLon(editForm.homeLon) && <div style={{ color: '#ef4444', fontSize: '11px', marginTop: '4px' }}>Must be between -180 and 180</div>}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '24px', marginLeft: '8px' }}>
            <input type="checkbox" id="sameAddress" checked={editForm.isSameAddress} onChange={handleCheckboxChange} style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--terracotta-primary)' }} />
            <label htmlFor="sameAddress" style={{ fontSize: '15px', color: '#444', cursor: 'pointer', fontWeight: 500 }}>Both addresses are the same</label>
          </div>

          <div style={{ marginBottom: '24px', padding: '24px', backgroundColor: editForm.isSameAddress ? '#f5f5f5' : '#fafafa', borderRadius: '16px', border: '1px solid #eee', opacity: editForm.isSameAddress ? 0.6 : 1, pointerEvents: editForm.isSameAddress ? 'none' : 'auto', transition: 'all 0.3s ease' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h4 style={{ margin: 0, color: 'var(--terracotta-primary)' }}>Farm Address</h4>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                {isFetchingAddress.farm && <span style={{ fontSize: '12px', color: '#888' }}>Fetching address...</span>}
                {addressFetchError.farm && <span style={{ fontSize: '12px', color: '#c62828' }}>Unable to fetch address.</span>}
                {!editForm.isSameAddress && (
                  <button type="button" onClick={() => fetchGPS('farm')} style={{ display: 'flex', alignItems: 'center', gap: '6px', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', border: 'none', padding: '8px 16px', borderRadius: '20px', fontSize: '13px', fontWeight: 700, cursor: 'pointer' }}>
                    <Navigation size={14} /> Use GPS
                  </button>
                )}
              </div>
            </div>
            <textarea value={editForm.farmAddress} onChange={(e) => handleAddressChange(e, 'farm')} placeholder="Enter farm address manually..." disabled={isFetchingAddress.farm} style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #ddd', minHeight: '80px', marginBottom: '12px', fontFamily: 'inherit', fontSize: '14px', opacity: isFetchingAddress.farm ? 0.6 : 1 }} />
            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{ flex: 1 }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>Latitude</label>
                <input type="number" step="any" value={editForm.farmLat} onChange={(e) => setEditForm({...editForm, farmLat: e.target.value})} style={{ width: '100%', padding: '10px', borderRadius: '8px', border: isInvalidLat(editForm.farmLat) ? '1px solid #ef4444' : '1px solid #ddd', backgroundColor: '#fff', color: '#333', fontSize: '13px' }} placeholder="Lat (-90 to 90)" />
                {isInvalidLat(editForm.farmLat) && <div style={{ color: '#ef4444', fontSize: '11px', marginTop: '4px' }}>Must be between -90 and 90</div>}
              </div>
              <div style={{ flex: 1 }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>Longitude</label>
                <input type="number" step="any" value={editForm.farmLon} onChange={(e) => setEditForm({...editForm, farmLon: e.target.value})} style={{ width: '100%', padding: '10px', borderRadius: '8px', border: isInvalidLon(editForm.farmLon) ? '1px solid #ef4444' : '1px solid #ddd', backgroundColor: '#fff', color: '#333', fontSize: '13px' }} placeholder="Lon (-180 to 180)" />
                {isInvalidLon(editForm.farmLon) && <div style={{ color: '#ef4444', fontSize: '11px', marginTop: '4px' }}>Must be between -180 and 180</div>}
              </div>
            </div>
          </div>

          <button type="submit" disabled={isSaving} className="primary" style={{ width: '100%', height: '56px', fontSize: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
            {isSaving ? <AgriLoadingSpinner /> : <><Save size={20} /> Save Changes</>}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div ref={containerRef} style={{ maxWidth: '800px', margin: '0 auto' }}>
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '40px', backgroundColor: '#fff', padding: '32px', borderRadius: '24px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
          <div style={{ position: 'relative', width: '100px', height: '100px', borderRadius: '50%', overflow: 'hidden', border: '4px solid var(--peach-background)' }}>
            {profileData.profile_image_url ? (
              <img src={profileData.profile_image_url} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--peach-background)' }}>
                <Logo size={60} />
              </div>
            )}
          </div>
          <div>
            <h1 style={{ fontSize: '28px', color: '#222', marginBottom: '4px', fontWeight: 800 }}>{profileData.name}</h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <span style={{ color: 'var(--terracotta-primary)', backgroundColor: 'var(--peach-background)', padding: '4px 10px', borderRadius: '20px', fontSize: '12px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Edit3 size={12} /> @{profileData.username || 'farmer'}
              </span>
            </div>
            <p style={{ color: 'var(--text-muted)', fontSize: '15px', marginBottom: '4px' }}>{profileData.email}</p>
            <p style={{ color: 'var(--terracotta-primary)', fontWeight: 600, fontSize: '14px', textTransform: 'uppercase', letterSpacing: '1px' }}>
              {profileData.role}
            </p>
          </div>
        </div>
      </header>

      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
        <h2 style={{ fontSize: '20px', borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '24px' }}>Farmer Details</h2>
        
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '24px' }}>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Phone size={14} /> Phone
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.phone || 'Not provided'}</p>
          </div>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Sprout size={14} /> Primary Crop
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.primary_crop || 'Not Set'}</p>
          </div>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Map size={14} /> Total Acreage
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.total_acreage || 'Not set'}</p>
          </div>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <MapPin size={14} /> Soil Type
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.soil_type || 'Not Set'}</p>
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <CreditCard size={14} /> Aadhaar Number
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500, letterSpacing: '1px' }}>
              XXXX-XXXX-{profileData.aadhaar_masked || '0000'}
            </p>
          </div>
        </div>

        <div style={{ marginTop: '32px', display: 'flex', flexWrap: 'wrap', gap: '24px' }}>
          <div style={{ flex: 1, minWidth: '250px', backgroundColor: '#fafafa', padding: '20px', borderRadius: '16px', border: '1px solid #eee' }}>
            <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', color: 'var(--terracotta-primary)' }}>Home Address</h4>
            <p style={{ fontSize: '15px', color: '#444', margin: 0, lineHeight: 1.5 }}>{profileData.home_address || 'Not Set'}</p>
          </div>
          <div style={{ flex: 1, minWidth: '250px', backgroundColor: '#fafafa', padding: '20px', borderRadius: '16px', border: '1px solid #eee' }}>
            <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', color: 'var(--terracotta-primary)' }}>Farm Address</h4>
            <p style={{ fontSize: '15px', color: '#444', margin: 0, lineHeight: 1.5 }}>{profileData.farm_address || 'Not Set'}</p>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '40px' }}>
          <button onClick={handleEditClick} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 32px', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', border: 'none', borderRadius: '12px', fontWeight: 700, cursor: 'pointer', transition: 'all 0.2s ease', fontSize: '16px' }}>
            <Edit3 size={20} /> Edit Profile
          </button>
        </div>
      </section>
    </div>
  );
};

export default Profile;
