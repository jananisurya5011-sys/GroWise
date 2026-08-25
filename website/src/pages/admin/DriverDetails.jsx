import React, { useEffect, useState, useRef } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import { ArrowLeft, User, Phone, CreditCard, Truck, CheckCircle2, XCircle, FileText, Loader2 } from 'lucide-react';
import notify from '../../services/NotificationService';


const AdminDriverDetails = () => {
  const { email } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const driverData = location.state?.driverData;
  
  const [isProcessing, setIsProcessing] = useState(false);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectionReason, setRejectionReason] = useState('');
  
  // Success state for approval
  const [approvalResult, setApprovalResult] = useState(null);
  
  const containerRef = useRef(null);
  const modalRef = useRef(null);
  const successModalRef = useRef(null);

  useEffect(() => {
    if (!driverData) {
      // If accessed directly without state, bounce back to list
      navigate('/admin/verify');
      return;
    }
    
    if (containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, y: 15 }, 
        { opacity: 1, y: 0, duration: 0.4, stagger: 0.1, ease: 'power2.out' }
      );
    }
  }, [driverData, navigate]);

  useEffect(() => {
    if (showRejectModal && modalRef.current) {
      gsap.fromTo(modalRef.current, 
        { opacity: 0, scale: 0.95 }, 
        { opacity: 1, scale: 1, duration: 0.3, ease: 'power2.out' }
      );
    }
  }, [showRejectModal]);

  useEffect(() => {
    if (approvalResult && successModalRef.current) {
      gsap.fromTo(successModalRef.current, 
        { opacity: 0, scale: 0.9 }, 
        { opacity: 1, scale: 1, duration: 0.5, ease: 'back.out(1.5)' }
      );
    }
  }, [approvalResult]);

  const handleAction = async (action) => {
    if (action === 'REJECT' && !rejectionReason.trim()) {
      notify.warning("Please provide a reason for rejection.");
      return;
    }
    
    setIsProcessing(true);
    try {
      const payload = {
        email: driverData.email,
        action: action
      };
      if (action === 'REJECT') {
        payload.reason = rejectionReason;
      }

      const { data } = await apiClient.post('/api/admin/verify-driver', payload);
      if (data.success) {
        if (action === 'APPROVE') {
          setApprovalResult(data.message); // e.g., "Approved. ID: GW-D1234"
        } else {
          notify.info(data.message);
          navigate('/admin/verify');
        }
      }
    } catch (error) {
      console.error(`Verification ${action} failed`, error);
      notify.error("An error occurred during verification.");
      setIsProcessing(false);
      setShowRejectModal(false);
    } 
  };

  if (!driverData) return null;

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', position: 'relative' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <button onClick={() => navigate('/admin/verify')} style={{ background: '#fff', border: '1px solid #eee', borderRadius: '50%', width: '44px', height: '44px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
          <ArrowLeft size={20} color="#444" />
        </button>
        <div>
          <h1 style={{ fontSize: '28px', fontWeight: 800, color: '#111', margin: '0 0 4px 0' }}>Review Application</h1>
          <p style={{ color: '#666', fontSize: '14px', margin: 0 }}>Driver: {driverData.email}</p>
        </div>
      </header>

      <div ref={containerRef}>
        {/* Personal Details Card */}
        <section style={{ backgroundColor: '#fff', padding: '32px', borderRadius: '24px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)', marginBottom: '24px', display: 'flex', gap: '32px', alignItems: 'center' }}>
          <div style={{ width: '100px', height: '100px', borderRadius: '50%', backgroundColor: '#f8fafc', border: '2px solid var(--peach-background)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', flexShrink: 0 }}>
            {driverData.profileImageUrl ? (
              <img src={driverData.profileImageUrl} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              <User size={40} color="#94a3b8" />
            )}
          </div>
          
          <div style={{ flex: 1 }}>
            <h2 style={{ fontSize: '24px', fontWeight: 800, color: '#222', margin: '0 0 12px 0' }}>{driverData.name || 'Not Provided'}</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
              <p style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', color: '#555', fontWeight: 500 }}>
                <Phone size={16} color="var(--terracotta-primary)" /> {driverData.phone || 'N/A'}
              </p>
              <p style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', color: '#555', fontWeight: 500 }}>
                <Truck size={16} color="var(--terracotta-primary)" /> {driverData.vehicleType || 'N/A'}
              </p>
              <p style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', color: '#555', fontWeight: 500 }}>
                <CreditCard size={16} color="var(--terracotta-primary)" /> {driverData.aadhaarNumber || 'Govt ID Missing'}
              </p>
            </div>
          </div>
        </section>

        {/* Document Vault */}
        <section style={{ backgroundColor: '#fff', padding: '32px', borderRadius: '24px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)', marginBottom: '32px' }}>
          <h3 style={{ fontSize: '18px', fontWeight: 700, color: '#222', margin: '0 0 24px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <FileText size={20} color="#3b82f6" /> Regulatory Documents
          </h3>
          
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
            {/* RC Book */}
            <div style={{ border: '1px solid #e2e8f0', borderRadius: '16px', padding: '16px', backgroundColor: '#f8fafc' }}>
              <p style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 600, color: '#475569' }}>Vehicle RC Book</p>
              <div style={{ width: '100%', height: '220px', backgroundColor: '#e2e8f0', borderRadius: '12px', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {driverData.rcBookUrl ? (
                  <img src={driverData.rcBookUrl.startsWith('/') ? driverData.rcBookUrl : `/uploads/drivers/${driverData.rcBookUrl}`} alt="RC Book" style={{ width: '100%', height: '100%', objectFit: 'contain', backgroundColor: '#000' }} />
                ) : (
                  <span style={{ color: '#94a3b8', fontSize: '13px' }}>Missing Document</span>
                )}
              </div>
            </div>

            {/* License */}
            <div style={{ border: '1px solid #e2e8f0', borderRadius: '16px', padding: '16px', backgroundColor: '#f8fafc' }}>
              <p style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 600, color: '#475569' }}>Driving License</p>
              <div style={{ width: '100%', height: '220px', backgroundColor: '#e2e8f0', borderRadius: '12px', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {driverData.licenseUrl ? (
                  <img src={driverData.licenseUrl.startsWith('/') ? driverData.licenseUrl : `/uploads/drivers/${driverData.licenseUrl}`} alt="License" style={{ width: '100%', height: '100%', objectFit: 'contain', backgroundColor: '#000' }} />
                ) : (
                  <span style={{ color: '#94a3b8', fontSize: '13px' }}>Missing Document</span>
                )}
              </div>
            </div>
          </div>
        </section>

        {/* Action Buttons */}
        <div style={{ display: 'flex', gap: '16px' }}>
          <button 
            onClick={() => setShowRejectModal(true)} 
            disabled={isProcessing}
            style={{ flex: 1, padding: '16px', backgroundColor: '#fff', color: '#ef4444', border: '2px solid #fee2e2', borderRadius: '16px', fontSize: '16px', fontWeight: 700, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', transition: 'all 0.2s' }}
          >
            <XCircle size={20} /> Reject Application
          </button>
          
          <button 
            onClick={() => handleAction('APPROVE')}
            disabled={isProcessing}
            style={{ flex: 2, padding: '16px', backgroundColor: '#10b981', color: '#fff', border: 'none', borderRadius: '16px', fontSize: '16px', fontWeight: 700, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', transition: 'all 0.2s', boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)' }}
          >
            {isProcessing ? <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> : <><CheckCircle2 size={20} /> Approve & Issue ID</>}
          </button>
        </div>
      </div>

      {/* --- CUSTOM REJECTION MODAL --- */}
      {showRejectModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.6)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(4px)' }}>
          <div ref={modalRef} style={{ backgroundColor: '#fff', width: '90%', maxWidth: '500px', borderRadius: '24px', padding: '32px', boxShadow: '0 20px 40px rgba(0,0,0,0.2)' }}>
            <h2 style={{ margin: '0 0 16px 0', fontSize: '24px', fontWeight: 800, color: '#222' }}>Rejection Reason</h2>
            <p style={{ margin: '0 0 24px 0', color: '#666', fontSize: '14px', lineHeight: 1.5 }}>
              Please provide a specific reason for rejecting this driver's application. This will be recorded in the system.
            </p>
            
            <textarea 
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              placeholder="e.g., Blurry RC book, License expired, Failed background check..."
              style={{
                width: '100%',
                height: '120px',
                padding: '16px',
                borderRadius: '12px',
                border: '1px solid #ddd',
                backgroundColor: '#fafafa',
                fontSize: '14px',
                outline: 'none',
                resize: 'none',
                fontFamily: 'inherit',
                marginBottom: '24px'
              }}
            />

            <div style={{ display: 'flex', gap: '12px' }}>
              <button 
                onClick={() => setShowRejectModal(false)}
                disabled={isProcessing}
                style={{ flex: 1, padding: '14px', backgroundColor: '#f1f5f9', color: '#475569', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 600, cursor: 'pointer' }}
              >
                Cancel
              </button>
              <button 
                onClick={() => handleAction('REJECT')}
                disabled={isProcessing}
                style={{ flex: 1, padding: '14px', backgroundColor: '#ef4444', color: '#fff', border: 'none', borderRadius: '12px', fontSize: '15px', fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
              >
                {isProcessing ? <Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} /> : 'Confirm Rejection'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* --- APPROVAL SUCCESS MODAL --- */}
      {approvalResult && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.6)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(4px)' }}>
          <div ref={successModalRef} style={{ backgroundColor: '#fff', width: '90%', maxWidth: '400px', borderRadius: '24px', padding: '32px', boxShadow: '0 20px 40px rgba(0,0,0,0.2)', textAlign: 'center' }}>
            <div style={{ width: '64px', height: '64px', borderRadius: '50%', backgroundColor: '#ecfdf5', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px auto' }}>
              <CheckCircle2 size={32} color="#10b981" />
            </div>
            <h2 style={{ margin: '0 0 12px 0', fontSize: '24px', fontWeight: 800, color: '#222' }}>Driver Verified!</h2>
            <p style={{ margin: '0 0 24px 0', color: '#475569', fontSize: '15px', lineHeight: 1.5 }}>
              The application was approved successfully. The system has generated the following unique Driver ID:
            </p>
            
            <div style={{ backgroundColor: '#f1f5f9', padding: '16px', borderRadius: '12px', fontSize: '18px', fontWeight: 800, color: 'var(--terracotta-primary)', letterSpacing: '1px', marginBottom: '24px', border: '1px dashed #cbd5e1' }}>
              {approvalResult.replace('Approved. ID: ', '')}
            </div>

            <button 
              onClick={() => navigate('/admin/verify')}
              style={{ width: '100%', padding: '16px', backgroundColor: '#10b981', color: '#fff', border: 'none', borderRadius: '16px', fontSize: '16px', fontWeight: 700, cursor: 'pointer', transition: 'all 0.2s', boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)' }}
            >
              Back to Queue
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDriverDetails;
