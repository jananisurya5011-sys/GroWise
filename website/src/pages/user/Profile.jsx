import React, { useEffect, useState, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import Logo from '../../components/Logo';
import { Mail, Phone, Building2, Edit3, X, Save, Camera, Navigation, MapPin, Calendar, AtSign, Home, Briefcase, Plus, Trash2, CheckCircle2 } from 'lucide-react';
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

const Profile = () => {
  const { user } = useAuth();
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Global Edit State
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [globalEditForm, setGlobalEditForm] = useState({});
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  
  // Address Management State
  const [addresses, setAddresses] = useState([]);
  const [isAddressModalOpen, setIsAddressModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [addressForm, setAddressForm] = useState(null);
  const [addressToDelete, setAddressToDelete] = useState(null);
  const [gpsLoading, setGpsLoading] = useState(false);
  const [isFetchingAddress, setIsFetchingAddress] = useState(false);
  const [addressFetchError, setAddressFetchError] = useState(false);

  // Refs
  const containerRef = useRef(null);
  const editContainerRef = useRef(null);
  const fileInputRef = useRef(null);
  const addressModalRef = useRef(null);
  const deleteModalRef = useRef(null);
  const prevAddressCoords = useRef({ lat: null, lon: null });

  const fetchProfile = async () => {
    try {
      const { data } = await apiClient.post('/api/profile/fetch-details', { email: user.email });
      setProfileData(data);
      setAddresses(data.addresses || []);
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

  // Modal Animations
  useEffect(() => {
    if (isAddressModalOpen && addressModalRef.current) {
      gsap.fromTo(addressModalRef.current, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.3, ease: 'power2.out' });
    }
  }, [isAddressModalOpen]);

  useEffect(() => {
    if (isDeleteModalOpen && deleteModalRef.current) {
      gsap.fromTo(deleteModalRef.current, { opacity: 0, scale: 0.9 }, { opacity: 1, scale: 1, duration: 0.2, ease: 'back.out(1.7)' });
    }
  }, [isDeleteModalOpen]);

  // ==========================================
  // GLOBAL PROFILE EDIT LOGIC
  // ==========================================
  const handleEditClick = () => {
    setGlobalEditForm({ 
      phone: profileData.phone || '',
      name: profileData.name || '',
      username: profileData.username || ''
    });
    setPreviewUrl(null);
    setSelectedFile(null);
    setIsEditing(true);
  };

  const handleGlobalSave = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    const formData = new FormData();
    formData.append('email', user.email);
    formData.append('phone', globalEditForm.phone);
    formData.append('name', globalEditForm.name);
    formData.append('username', globalEditForm.username);
    if (selectedFile) formData.append('profile_image', selectedFile);

    try {
      await apiClient.post('/api/profile/update-details', formData, { headers: { 'Content-Type': 'multipart/form-data' }});
      await fetchProfile();
      setIsEditing(false);
    } catch (error) {
      console.error("Global save failed", error);
      notify.error("Failed to update profile details.");
    } finally {
      setIsSaving(false);
    }
  };

  // ==========================================
  // ADDRESS MANAGEMENT LOGIC
  // ==========================================
  const saveAddressesToBackend = async (newAddressesArray) => {
    setIsSaving(true);
    const formData = new FormData();
    formData.append('email', user.email);
    formData.append('addresses', JSON.stringify(newAddressesArray));

    try {
      await apiClient.post('/api/profile/update-details', formData, { headers: { 'Content-Type': 'multipart/form-data' }});
      setAddresses(newAddressesArray);
    } catch (error) {
      console.error("Address save failed", error);
      notify.error("Failed to sync addresses with database.");
    } finally {
      setIsSaving(false);
    }
  };

  const openAddressModal = (address = null) => {
    if (address) {
      setAddressForm({ ...address });
      prevAddressCoords.current = { lat: address.lat || 0.0, lon: address.lon || 0.0 };
    } else {
      setAddressForm({ id: Date.now().toString(), label: 'Home', addressText: '', lat: 0.0, lon: 0.0, isDefault: addresses.length === 0 });
      prevAddressCoords.current = { lat: 0.0, lon: 0.0 };
    }
    
    setAddressFetchError(false);
    setIsFetchingAddress(false);
    setIsAddressModalOpen(true);
  };

  const closeAddressModal = () => {
    gsap.to(addressModalRef.current, { 
      opacity: 0, y: 20, duration: 0.2, 
      onComplete: () => {
        setIsAddressModalOpen(false);
        setAddressForm(null);
      }
    });
  };

  const handleAddressSubmit = async (e) => {
    e.preventDefault();
    let updatedAddresses = [...addresses];
    
    if (addressForm.isDefault) {
      updatedAddresses = updatedAddresses.map(addr => ({ ...addr, isDefault: false }));
    }

    const existingIndex = updatedAddresses.findIndex(a => a.id === addressForm.id);
    if (existingIndex >= 0) {
      updatedAddresses[existingIndex] = addressForm;
    } else {
      updatedAddresses.push(addressForm);
    }

    await saveAddressesToBackend(updatedAddresses);
    closeAddressModal();
  };

  const confirmDeleteAddress = async () => {
    const updatedAddresses = addresses.filter(a => a.id !== addressToDelete.id);
    // If we deleted the default, make the first remaining address the default
    if (addressToDelete.isDefault && updatedAddresses.length > 0) {
      updatedAddresses[0].isDefault = true;
    }
    
    await saveAddressesToBackend(updatedAddresses);
    
    gsap.to(deleteModalRef.current, { 
      opacity: 0, scale: 0.9, duration: 0.2, 
      onComplete: () => {
        setIsDeleteModalOpen(false);
        setAddressToDelete(null);
      }
    });
  };

  const handleSetDefault = async (id) => {
    const updatedAddresses = addresses.map(addr => ({
      ...addr,
      isDefault: addr.id === id
    }));
    await saveAddressesToBackend(updatedAddresses);
  };

  const fetchGPS = () => {
    setGpsLoading(true);
    if (!navigator.geolocation) {
      notify.info("Geolocation is not supported by your browser.");
      setGpsLoading(false);
      return;
    }
    
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = position.coords.latitude;
        const lon = position.coords.longitude;
        setAddressForm(prev => ({ ...prev, lat, lon }));
        setGpsLoading(false);
      },
      () => {
        notify.info("Permission denied. (Lat/Lon will default to 0.0)");
        setGpsLoading(false);
      }
    );
  };

  useEffect(() => {
    if (!isAddressModalOpen || !addressForm) return;
    
    const lat = parseFloat(addressForm.lat);
    const lon = parseFloat(addressForm.lon);
    
    if (isNaN(lat) || isNaN(lon)) return;
    if (lat === 0 && lon === 0) return;
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return;
    if (lat === prevAddressCoords.current.lat && lon === prevAddressCoords.current.lon) return;

    const timer = setTimeout(async () => {
      setIsFetchingAddress(true);
      setAddressFetchError(false);
      
      const addr = await reverseGeocode(lat, lon);
      if (addr) {
        setAddressForm(prev => ({ ...prev, addressText: addr }));
        prevAddressCoords.current = { lat, lon };
      } else {
        setAddressFetchError(true);
      }
      setIsFetchingAddress(false);
    }, 500);

    return () => clearTimeout(timer);
  }, [addressForm?.lat, addressForm?.lon, isAddressModalOpen]);

  // ==========================================
  // RENDER
  // ==========================================
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
          <h1 style={{ fontSize: '28px', color: '#222', fontWeight: 800 }}>Edit Basic Information</h1>
          <button onClick={() => setIsEditing(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#888' }} disabled={isSaving}>
            <X size={28} />
          </button>
        </div>

        <form onSubmit={handleGlobalSave} style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 24px rgba(0,0,0,0.06)' }}>
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
            <input type="file" ref={fileInputRef} onChange={(e) => {
              if (e.target.files && e.target.files[0]) {
                setSelectedFile(e.target.files[0]);
                setPreviewUrl(URL.createObjectURL(e.target.files[0]));
              }
            }} accept="image/*" style={{ display: 'none' }} />
            <p style={{ marginTop: '8px', fontSize: '13px', color: 'var(--text-muted)' }}>Tap to change photo</p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '20px', marginBottom: '32px' }}>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Full Name</label>
              <input type="text" value={globalEditForm.name} onChange={e => setGlobalEditForm({...globalEditForm, name: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Username (No Spaces)</label>
              <input type="text" value={globalEditForm.username} onChange={e => setGlobalEditForm({...globalEditForm, username: e.target.value.replace(/\s/g, '')})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Phone Number</label>
              <input type="text" value={globalEditForm.phone} onChange={e => setGlobalEditForm({...globalEditForm, phone: e.target.value})} style={{ width: '100%', padding: '12px 16px', borderRadius: '12px', border: '1px solid #ddd', fontSize: '15px' }} />
            </div>
          </div>

          <button type="submit" disabled={isSaving} className="primary" style={{ width: '100%', height: '56px', fontSize: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
            {isSaving ? <AgriLoadingSpinner /> : <><Save size={20} /> Save Changes</>}
          </button>
        </form>
      </div>
    );
  }

  const getLabelIcon = (label) => {
    if (label === 'Home') return <Home size={16} />;
    if (label === 'Work') return <Briefcase size={16} />;
    return <MapPin size={16} />;
  };

  return (
    <div ref={containerRef} style={{ maxWidth: '800px', margin: '0 auto', position: 'relative' }}>
      
      {/* --------------------------- HEADER CARD --------------------------- */}
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '32px', backgroundColor: '#fff', padding: '32px', borderRadius: '24px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
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
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
              <span style={{ color: 'var(--terracotta-primary)', backgroundColor: 'var(--peach-background)', padding: '4px 10px', borderRadius: '20px', fontSize: '12px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                <AtSign size={12} /> {profileData.username || 'user'}
              </span>
              <span style={{ color: '#555', backgroundColor: '#eee', padding: '4px 10px', borderRadius: '20px', fontSize: '12px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '1px' }}>
                {profileData.isNgo ? 'Registered NGO' : 'User'}
              </span>
            </div>
            <p style={{ color: 'var(--text-muted)', fontSize: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Calendar size={14} /> Member Since: {profileData.member_since}
            </p>
          </div>
        </div>
      </header>

      {/* --------------------------- USER DETAILS --------------------------- */}
      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)', marginBottom: '32px' }}>
        <h2 style={{ fontSize: '20px', borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '24px' }}>Account Details</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '24px' }}>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Mail size={14} /> Email
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.email}</p>
          </div>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Phone size={14} /> Phone
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.phone || 'Not provided'}</p>
          </div>
          {profileData.isNgo && (
            <div>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
                <Building2 size={14} /> Darpan ID
              </label>
              <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500, letterSpacing: '1px' }}>
                XXXX-XXXX-{profileData.darpan_masked || '0000'}
              </p>
            </div>
          )}
        </div>
      </section>

      {/* --------------------------- SAVED ADDRESSES --------------------------- */}
      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)', marginBottom: '32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '24px' }}>
          <h2 style={{ fontSize: '20px', margin: 0 }}>Saved Addresses</h2>
          <button onClick={() => openAddressModal()} style={{ display: 'flex', alignItems: 'center', gap: '6px', backgroundColor: 'var(--terracotta-primary)', color: '#fff', border: 'none', padding: '8px 16px', borderRadius: '20px', fontSize: '14px', fontWeight: 600, cursor: 'pointer' }}>
            <Plus size={16} /> Add New
          </button>
        </div>

        {addresses.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: '#888' }}>
            <MapPin size={48} color="#ddd" style={{ marginBottom: '16px' }} />
            <p>No saved addresses yet. Add one for faster checkouts!</p>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: '16px' }}>
            {addresses.map((addr) => (
              <div key={addr.id} style={{ border: addr.isDefault ? '2px solid var(--peach-background)' : '1px solid #eee', borderRadius: '16px', padding: '20px', backgroundColor: addr.isDefault ? '#fffbfa' : '#fff', position: 'relative' }}>
                
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '6px', backgroundColor: '#f0f0f0', color: '#555', padding: '4px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: 700, textTransform: 'uppercase' }}>
                      {getLabelIcon(addr.label)} {addr.label}
                    </span>
                    {addr.isDefault && (
                      <span style={{ backgroundColor: 'var(--terracotta-primary)', color: '#fff', padding: '4px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <CheckCircle2 size={12} /> Default
                      </span>
                    )}
                  </div>
                  
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button onClick={() => openAddressModal(addr)} style={{ background: 'none', border: 'none', color: '#666', cursor: 'pointer', padding: '4px' }}>
                      <Edit3 size={18} />
                    </button>
                    <button onClick={() => { setAddressToDelete(addr); setIsDeleteModalOpen(true); }} style={{ background: 'none', border: 'none', color: '#e53935', cursor: 'pointer', padding: '4px' }}>
                      <Trash2 size={18} />
                    </button>
                  </div>
                </div>

                <p style={{ fontSize: '15px', color: '#333', margin: '0 0 12px 0', lineHeight: 1.5 }}>{addr.addressText}</p>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <p style={{ fontSize: '12px', color: '#999', margin: 0, fontFamily: 'monospace' }}>Lat: {addr.lat.toFixed(4)}, Lon: {addr.lon.toFixed(4)}</p>
                  
                  {!addr.isDefault && (
                    <button onClick={() => handleSetDefault(addr.id)} style={{ background: 'none', border: 'none', color: 'var(--terracotta-primary)', fontSize: '13px', fontWeight: 600, cursor: 'pointer', padding: 0 }}>
                      Set as Default
                    </button>
                  )}
                </div>

              </div>
            ))}
          </div>
        )}
        
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '40px' }}>
          <button onClick={handleEditClick} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 32px', backgroundColor: '#f5f5f5', color: '#555', border: 'none', borderRadius: '12px', fontWeight: 700, cursor: 'pointer', transition: 'all 0.2s ease', fontSize: '16px' }}>
            <Edit3 size={20} /> Edit Basic Information
          </button>
        </div>
      </section>

      {/* --------------------------- ADDRESS MODAL --------------------------- */}
      {isAddressModalOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div ref={addressModalRef} style={{ backgroundColor: '#fff', padding: '32px', borderRadius: '24px', width: '90%', maxWidth: '500px', opacity: 0 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <h2 style={{ fontSize: '20px', margin: 0 }}>{addressForm.addressText ? 'Edit Address' : 'Add New Address'}</h2>
              <button onClick={closeAddressModal} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#888' }}><X size={24} /></button>
            </div>

            <form onSubmit={handleAddressSubmit}>
              <div style={{ marginBottom: '20px' }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Label</label>
                <div style={{ display: 'flex', gap: '12px' }}>
                  {['Home', 'Work', 'Custom'].map(l => (
                    <button type="button" key={l} onClick={() => setAddressForm({...addressForm, label: l})} style={{ flex: 1, padding: '10px', borderRadius: '12px', border: addressForm.label === l ? '2px solid var(--terracotta-primary)' : '1px solid #ddd', backgroundColor: addressForm.label === l ? 'var(--peach-background)' : '#fff', color: addressForm.label === l ? 'var(--terracotta-primary)' : '#555', fontWeight: 600, cursor: 'pointer' }}>
                      {l}
                    </button>
                  ))}
                </div>
              </div>

              <div style={{ marginBottom: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <label style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Address</label>
                    {isFetchingAddress && <span style={{ fontSize: '12px', color: '#888' }}>Fetching address...</span>}
                    {addressFetchError && <span style={{ fontSize: '12px', color: '#c62828' }}>Unable to fetch address.</span>}
                  </div>
                  <button type="button" onClick={fetchGPS} disabled={gpsLoading} style={{ display: 'flex', alignItems: 'center', gap: '6px', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', border: 'none', padding: '4px 12px', borderRadius: '12px', fontSize: '12px', fontWeight: 700, cursor: 'pointer' }}>
                    {gpsLoading ? 'Locating...' : <><Navigation size={12} /> Use GPS</>}
                  </button>
                </div>
                <textarea required value={addressForm.addressText} onChange={e => setAddressForm({...addressForm, addressText: e.target.value})} placeholder="Enter full address..." disabled={isFetchingAddress} style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px solid #ddd', minHeight: '80px', fontFamily: 'inherit', opacity: isFetchingAddress ? 0.6 : 1 }} />
              </div>

              <div style={{ display: 'flex', gap: '16px', marginBottom: '24px' }}>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Latitude</label>
                  <input type="number" step="any" value={addressForm.lat} onChange={e => setAddressForm({...addressForm, lat: parseFloat(e.target.value) || 0.0})} style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #eee', backgroundColor: '#fafafa', fontSize: '13px' }} />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Longitude</label>
                  <input type="number" step="any" value={addressForm.lon} onChange={e => setAddressForm({...addressForm, lon: parseFloat(e.target.value) || 0.0})} style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #eee', backgroundColor: '#fafafa', fontSize: '13px' }} />
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '24px' }}>
                <input type="checkbox" id="defaultAddr" checked={addressForm.isDefault} onChange={e => setAddressForm({...addressForm, isDefault: e.target.checked})} style={{ width: '18px', height: '18px', accentColor: 'var(--terracotta-primary)' }} />
                <label htmlFor="defaultAddr" style={{ fontSize: '14px', color: '#444', fontWeight: 500 }}>Set as Default Address</label>
              </div>

              <button type="submit" disabled={isSaving} className="primary" style={{ width: '100%', height: '50px', fontSize: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                {isSaving ? <AgriLoadingSpinner /> : <><Save size={18} /> Save Address</>}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* --------------------------- DELETE CONFIRMATION MODAL --------------------------- */}
      {isDeleteModalOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.6)', zIndex: 1100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div ref={deleteModalRef} style={{ backgroundColor: '#fff', padding: '32px', borderRadius: '24px', width: '90%', maxWidth: '400px', textAlign: 'center', opacity: 0 }}>
            <div style={{ width: '64px', height: '64px', borderRadius: '50%', backgroundColor: '#ffebee', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px auto' }}>
              <Trash2 size={32} color="#e53935" />
            </div>
            <h3 style={{ margin: '0 0 12px 0', fontSize: '22px', color: '#333' }}>Delete Address?</h3>
            <p style={{ color: '#666', fontSize: '15px', margin: '0 0 32px 0', lineHeight: 1.5 }}>
              Are you sure you want to delete your <b>{addressToDelete?.label}</b> address? This action cannot be undone.
            </p>
            <div style={{ display: 'flex', gap: '16px' }}>
              <button onClick={() => { setIsDeleteModalOpen(false); setAddressToDelete(null); }} style={{ flex: 1, padding: '14px', borderRadius: '12px', border: '1px solid #ddd', backgroundColor: '#fff', color: '#555', fontWeight: 700, cursor: 'pointer' }}>Cancel</button>
              <button onClick={confirmDeleteAddress} disabled={isSaving} style={{ flex: 1, padding: '14px', borderRadius: '12px', border: 'none', backgroundColor: '#e53935', color: '#fff', fontWeight: 700, cursor: 'pointer', display: 'flex', justifyContent: 'center' }}>
                {isSaving ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

export default Profile;
