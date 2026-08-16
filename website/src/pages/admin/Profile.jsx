import React, { useEffect, useState, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import Logo from '../../components/Logo';
import { Phone, Edit3, X, Save, Camera, ShieldCheck, Mail } from 'lucide-react';

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
  const [editForm, setEditForm] = useState({});
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);

  const containerRef = useRef(null);
  const editContainerRef = useRef(null);
  const fileInputRef = useRef(null);

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
    setIsSaving(true);
    
    const formData = new FormData();
    formData.append('email', user.email);
    if (selectedFile) formData.append('profile_image', selectedFile);

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
            <h1 style={{ fontSize: '28px', color: '#222', marginBottom: '4px', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '8px' }}>
              {profileData.name} <ShieldCheck size={28} color="var(--terracotta-primary)" />
            </h1>
            <p style={{ color: 'var(--terracotta-primary)', fontWeight: 600, fontSize: '14px', textTransform: 'uppercase', letterSpacing: '1px' }}>
              System Administrator
            </p>
          </div>
        </div>
      </header>

      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
        <h2 style={{ fontSize: '20px', borderBottom: '1px solid #eee', paddingBottom: '16px', marginBottom: '24px' }}>Admin Details</h2>
        
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
