import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { ArrowLeft, UploadCloud, Package, Loader2, Heart, Clock, CheckCircle } from 'lucide-react';
import gsap from 'gsap';
import { formatCurrency } from '../../utils/constants';

const getImageUrl = (dbImageString, type = 'inventory') => {
  if (!dbImageString) return '';
  
  if (dbImageString.startsWith('/api/') && !dbImageString.includes('/routes/')) return dbImageString;
  
  const parts = dbImageString.split('/');
  const cleanFileName = parts[parts.length - 1];
  
  if (type === 'inventory') {
    return `/api/inventory/uploads/product_photos/${cleanFileName}`;
  } else {
    return `/api/rental/uploads/rental_photos/${cleanFileName}`;
  }
};

const FarmerListProduct = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [activeTab, setActiveTab] = useState('add'); 
  const [inventory, setInventory] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  
  const [formData, setFormData] = useState({
    cropName: '',
    category: 'Vegetables',
    pricePerKg: '',
    availableKg: '',
    moq: '',
    harvestDate: new Date().toISOString().split('T')[0],
    expiryDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
  });
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const fileInputRef = useRef(null);
  const containerRef = useRef(null);

  useEffect(() => {
    if (activeTab === 'manage') fetchInventory();

    if (containerRef.current) {
      gsap.fromTo(containerRef.current, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }
      );
    }
  }, [activeTab]);

  const fetchInventory = async () => {
    if (!user?.email) return;
    setIsLoading(true);
    try {
      const response = await fetch('/api/inventory/fetch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: user.email })
      });
      const data = await response.json();
      setInventory(data || []);
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.cropName || !formData.pricePerKg || !formData.availableKg) {
      alert("Please fill in required fields.");
      return;
    }

    setIsSubmitting(true);
    const payload = new FormData();
    Object.keys(formData).forEach(key => payload.append(key, formData[key]));
    payload.append('email', user?.email || '');
    if (selectedFile) payload.append('image', selectedFile);

    try {
      const token = localStorage.getItem('token');
      const response = await fetch('/api/inventory/add', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: payload
      });
      const data = await response.json();
      
      if (data.success) {
        alert("Product listed successfully!");
        setFormData({ ...formData, cropName: '', pricePerKg: '', availableKg: '', moq: '' });
        setSelectedFile(null);
        setPreviewUrl(null);
        setActiveTab('manage');
      } else {
        alert(data.message || "Failed to list product.");
      }
    } catch (error) {
      console.error("Submission Error:", error);
      alert("Network error.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const donateToNGO = async (itemId) => {
    if (!window.confirm("Donate this batch to NGOs? It will be removed from your sellable inventory.")) return;
    try {
      const response = await fetch('/api/ngo/donate-item', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemId })
      });
      const data = await response.json();
      if (data.success) {
        alert("Successfully marked for NGO Donation.");
        fetchInventory();
      }
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div ref={containerRef} style={{ maxWidth: '800px', margin: '0 auto', paddingBottom: '40px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/home/farmer')} style={{ background: '#fff', border: '1px solid #eee', width: '40px', height: '40px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <ArrowLeft size={20} color="#333" />
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 800, color: '#222' }}>Inventory Manager</h1>
          <p style={{ margin: 0, fontSize: '14px', color: '#666' }}>Sell produce or route to NGOs</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', marginBottom: '32px', backgroundColor: '#fff', padding: '8px', borderRadius: '16px', border: '1px solid #eee' }}>
        <button onClick={() => setActiveTab('add')} style={{ flex: 1, padding: '14px', backgroundColor: activeTab === 'add' ? 'var(--peach-background)' : 'transparent', color: activeTab === 'add' ? 'var(--terracotta-primary)' : '#666', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 800, cursor: 'pointer', transition: 'all 0.2s' }}>+ Post Product</button>
        <button onClick={() => setActiveTab('manage')} style={{ flex: 1, padding: '14px', backgroundColor: activeTab === 'manage' ? 'var(--peach-background)' : 'transparent', color: activeTab === 'manage' ? 'var(--terracotta-primary)' : '#666', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 800, cursor: 'pointer', transition: 'all 0.2s' }}>Manage Inventory</button>
      </div>

      {activeTab === 'manage' && (
        <div>
          {isLoading ? <div style={{ textAlign: 'center', padding: '40px' }}><Loader2 size={32} style={{ animation: 'spin 1s linear infinite', color: 'var(--terracotta-primary)' }} /></div> : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {inventory.map((item, idx) => {
                  const status = item.expiryStatus || 'Fresh Batch';
                  const isNearExpiry = status === 'Near Expiry';
                  const isClearance = status === 'Clearance Sale';
                  const isNGOFeed = status === 'ACTIVE NGO FEED';
                  const cleanImgUrl = getImageUrl(item.imageUrl, 'inventory');
                  const availableQty = parseFloat(item.availableKg || 0);
                  
                  return (
                    <div key={idx} style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '20px', border: '1px solid #eee', boxShadow: '0 4px 12px rgba(0,0,0,0.03)', display: 'flex', gap: '20px', alignItems: 'center', position: 'relative' }}>
                      <div style={{ width: '100px', height: '100px', borderRadius: '16px', backgroundColor: '#f5f5f5', overflow: 'hidden', flexShrink: 0, position: 'relative' }}>
                        {cleanImgUrl ? <img src={cleanImgUrl} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="Crop" /> : <Package size={40} color="#ccc" style={{ margin: '30px' }} />}
                        
                        {/* Discount Badges */}
                        {isNearExpiry && (
                          <div style={{ position: 'absolute', top: 0, right: 0, backgroundColor: '#f59e0b', color: '#fff', fontSize: '10px', fontWeight: 900, padding: '4px 8px', borderBottomLeftRadius: '12px' }}>30% OFF</div>
                        )}
                        {isClearance && (
                          <div style={{ position: 'absolute', top: 0, right: 0, backgroundColor: '#d32f2f', color: '#fff', fontSize: '10px', fontWeight: 900, padding: '4px 8px', borderBottomLeftRadius: '12px' }}>50% OFF</div>
                        )}
                      </div>
                      
                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                          <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#222' }}>{item.cropName}</h3>
                          {!isNGOFeed && (
                            <div style={{ backgroundColor: 'var(--peach-background)', color: 'var(--terracotta-primary)', padding: '4px 10px', borderRadius: '8px', fontSize: '13px', fontWeight: 800 }}>{formatCurrency(item.pricePerKg)}/kg</div>
                          )}
                        </div>
                        
                        <div style={{ display: 'flex', gap: '16px', color: '#666', fontSize: '13px', marginBottom: '16px' }}>
                          <span><strong>{availableQty}</strong> kg available</span>
                          {!isNGOFeed && <span>MOQ: <strong>{item.moq}</strong> kg</span>}
                        </div>
  
                        {isNGOFeed ? (
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#f3e8ff', padding: '12px', borderRadius: '12px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#7c3aed', fontSize: '13px', fontWeight: 700 }}>
                              <Heart size={16} /> ACTIVE NGO FEED
                            </div>
                          </div>
                        ) : (isNearExpiry || isClearance) ? (
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#fef2f2', padding: '12px', borderRadius: '12px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: isClearance ? '#d32f2f' : '#f59e0b', fontSize: '13px', fontWeight: 700 }}>
                              <Clock size={16} /> {isClearance ? 'Clearance Sale' : 'Near Expiry'}
                            </div>
                            <button 
                              onClick={() => donateToNGO(item.id)}
                              style={{ padding: '8px 16px', backgroundColor: '#dc2626', color: '#fff', border: 'none', borderRadius: '8px', fontSize: '12px', fontWeight: 800, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                              <Heart size={14} /> Donate to NGO
                            </button>
                          </div>
                        ) : (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#10b981', fontSize: '13px', fontWeight: 700 }}>
                            <CheckCircle size={16} /> Fresh Batch
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              {inventory.length === 0 && <div style={{ textAlign: 'center', padding: '40px', color: '#888' }}>No active inventory found.</div>}
            </div>
          )}
        </div>
      )}

      {activeTab === 'add' && (
        <form onSubmit={handleSubmit} style={{ backgroundColor: '#fff', borderRadius: '32px', padding: '40px', border: '1px solid #eee', boxShadow: '0 12px 40px rgba(0,0,0,0.04)' }}>
          <div 
            onClick={() => fileInputRef.current?.click()}
            style={{ width: '100%', height: '240px', backgroundColor: '#f8fafc', borderRadius: '24px', border: '2px dashed #cbd5e1', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', overflow: 'hidden', marginBottom: '32px', transition: 'border 0.2s' }}
            onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--terracotta-primary)'}
            onMouseLeave={e => e.currentTarget.style.borderColor = '#cbd5e1'}
          >
            {previewUrl ? (
              <img src={previewUrl} alt="Preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              <>
                <UploadCloud size={48} color="#94a3b8" style={{ marginBottom: '16px' }} />
                <span style={{ fontSize: '15px', color: '#64748b', fontWeight: 700 }}>Tap to upload product photo</span>
              </>
            )}
          </div>
          <input type="file" accept="image/*" ref={fileInputRef} onChange={handleFileChange} style={{ display: 'none' }} />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Crop Name *</label>
              <input type="text" name="cropName" value={formData.cropName} onChange={handleInputChange} required style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="e.g. Tomato" />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Category</label>
              <select name="category" value={formData.category} onChange={handleInputChange} style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }}>
                <option value="Vegetables">Vegetables</option>
                <option value="Fruits">Fruits</option>
                <option value="Grains">Grains</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '24px', marginBottom: '32px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Price/Kg (₹) *</label>
              <input type="number" step="0.01" name="pricePerKg" value={formData.pricePerKg} onChange={handleInputChange} required style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="0.00" />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Available (Kg) *</label>
              <input type="number" step="0.1" name="availableKg" value={formData.availableKg} onChange={handleInputChange} required style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="100" />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>MOQ (Kg)</label>
              <input type="number" step="0.1" name="moq" value={formData.moq} onChange={handleInputChange} style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} placeholder="Min. Order" />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '40px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Harvest Date</label>
              <input type="date" name="harvestDate" value={formData.harvestDate} onChange={handleInputChange} style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '14px', fontWeight: 700, color: '#334155', marginBottom: '12px' }}>Expiry Date</label>
              <input type="date" name="expiryDate" value={formData.expiryDate} onChange={handleInputChange} style={{ width: '100%', padding: '16px', borderRadius: '16px', border: '1px solid #e2e8f0', fontSize: '16px', outline: 'none', backgroundColor: '#f8fafc' }} />
            </div>
          </div>

          <button 
            type="submit"
            disabled={isSubmitting}
            style={{ width: '100%', padding: '18px', backgroundColor: 'var(--terracotta-primary)', color: '#fff', border: 'none', borderRadius: '16px', fontSize: '18px', fontWeight: 800, cursor: isSubmitting ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px', boxShadow: '0 8px 24px rgba(210, 84, 60, 0.2)' }}
          >
            {isSubmitting ? <Loader2 size={24} style={{ animation: 'spin 1s linear infinite' }} /> : <Package size={24} />}
            {isSubmitting ? 'Listing Product...' : 'Publish to Market'}
          </button>
        </form>
      )}
    </div>
  );
};

export default FarmerListProduct;
