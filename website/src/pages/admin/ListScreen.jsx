import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import { ArrowLeft, Search, User, ShieldCheck, Home } from 'lucide-react';

const AdminListScreen = () => {
  const { type } = useParams();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  
  const containerRef = useRef(null);

  useEffect(() => {
    fetchList();
  }, [type]);

  useEffect(() => {
    if (!loading && containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, y: 15 }, 
        { opacity: 1, y: 0, duration: 0.4, stagger: 0.05, ease: 'power2.out' }
      );
    }
  }, [loading]);

  const fetchList = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get(`/api/admin/list?type=${type}`);
      if (data.success) {
        setItems(data.items);
      }
    } catch (error) {
      console.error("Failed to fetch list", error);
    } finally {
      setLoading(false);
    }
  };

  const getTitle = () => {
    switch(type) {
      case 'users': return 'Registered Users & NGOs';
      case 'farmers': return 'Active Farmers';
      case 'drivers': return 'Verified Drivers';
      case 'deals': return 'Active Deals & Orders';
      default: return 'Directory';
    }
  };

  const filteredItems = items.filter(item => 
    item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.email.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/home/admin')} style={{ background: '#fff', border: '1px solid #eee', borderRadius: '50%', width: '44px', height: '44px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
          <ArrowLeft size={20} color="#444" />
        </button>
        <div>
          <h1 style={{ fontSize: '28px', fontWeight: 800, color: '#111', margin: '0 0 4px 0' }}>{getTitle()}</h1>
          <p style={{ color: '#666', fontSize: '14px', margin: 0 }}>Managing {items.length} records in the system</p>
        </div>
      </header>

      <div style={{ position: 'relative', marginBottom: '24px' }}>
        <Search size={20} color="#888" style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)' }} />
        <input 
          type="text" 
          placeholder="Search by name, ID, or email..." 
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{
            width: '100%',
            padding: '16px 16px 16px 48px',
            borderRadius: '16px',
            border: '1px solid #e0e0e0',
            fontSize: '15px',
            backgroundColor: '#fff',
            outline: 'none',
            boxShadow: '0 4px 12px rgba(0,0,0,0.02)'
          }}
        />
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--terracotta-primary)', fontWeight: 600 }}>Loading Data...</div>
      ) : (
        <div ref={containerRef} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {filteredItems.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#888', backgroundColor: '#fff', borderRadius: '16px', border: '1px dashed #ccc' }}>
              No records found.
            </div>
          ) : (
            filteredItems.map((item, idx) => (
              <div key={idx} style={{ backgroundColor: '#fff', padding: '20px 24px', borderRadius: '16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: 'var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <User size={20} color="var(--terracotta-primary)" />
                  </div>
                  <div>
                    <h3 style={{ margin: '0 0 4px 0', fontSize: '16px', fontWeight: 700, color: '#222' }}>{item.name}</h3>
                    <p style={{ margin: 0, fontSize: '13px', color: '#666', display: 'flex', gap: '12px' }}>
                      <span>{item.email}</span>
                      <span style={{ color: '#ccc' }}>|</span>
                      <span>{item.phone}</span>
                    </p>
                  </div>
                </div>
                
                <div style={{ textAlign: 'right' }}>
                  {type !== 'users' && type !== 'farmers' && (
                    <span style={{ display: 'inline-block', padding: '4px 10px', backgroundColor: '#f1f5f9', color: '#475569', borderRadius: '12px', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                      {item.id}
                    </span>
                  )}
                  <p style={{ margin: 0, fontSize: '12px', fontWeight: 600, color: item.status === 'Active' || item.status === 'Verified' ? '#10b981' : '#f59e0b', display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '4px' }}>
                    <ShieldCheck size={14} /> {item.status}
                  </p>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default AdminListScreen;
