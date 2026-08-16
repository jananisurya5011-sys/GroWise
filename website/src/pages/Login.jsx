import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import gsap from 'gsap';
import Logo from '../components/Logo';
import apiClient from '../utils/apiClient';
import { saveSession } from '../utils/session';
import { useAuth } from '../contexts/AuthContext';
import { Hourglass, XCircle, CheckCircle2, Eye, EyeOff } from 'lucide-react';
import { signInWithCustomToken } from 'firebase/auth';
import { auth } from '../utils/firebase';

const Login = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Status Screens (Driver Logic)
  const [showPendingScreen, setShowPendingScreen] = useState(false);
  const [showRejectedScreen, setShowRejectedScreen] = useState(false);
  const [rejectionReason, setRejectionReason] = useState('');
  const [showApprovedScreen, setShowApprovedScreen] = useState(false);
  const [approvedDriverId, setApprovedDriverId] = useState('');

  // Form Validation
  const [isValidId, setIsValidId] = useState(null);

  const emailRegex = /^[a-zA-Z0-9._%+-]+@(gmail\.com|mail\.com)$/;
  const phoneRegex = /^\d{10}$/;
  const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{6,8}$/;

  // GSAP Refs
  const leftPanelRef = useRef(null);
  const rightPanelRef = useRef(null);
  const buttonRef = useRef(null);
  const formRef = useRef(null);
  const wrapperRef = useRef(null);

  useEffect(() => {
    const tl = gsap.timeline();
    tl.fromTo(leftPanelRef.current, { x: '-100%', opacity: 0 }, { x: '0%', opacity: 1, duration: 1, ease: 'power3.out' })
      .fromTo(rightPanelRef.current, { x: '10%', opacity: 0 }, { x: '0%', opacity: 1, duration: 0.8, ease: 'power3.out' }, "-=0.6")
      .fromTo(formRef.current?.children || [], { y: 20, opacity: 0 }, { y: 0, opacity: 1, duration: 0.6, stagger: 0.1, ease: 'power2.out' }, "-=0.4");
  }, []);

  useEffect(() => {
    if (showPendingScreen || showRejectedScreen || showApprovedScreen) {
      gsap.fromTo(wrapperRef.current, { opacity: 0, scale: 0.95 }, { opacity: 1, scale: 1, duration: 0.4, ease: 'back.out(1.7)' });
    }
  }, [showPendingScreen, showRejectedScreen, showApprovedScreen]);

  const handleButtonHover = () => {
    if (!isLoading) gsap.to(buttonRef.current, { scale: 1.02, duration: 0.2, ease: "power1.out" });
  };

  const handleButtonLeave = () => {
    if (!isLoading) gsap.to(buttonRef.current, { scale: 1, duration: 0.2, ease: "power1.out" });
  };

  useEffect(() => {
    if (identifier) {
      const isEmailFormat = identifier.includes('@');
      const isDriverIdFormat = identifier.startsWith('GW-D');
      if (isEmailFormat) setIsValidId(emailRegex.test(identifier));
      else if (!isDriverIdFormat) setIsValidId(phoneRegex.test(identifier));
      else setIsValidId(true);
    } else setIsValidId(null);
  }, [identifier]);

  const handleLogin = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    const isEmailFormat = identifier.includes('@');
    const isDriverIdFormat = identifier.startsWith('GW-D');

    if (identifier === "growise@gmail.com" && password === "Grow@123") {
      // Valid Admin Format
    } else if (isEmailFormat && !emailRegex.test(identifier)) {
      return setErrorMessage("Invalid Domain: Must use @gmail.com or @mail.com");
    } else if (!isEmailFormat && !isDriverIdFormat && !phoneRegex.test(identifier)) {
      return setErrorMessage("Invalid Phone: Must be 10 digits");
    } else if (password.length < 6 || password.length > 8) {
      return setErrorMessage("Length Error: Password must be 6 to 8 characters.");
    } else if (!passwordRegex.test(password)) {
      return setErrorMessage("Complexity Error: Requires 1 uppercase, 1 lowercase, 1 digit, 1 special.");
    }

    setIsLoading(true);
    gsap.to(formRef.current, { opacity: 0.5, duration: 0.3 });
    
    try {
      const { data } = await apiClient.post('/api/auth/login', { email: identifier, password });
      
      const verificationStatus = data.verificationStatus;
      const userRole = data.role || "user";
      const returnedEmail = data.email || identifier;

      if (userRole === "driver" && verificationStatus === "PENDING") {
        setShowPendingScreen(true);
      } else if (userRole === "driver" && verificationStatus === "REJECTED") {
        setRejectionReason(data.rejectionReason || "Failed background verification.");
        setShowRejectedScreen(true);
      } else if (userRole === "driver" && verificationStatus === "APPROVED_NEEDS_ID" && !isDriverIdFormat) {
        setApprovedDriverId(data.driverId || "ID_FETCH_ERROR");
        setShowApprovedScreen(true);
        // Pre-cache driver ID safely if needed
      } else {
        // Retrieve custom token from backend response
        const customToken = data.customToken;
        if (!customToken) {
          throw new Error("Security Error: Missing Firebase Auth Token from Server.");
        }

        // Authenticate with Firebase BEFORE completing local session
        await signInWithCustomToken(auth, customToken);

        // Proceed with full authentication
        saveSession(userRole, returnedEmail, isDriverIdFormat ? identifier : null);
        
        // Let context know
        const sessionPayload = { role: userRole, email: returnedEmail, timestamp: Date.now() };
        login(sessionPayload);
        
        gsap.to(rightPanelRef.current, { x: '10%', opacity: 0, duration: 0.5, ease: 'power2.in' });
        gsap.to(leftPanelRef.current, { x: '-10%', opacity: 0, duration: 0.5, ease: 'power2.in', onComplete: () => {
          if (userRole === 'admin') navigate('/home/admin');
          else if (userRole === 'driver') navigate('/home/driver'); // Route directly now, status handled above
          else if (userRole === 'farmer') navigate('/home/farmer');
          else navigate('/home/user');
        }});
      }
      
    } catch (err) {
      setErrorMessage(err.message || 'System Error');
      gsap.to(formRef.current, { opacity: 1, duration: 0.3 });
      gsap.fromTo(formRef.current, { x: -10 }, { x: 10, duration: 0.1, yoyo: true, repeat: 3, onComplete: () => gsap.set(formRef.current, {x: 0}) });
    } finally {
      setIsLoading(false);
    }
  };

  const Spinner = () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ animation: 'spin 1s linear infinite' }}>
      <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
      <line x1="12" y1="2" x2="12" y2="6"></line>
      <line x1="12" y1="18" x2="12" y2="22"></line>
      <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"></line>
      <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"></line>
      <line x1="2" y1="12" x2="6" y2="12"></line>
      <line x1="18" y1="12" x2="22" y2="12"></line>
      <line x1="4.93" y1="19.07" x2="7.76" y2="16.24"></line>
      <line x1="16.24" y1="7.76" x2="19.07" y2="4.93"></line>
    </svg>
  );

  const handleReset = () => {
    setShowPendingScreen(false);
    setShowRejectedScreen(false);
    setShowApprovedScreen(false);
    setIdentifier('');
    setPassword('');
    gsap.to(formRef.current, { opacity: 1, duration: 0.3 });
  };

  if (showPendingScreen) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', justifyContent: 'center', alignItems: 'center', backgroundColor: 'var(--peach-background)', padding: '24px' }}>
        <div ref={wrapperRef} style={{ textAlign: 'center', maxWidth: '400px' }}>
          <Logo size={100} />
          <Hourglass size={64} color="var(--terracotta-primary)" style={{ margin: '24px 0' }} />
          <h2 style={{ fontSize: '24px', fontWeight: 800, color: 'var(--terracotta-primary)', marginBottom: '8px' }}>Verification Pending</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '32px' }}>Your driver account is currently under review by the Administrator. Please check back later.</p>
          <button onClick={handleReset} className="primary" style={{ width: '100%' }}>Back to Login</button>
        </div>
      </div>
    );
  }

  if (showRejectedScreen) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', justifyContent: 'center', alignItems: 'center', backgroundColor: 'var(--peach-background)', padding: '24px' }}>
        <div ref={wrapperRef} style={{ textAlign: 'center', maxWidth: '400px', width: '100%' }}>
          <div style={{ width: '100px', height: '100px', borderRadius: '50%', backgroundColor: '#FFEbee', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 32px' }}>
            <XCircle size={60} color="var(--error-red)" />
          </div>
          <div style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', border: '2px solid var(--golden-yellow)', boxShadow: '0 8px 24px rgba(0,0,0,0.05)' }}>
            <h2 style={{ fontSize: '22px', fontWeight: 800, color: 'var(--error-red)', marginBottom: '16px' }}>Application Rejected</h2>
            <p style={{ fontWeight: 700, color: '#444', fontSize: '14px', marginBottom: '8px' }}>Reason for Rejection:</p>
            <p style={{ color: '#222', fontSize: '16px', marginBottom: '24px' }}>{rejectionReason}</p>
            <button onClick={handleReset} className="primary" style={{ width: '100%' }}>Back to Login</button>
          </div>
        </div>
      </div>
    );
  }

  if (showApprovedScreen) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', justifyContent: 'center', alignItems: 'center', backgroundColor: 'var(--peach-background)', padding: '24px' }}>
        <div ref={wrapperRef} style={{ textAlign: 'center', maxWidth: '400px', width: '100%' }}>
          <div style={{ width: '100px', height: '100px', borderRadius: '50%', backgroundColor: '#E8F5E9', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 32px' }}>
            <CheckCircle2 size={60} color="#2E7D32" />
          </div>
          <div style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', border: '2px solid var(--golden-yellow)', boxShadow: '0 8px 24px rgba(0,0,0,0.05)' }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '16px' }}><Logo size={50} /></div>
            <h2 style={{ fontSize: '22px', fontWeight: 800, color: '#2E7D32', marginBottom: '8px' }}>Account Approved!</h2>
            <p style={{ color: '#444', fontSize: '13px', marginBottom: '24px' }}>Welcome to the team. To securely enter the driver portal, you must log in using your official Driver ID.</p>
            <p style={{ fontSize: '11px', color: '#888', fontWeight: 700, letterSpacing: '0.5px' }}>YOUR DRIVER ID</p>
            <div style={{ backgroundColor: 'var(--peach-background)', borderRadius: '12px', padding: '16px', margin: '8px 0 24px' }}>
              <p style={{ fontSize: '24px', fontWeight: 900, color: 'var(--terracotta-primary)' }}>{approvedDriverId}</p>
            </div>
            <button onClick={handleReset} className="primary" style={{ width: '100%' }}>Log in with Driver ID</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="split-screen">
      <div className="split-left" ref={leftPanelRef}>
        <Logo size={160} />
        <h1 style={{ color: 'var(--terracotta-primary)', fontSize: '3.5rem', marginTop: '24px', fontWeight: 900 }}>GroWise</h1>
        <p style={{ color: 'var(--terracotta-primary)', opacity: 0.9, fontSize: '1.25rem', marginTop: '12px', fontWeight: 500 }}>Cultivating Intelligence, Harvesting Tomorrow.</p>
      </div>
      
      <div className="split-right" ref={rightPanelRef}>
        <div className="form-container">
          <div style={{ marginBottom: '40px' }}>
            <h2 style={{ fontSize: '36px', color: '#222', fontWeight: 800 }}>Welcome Back</h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '16px', marginTop: '8px' }}>Please enter your details to sign in.</p>
          </div>
          
          <form ref={formRef} onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '20px', position: 'relative' }}>
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Email, Phone, or GW-D ID</label>
              <input type="text" placeholder="Enter your email address or phone" value={identifier} onChange={(e) => { setIdentifier(e.target.value); setErrorMessage(''); }} className={`${errorMessage ? 'is-invalid' : ''} ${isValidId === true ? 'is-valid' : ''}`} required disabled={isLoading} />
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Password</label>
              <div style={{ position: 'relative' }}>
                <input type={passwordVisible ? "text" : "password"} placeholder="Enter your password" value={password} onChange={(e) => { setPassword(e.target.value); setErrorMessage(''); }} className={errorMessage ? 'is-invalid' : ''} required disabled={isLoading} />
                <button type="button" onClick={() => setPasswordVisible(!passwordVisible)} style={{ position: 'absolute', right: '16px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', width: 'auto', padding: 0, color: 'var(--text-muted)' }} disabled={isLoading}>
                  {passwordVisible ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            {errorMessage && <p style={{ color: 'var(--error-red)', fontSize: '14px', fontWeight: 500, marginTop: '-8px' }}>{errorMessage}</p>}

            <button ref={buttonRef} type="submit" className="primary" disabled={isLoading} style={{ marginTop: '12px', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', transition: 'none' }} onMouseEnter={handleButtonHover} onMouseLeave={handleButtonLeave}>
              {isLoading ? <Spinner /> : 'Sign In'}
            </button>
          </form>

          <div style={{ textAlign: 'center', marginTop: '32px' }}>
            <span style={{ color: 'var(--text-muted)', fontSize: '15px' }}>Don't have an account? </span>
            <Link to={isLoading ? "#" : "/signup"} style={{ color: 'var(--terracotta-primary)', fontWeight: 'bold', textDecoration: 'none', fontSize: '15px', opacity: isLoading ? 0.5 : 1 }}>
              Create account
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
