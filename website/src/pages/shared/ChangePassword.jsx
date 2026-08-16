import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, KeyRound, CheckCircle, AlertCircle, Eye, EyeOff, Check, Circle } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import apiClient from '../../utils/apiClient';

const ChangePassword = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  
  const [step, setStep] = useState(1); // 1 = Verify Old, 2 = Enter New
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  
  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  // Regex Requirements extracted from backend
  const reqLower = /[a-z]/;
  const reqUpper = /[A-Z]/;
  const reqNumber = /\d/;
  const reqSpecial = /[@$!%*?&]/;
  const reqLength = /^[A-Za-z\d@$!%*?&]{6,8}$/;

  const isLower = reqLower.test(newPassword);
  const isUpper = reqUpper.test(newPassword);
  const isNumber = reqNumber.test(newPassword);
  const isSpecial = reqSpecial.test(newPassword);
  const isLength = newPassword.length >= 6 && newPassword.length <= 8;

  const allReqsMet = isLower && isUpper && isNumber && isSpecial && isLength;
  const passwordsMatch = newPassword && confirmPassword && newPassword === confirmPassword;

  const handleVerify = async (e) => {
    e.preventDefault();
    setError(null);
    if (!oldPassword) {
      setError('Please enter your current password.');
      return;
    }
    setLoading(true);
    try {
      const identifier = user.email || user.driverId || user.phone;
      const res = await apiClient.post('/api/auth/login', {
        email: identifier,
        password: oldPassword
      });
      if (res.data.message === "Login successful" || res.data.customToken) {
        setStep(2);
      } else {
        setError('Incorrect current password.');
      }
    } catch (err) {
      setError('Incorrect current password.');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);

    if (!allReqsMet || !passwordsMatch) return;

    setLoading(true);
    try {
      const identifier = user.email || user.driverId || user.phone;
      const res = await apiClient.post('/api/auth/update-password', {
        identifier,
        oldPassword,
        newPassword
      });

      if (res.data.success || res.status === 200) {
        setSuccess(true);
        setTimeout(() => navigate(-1), 2000);
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to update password.');
    } finally {
      setLoading(false);
    }
  };

  const inputContainerStyle = {
    position: 'relative',
    display: 'block',
    width: '100%'
  };

  const inputStyle = {
    width: '100%',
    padding: '14px 72px 14px 16px', // 72px right padding to ensure text never reaches the eye icon
    borderRadius: '12px',
    border: '1px solid rgba(212, 175, 55, 0.4)',
    background: '#fff',
    fontSize: '15px',
    color: '#7C3D12',
    fontWeight: 500,
    outline: 'none',
    boxSizing: 'border-box'
  };

  const labelStyle = {
    display: 'block',
    marginBottom: '8px',
    fontWeight: 700,
    fontSize: '14px',
    color: '#7C3D12'
  };

  const visibilityBtnStyle = {
    position: 'absolute',
    right: '24px',
    top: '50%',
    transform: 'translateY(-50%)',
    width: '20px',
    height: '20px',
    padding: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: '#E07A5F',
    zIndex: 10 // Ensure it's clickable
  };

  const RequirementRow = ({ met, label }) => (
    <motion.div 
      initial={false}
      animate={{ color: met ? '#15803d' : '#888' }}
      style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 600, transition: 'color 0.2s' }}
    >
      <div style={{ width: 16, display: 'flex', justifyContent: 'center' }}>
        {met ? <Check size={16} color="#15803d" strokeWidth={3} /> : <Circle size={12} color="#888" />}
      </div>
      {label}
    </motion.div>
  );

  return (
    <div style={{ background: '#FDF5E6', minHeight: '100vh', padding: '24px', fontFamily: 'system-ui, sans-serif' }}>
      <style>{`
        input:focus {
          border-color: #E07A5F !important;
          box-shadow: 0 0 0 3px rgba(224, 122, 95, 0.1) !important;
        }
      `}</style>
      
      <div style={{ maxWidth: '500px', margin: '0 auto' }}>
        {/* HEADER */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
          <button 
            onClick={() => navigate(-1)} 
            style={{ 
              background: '#fff', border: '1px solid rgba(212, 175, 55, 0.3)', cursor: 'pointer', 
              display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#7C3D12',
              width: 44, height: 44, borderRadius: '50%', boxShadow: '0 4px 12px rgba(124, 61, 18, 0.05)',
              transition: 'all 0.2s'
            }}
          >
            <ArrowLeft size={22} />
          </button>
          <div>
            <h1 style={{ margin: 0, fontSize: '24px', color: '#E07A5F', fontWeight: '900' }}>Change Password</h1>
            <p style={{ margin: '4px 0 0', color: '#7C3D12', opacity: 0.7, fontWeight: 500, fontSize: '14px' }}>Secure your GroWise account</p>
          </div>
        </div>

        {/* MAIN CARD */}
        <motion.div 
          style={{ 
            background: '#fff', borderRadius: '24px', padding: '32px', 
            border: '1px solid rgba(212, 175, 55, 0.3)', 
            boxShadow: '0 12px 32px rgba(124, 61, 18, 0.08)' 
          }}
        >
          <AnimatePresence mode="wait">
            {step === 1 ? (
              <motion.form 
                key="step1"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.3 }}
                onSubmit={handleVerify} 
                style={{ display: 'flex', flexDirection: 'column', gap: '24px', overflow: 'hidden' }}
              >
                <div>
                  <label style={labelStyle}>Current Password</label>
                  <div style={inputContainerStyle}>
                    <input 
                      type={showOld ? 'text' : 'password'} 
                      value={oldPassword}
                      onChange={(e) => setOldPassword(e.target.value)}
                      placeholder="Enter current password"
                      style={inputStyle}
                    />
                    <button type="button" onClick={() => setShowOld(!showOld)} style={visibilityBtnStyle}>
                      {showOld ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>

                <button 
                  type="submit"
                  disabled={loading}
                  style={{ 
                    padding: '14px', background: '#E07A5F', color: '#fff', 
                    border: 'none', borderRadius: '12px', fontWeight: 800, fontSize: '15px', 
                    cursor: loading ? 'not-allowed' : 'pointer', boxShadow: '0 8px 24px rgba(224, 122, 95, 0.3)',
                    transition: 'all 0.2s', opacity: loading ? 0.7 : 1
                  }}
                >
                  {loading ? 'Verifying...' : 'Verify Password'}
                </button>
              </motion.form>
            ) : (
              <motion.form 
                key="step2"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                transition={{ duration: 0.3 }}
                onSubmit={handleUpdate} 
                style={{ display: 'flex', flexDirection: 'column', gap: '24px', overflow: 'hidden' }}
              >
                <div>
                  <label style={labelStyle}>New Password</label>
                  <div style={inputContainerStyle}>
                    <input 
                      type={showNew ? 'text' : 'password'} 
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Enter new password"
                      style={inputStyle}
                    />
                    <button type="button" onClick={() => setShowNew(!showNew)} style={visibilityBtnStyle}>
                      {showNew ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>

                {/* Live Requirements */}
                <div style={{ background: '#FDF5E6', padding: '16px', borderRadius: '12px', border: '1px solid rgba(212, 175, 55, 0.2)' }}>
                  <h4 style={{ margin: '0 0 12px', fontSize: 13, color: '#7C3D12', textTransform: 'uppercase', letterSpacing: 0.5, fontWeight: 800 }}>Password Requirements</h4>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                    <RequirementRow met={isLower} label="Lowercase" />
                    <RequirementRow met={isUpper} label="Uppercase" />
                    <RequirementRow met={isNumber} label="Number" />
                    <RequirementRow met={isSpecial} label="Special Character" />
                    <RequirementRow met={isLength} label="6-8 Characters" />
                  </div>
                </div>

                <div>
                  <label style={labelStyle}>Confirm Password</label>
                  <div style={inputContainerStyle}>
                    <input 
                      type={showConfirm ? 'text' : 'password'} 
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Retype new password"
                      style={{
                        ...inputStyle,
                        borderColor: confirmPassword ? (passwordsMatch ? '#15803d' : '#b91c1c') : 'rgba(212, 175, 55, 0.4)'
                      }}
                    />
                    <button type="button" onClick={() => setShowConfirm(!showConfirm)} style={visibilityBtnStyle}>
                      {showConfirm ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                  {/* Match Indicator */}
                  <AnimatePresence>
                    {confirmPassword && (
                      <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        exit={{ opacity: 0, height: 0 }}
                        style={{
                          marginTop: '8px', fontSize: '13px', fontWeight: 700,
                          color: passwordsMatch ? '#15803d' : '#b91c1c'
                        }}
                      >
                        {passwordsMatch ? 'Passwords Match' : 'Passwords Do Not Match'}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>

                <button 
                  type="submit"
                  disabled={loading || !allReqsMet || !passwordsMatch}
                  style={{ 
                    marginTop: '8px', padding: '14px', background: '#E07A5F', 
                    color: '#fff', border: 'none', borderRadius: '12px', 
                    fontWeight: 800, fontSize: '15px', 
                    cursor: (loading || !allReqsMet || !passwordsMatch) ? 'not-allowed' : 'pointer',
                    boxShadow: '0 8px 24px rgba(224, 122, 95, 0.3)',
                    transition: 'all 0.2s', 
                    opacity: (loading || !allReqsMet || !passwordsMatch) ? 0.5 : 1
                  }}
                >
                  {loading ? 'Updating...' : 'Update Password'}
                </button>
              </motion.form>
            )}
          </AnimatePresence>
        </motion.div>
      </div>

      {/* SNACKBARS */}
      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, y: 50, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 50, scale: 0.9 }}
            style={{ 
              position: 'fixed', bottom: 32, left: '50%', transform: 'translateX(-50%)',
              background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c',
              padding: '16px 24px', borderRadius: '12px', display: 'flex', alignItems: 'center', gap: 12,
              boxShadow: '0 12px 24px rgba(185, 28, 28, 0.1)', zIndex: 9999, fontWeight: 600
            }}
          >
            <AlertCircle size={20} /> {error}
          </motion.div>
        )}

        {success && (
          <motion.div
            initial={{ opacity: 0, y: 50, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 50, scale: 0.9 }}
            style={{ 
              position: 'fixed', bottom: 32, left: '50%', transform: 'translateX(-50%)',
              background: '#f0fdf4', border: '1px solid #bbf7d0', color: '#15803d',
              padding: '16px 24px', borderRadius: '12px', display: 'flex', alignItems: 'center', gap: 12,
              boxShadow: '0 12px 24px rgba(21, 128, 61, 0.1)', zIndex: 9999, fontWeight: 600
            }}
          >
            <CheckCircle size={20} /> Password updated successfully!
          </motion.div>
        )}
      </AnimatePresence>

    </div>
  );
};

export default ChangePassword;
