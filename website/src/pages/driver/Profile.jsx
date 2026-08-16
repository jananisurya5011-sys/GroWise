import React, { useEffect, useState, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import Logo from '../../components/Logo';
import { Phone, Truck, CreditCard, Edit3, X, Save, Camera, MapPin, CheckCircle2, AlertCircle, FileText, Lock } from 'lucide-react';

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
  
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  
  // Vault State
  const [isVaultOpen, setIsVaultOpen] = useState(false);

  const containerRef = useRef(null);
  const editContainerRef = useRef(null);
  const fileInputRef = useRef(null);
  const vaultRef = useRef(null);

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

  useEffect(() => {
    if (isVaultOpen && vaultRef.current) {
      gsap.fromTo(vaultRef.current, { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' });
    }
  }, [isVaultOpen]);

  const handleEditClick = () => {
    setPreviewUrl(null);
    setSelectedFile(null);
    setIsEditing(true);
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    
    if (!selectedFile) {
      alert("Please select a new profile photo before saving.");
      return;
    }
    
    setIsSaving(true);
    
    const formData = new FormData();
    formData.append('email', user.email);
    formData.append('profile_image', selectedFile);

    try {
      await apiClient.post('/api/profile/update-details', formData, { headers: { 'Content-Type': 'multipart/form-data' }});
      await fetchProfile();
      setIsEditing(false);
    } catch (error) {
      console.error("Save failed", error);
      alert("Failed to update profile.");
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
  
  const isApproved = !!profileData.driverId;

  if (isEditing) {
    return (
      <div ref={editContainerRef} style={{ maxWidth: '800px', margin: '0 auto', opacity: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
          <h1 style={{ fontSize: '28px', color: '#222', fontWeight: 800 }}>Edit Information</h1>
          <button onClick={() => setIsEditing(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#888' }} disabled={isSaving}>
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

          <button type="submit" disabled={isSaving || !selectedFile} className="primary" style={{ width: '100%', height: '56px', fontSize: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', opacity: (!selectedFile || isSaving) ? 0.5 : 1, cursor: (!selectedFile || isSaving) ? 'not-allowed' : 'pointer' }}>
            {isSaving ? <AgriLoadingSpinner /> : <><Save size={20} /> Save Changes</>}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div ref={containerRef} style={{ maxWidth: '800px', margin: '0 auto', position: 'relative' }}>
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
            <p style={{ color: 'var(--text-muted)', fontSize: '15px', marginBottom: '4px' }}>{profileData.email}</p>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px' }}>
              <span style={{ padding: '4px 10px', backgroundColor: isApproved ? '#E8F5E9' : '#FFF3E0', color: isApproved ? '#2E7D32' : '#E65100', borderRadius: '20px', fontSize: '12px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                {isApproved ? <><CheckCircle2 size={12} /> Approved</> : <><AlertCircle size={12} /> Pending Verification</>}
              </span>
              {isApproved && (
                <span style={{ color: 'var(--terracotta-primary)', fontWeight: 600, fontSize: '13px', textTransform: 'uppercase', letterSpacing: '1px' }}>
                  ID: {profileData.driverId}
                </span>
              )}
            </div>
          </div>
        </div>
      </header>

      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)', marginBottom: '32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '24px' }}>
          <h2 style={{ fontSize: '20px', margin: 0 }}>Driver & Vehicle Details</h2>
        </div>
        
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '24px' }}>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Phone size={14} /> Phone
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.phone || 'Not provided'}</p>
          </div>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <Truck size={14} /> Vehicle Type
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500 }}>{profileData.vehicleType || 'Not set'}</p>
          </div>
          <div>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              <CreditCard size={14} /> Aadhaar Number
            </label>
            <p style={{ fontSize: '16px', color: '#333', marginTop: '4px', fontWeight: 500, letterSpacing: '1px' }}>
              XXXX-XXXX-{profileData.aadhaar_masked || '0000'}
            </p>
          </div>
        </div>


        
        <div style={{ marginTop: '24px' }}>
           <button onClick={() => setIsVaultOpen(true)} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', padding: '16px', width: '100%', backgroundColor: '#f0f4f8', color: '#3b82f6', border: '1px solid #dbeafe', borderRadius: '16px', fontWeight: 700, cursor: 'pointer', transition: 'all 0.2s ease', fontSize: '15px' }}>
             <Lock size={18} /> Open Document Vault
           </button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '40px' }}>
          <button onClick={handleEditClick} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 32px', backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', border: 'none', borderRadius: '12px', fontWeight: 700, cursor: 'pointer', transition: 'all 0.2s ease', fontSize: '16px' }}>
            <Edit3 size={20} /> Edit Profile Photo
          </button>
        </div>
      </section>
      
      {/* --------------------------- DOCUMENT VAULT MODAL --------------------------- */}
      {isVaultOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.8)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(5px)' }}>
          <div ref={vaultRef} style={{ backgroundColor: '#fff', borderRadius: '24px', width: '90%', maxWidth: '800px', height: '85vh', opacity: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <div style={{ padding: '24px 32px', backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2 style={{ margin: 0, fontSize: '20px', display: 'flex', alignItems: 'center', gap: '8px', color: '#334155' }}>
                <Lock size={22} color="#3b82f6" /> Secure Document Vault
              </h2>
              <button onClick={() => {
                gsap.to(vaultRef.current, { 
                  opacity: 0, y: 30, duration: 0.3, 
                  onComplete: () => setIsVaultOpen(false) 
                });
              }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b' }}>
                <X size={28} />
              </button>
            </div>
            
            <div style={{ padding: '32px', overflowY: 'auto', flex: 1, backgroundColor: '#f1f5f9' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '32px' }}>
                
                {/* RC Book */}
                <div style={{ backgroundColor: '#fff', borderRadius: '16px', padding: '20px', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.05)' }}>
                  <h3 style={{ margin: '0 0 16px 0', fontSize: '16px', color: '#334155', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <FileText size={18} color="#64748b" /> Vehicle RC Book
                  </h3>
                  <div style={{ width: '100%', height: '250px', backgroundColor: '#e2e8f0', borderRadius: '12px', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    {profileData.rcBookUrl ? (
                      <img src={profileData.rcBookUrl.startsWith('/') ? profileData.rcBookUrl : `/uploads/drivers/${profileData.rcBookUrl}`} alt="RC Book" style={{ width: '100%', height: '100%', objectFit: 'contain', backgroundColor: '#000' }} />
                    ) : (
                      <span style={{ color: '#94a3b8', fontWeight: 500 }}>No Document Logged</span>
                    )}
                  </div>
                </div>

                {/* Driving License */}
                <div style={{ backgroundColor: '#fff', borderRadius: '16px', padding: '20px', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.05)' }}>
                  <h3 style={{ margin: '0 0 16px 0', fontSize: '16px', color: '#334155', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <FileText size={18} color="#64748b" /> Driving License
                  </h3>
                  <div style={{ width: '100%', height: '250px', backgroundColor: '#e2e8f0', borderRadius: '12px', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    {profileData.licenseUrl ? (
                      <img src={profileData.licenseUrl.startsWith('/') ? profileData.licenseUrl : `/uploads/drivers/${profileData.licenseUrl}`} alt="Driving License" style={{ width: '100%', height: '100%', objectFit: 'contain', backgroundColor: '#000' }} />
                    ) : (
                      <span style={{ color: '#94a3b8', fontWeight: 500 }}>No Document Logged</span>
                    )}
                  </div>
                </div>

              </div>
              <p style={{ textAlign: 'center', marginTop: '24px', fontSize: '13px', color: '#94a3b8' }}>
                Documents are locked and cannot be edited. Please contact support to update your regulatory documents.
              </p>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

export default Profile;
