import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import gsap from 'gsap';
import Logo from '../components/Logo';
import apiClient from '../utils/apiClient';

const Signup = () => {
  const navigate = useNavigate();
  const [selectedRole, setSelectedRole] = useState(0); // 0=Farmer, 1=User, 2=Driver
  const roles = ["Farmer", "User", "Driver"];

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passVisible, setPassVisible] = useState(false);
  const [confirmPassVisible, setConfirmPassVisible] = useState(false);

  const [aadhaarNumber, setAadhaarNumber] = useState('');
  const [isAadhaarVerified, setIsAadhaarVerified] = useState(false);

  const [isNgo, setIsNgo] = useState(false);
  const [darpanId, setDarpanId] = useState('');
  const [isDarpanVerified, setIsDarpanVerified] = useState(false);

  const [vehicleType, setVehicleType] = useState('');
  const [licenseBase64, setLicenseBase64] = useState('');
  const [rcBase64, setRcBase64] = useState('');

  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const nameRegex = /^[a-zA-Z ]+$/;
  const emailRegex = /^[a-zA-Z0-9._%+-]+@(gmail\.com|mail\.com)$/;
  const phoneRegex = /^\d{10}$/;
  const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{6,8}$/;
  const darpanRegex = /^[A-Z]{2}\/\d{4}\/\d{7}$/;

  const leftPanelRef = useRef(null);
  const rightPanelRef = useRef(null);
  const formRef = useRef(null);
  const buttonRef = useRef(null);

  useEffect(() => {
    const tl = gsap.timeline();
    tl.fromTo(leftPanelRef.current, { x: '100%', opacity: 0 }, { x: '0%', opacity: 1, duration: 1, ease: 'power3.out' })
      .fromTo(rightPanelRef.current, { x: '-10%', opacity: 0 }, { x: '0%', opacity: 1, duration: 0.8, ease: 'power3.out' }, "-=0.6")
      .fromTo(formRef.current.children, { y: 20, opacity: 0 }, { y: 0, opacity: 1, duration: 0.5, stagger: 0.05, ease: 'power2.out' }, "-=0.4");
  }, []);

  const handleButtonHover = () => { if(!isLoading) gsap.to(buttonRef.current, { scale: 1.02, duration: 0.2, ease: "power1.out" }); };
  const handleButtonLeave = () => { if(!isLoading) gsap.to(buttonRef.current, { scale: 1, duration: 0.2, ease: "power1.out" }); };

  const handleRoleChange = (idx) => {
    if(isLoading) return;
    setSelectedRole(idx);
    setErrorMessage('');
    gsap.fromTo(formRef.current.children, { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: 0.3, stagger: 0.05, ease: 'power2.out', clearProps: 'all' });
  };

  const handleFileChange = (e, setter) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64String = reader.result.replace(/^data:image\/(png|jpg|jpeg);base64,/, "");
        setter(base64String);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!nameRegex.test(name)) return setErrorMessage("Only letters allowed. No spaces or numbers.");
    if (!emailRegex.test(email)) return setErrorMessage("Must use @gmail.com or @mail.com.");
    if (!phoneRegex.test(phone)) return setErrorMessage("Phone number must be exactly 10 digits.");
    if (!passwordRegex.test(password)) return setErrorMessage("Requires 1 uppercase, 1 lowercase, 1 digit, 1 special.");
    if (password !== confirmPassword) return setErrorMessage("Passwords do not match.");
    
    if ((selectedRole === 0 || selectedRole === 2) && !isAadhaarVerified) return setErrorMessage("You must verify your Aadhaar number.");
    if (selectedRole === 1 && isNgo && !isDarpanVerified) return setErrorMessage("You must verify your Darpan ID.");
    if (selectedRole === 2 && !vehicleType) return setErrorMessage("Vehicle Type is required.");
    if (selectedRole === 2 && (!licenseBase64 || !rcBase64)) return setErrorMessage("License and RC Book images are required.");

    setIsLoading(true);
    gsap.to(formRef.current, { opacity: 0.5, duration: 0.3 });

    const roleStr = ["farmer", "user", "driver"][selectedRole];
    
    const requestData = {
      name: name || "", 
      email: email || "", 
      phone: phone || "", 
      password: password || "", 
      role: roleStr || "",
      aadhaarNumber: (selectedRole === 0 || selectedRole === 2) ? (aadhaarNumber || "") : "",
      isNgo: selectedRole === 1 ? isNgo : false,
      darpanId: (selectedRole === 1 && isNgo) ? (darpanId || "") : "",
      vehicleType: selectedRole === 2 ? (vehicleType || "") : "",
      licenseUrl: selectedRole === 2 ? (licenseBase64 || "") : "",
      rcBookUrl: selectedRole === 2 ? (rcBase64 || "") : ""
    };

    try {
      // Using the centralized Axios utility
      await apiClient.post('/api/auth/signup', requestData);
      
      gsap.to(rightPanelRef.current, { x: '-10%', opacity: 0, duration: 0.5, ease: 'power2.in' });
      gsap.to(leftPanelRef.current, { x: '10%', opacity: 0, duration: 0.5, ease: 'power2.in', onComplete: () => {
        navigate('/login');
      }});
      
    } catch (err) {
      setErrorMessage(err.message || 'System Error');
      gsap.to(formRef.current, { opacity: 1, duration: 0.3 });
      gsap.fromTo(formRef.current, { x: -10 }, { x: 10, duration: 0.1, yoyo: true, repeat: 3, onComplete: () => gsap.set(formRef.current, {x: 0}) });
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

  return (
    <div className="split-screen" style={{ flexDirection: 'row-reverse' }}>
      <div className="split-left" ref={leftPanelRef}>
        <Logo size={160} />
        <h1 style={{ color: 'var(--terracotta-primary)', fontSize: '3.5rem', marginTop: '24px', fontWeight: 900 }}>GroWise</h1>
        <p style={{ color: 'var(--terracotta-primary)', opacity: 0.9, fontSize: '1.25rem', marginTop: '12px', fontWeight: 500 }}>Join our community today.</p>
      </div>

      <div className="split-right" ref={rightPanelRef}>
        <div className="form-container">
          <div style={{ marginBottom: '32px' }}>
            <h2 style={{ fontSize: '36px', color: '#222', fontWeight: 800 }}>Create Account</h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '16px', marginTop: '8px' }}>Sign up to get started with GroWise.</p>
          </div>

          <div className="segmented-control">
            <div className="segment-indicator" style={{ width: `${100 / roles.length}%`, transform: `translateX(${selectedRole * 100}%)` }} />
            {roles.map((role, idx) => (
              <div key={idx} className={`segment ${selectedRole === idx ? 'active' : ''}`} onClick={() => handleRoleChange(idx)}>
                {role}
              </div>
            ))}
          </div>

          <form ref={formRef} onSubmit={handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            
            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Full Name</label>
              <input type="text" disabled={isLoading} placeholder="Enter your full name" value={name} onChange={(e) => {setName(e.target.value); setErrorMessage('');}} className={errorMessage && !nameRegex.test(name) ? 'is-invalid' : ''} required />
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Email Address</label>
              <input type="email" disabled={isLoading} placeholder="Enter your email address" value={email} onChange={(e) => {setEmail(e.target.value); setErrorMessage('');}} className={errorMessage && !emailRegex.test(email) ? 'is-invalid' : ''} required />
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Phone Number</label>
              <input type="tel" disabled={isLoading} placeholder="Enter your 10-digit phone number" value={phone} onChange={(e) => {setPhone(e.target.value); setErrorMessage('');}} className={errorMessage && !phoneRegex.test(phone) ? 'is-invalid' : ''} required />
            </div>

            {(selectedRole === 0 || selectedRole === 2) && (
              <div>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Aadhaar Verification</label>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <input type="text" disabled={isLoading} placeholder="Enter your 12-digit Aadhaar number" value={aadhaarNumber} onChange={(e) => {setAadhaarNumber(e.target.value); setIsAadhaarVerified(false); setErrorMessage('');}} style={{ flex: 1 }} required />
                  <button type="button" disabled={isLoading} className={isAadhaarVerified ? "secondary" : "primary"} style={{ width: '120px', padding: '0 16px', boxShadow: 'none' }} onClick={() => { if (aadhaarNumber.length === 12 && /^\d+$/.test(aadhaarNumber)) { setIsAadhaarVerified(true); setErrorMessage(''); } else { setIsAadhaarVerified(false); setErrorMessage('Aadhaar must be exactly 12 digits.'); }}}>
                    {isAadhaarVerified ? 'Verified ✓' : 'Verify'}
                  </button>
                </div>
              </div>
            )}

            {selectedRole === 1 && (
              <div>
                <label style={{ display: 'flex', alignItems: 'center', gap: '12px', color: '#444', fontWeight: 600, cursor: isLoading ? 'not-allowed' : 'pointer', marginBottom: isNgo ? '16px' : '0' }}>
                  <input type="checkbox" disabled={isLoading} checked={isNgo} onChange={(e) => {setIsNgo(e.target.checked); setErrorMessage('');}} style={{ width: '24px', height: '24px', cursor: isLoading ? 'not-allowed' : 'pointer' }} />
                  Register as recognized NGO
                </label>
                
                {isNgo && (
                  <div>
                     <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Darpan ID Verification</label>
                    <div style={{ display: 'flex', gap: '12px' }}>
                      <input type="text" disabled={isLoading} placeholder="Enter your Darpan ID (e.g. TN/2026/1234567)" value={darpanId} onChange={(e) => {setDarpanId(e.target.value); setIsDarpanVerified(false); setErrorMessage('');}} style={{ flex: 1 }} required />
                      <button type="button" disabled={isLoading} className={isDarpanVerified ? "secondary" : "primary"} style={{ width: '120px', padding: '0 16px', boxShadow: 'none' }} onClick={() => { if (darpanRegex.test(darpanId)) { setIsDarpanVerified(true); setErrorMessage(''); } else { setIsDarpanVerified(false); setErrorMessage('Invalid Darpan format.'); }}}>
                        {isDarpanVerified ? 'Verified ✓' : 'Verify'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {selectedRole === 2 && (
              <>
                <div>
                  <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Vehicle Type</label>
                  <select disabled={isLoading} value={vehicleType} onChange={(e) => setVehicleType(e.target.value)} required>
                    <option value="" disabled>Select Vehicle Type</option>
                    <option value="Bike">Bike (&lt;= 20kg)</option>
                    <option value="Auto">Auto (&lt;= 500kg)</option>
                    <option value="Mini-Truck">Mini-Truck (&lt;= 1500kg)</option>
                    <option value="Heavy Lorry">Heavy Lorry (&gt; 1500kg)</option>
                  </select>
                </div>

                <div>
                   <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Required Documents</label>
                  <div style={{ display: 'flex', gap: '16px' }}>
                    <div className={`file-upload-box ${licenseBase64 ? 'has-file' : ''}`}>
                      <input disabled={isLoading} type="file" accept="image/*" onChange={(e) => handleFileChange(e, setLicenseBase64)} className="file-upload-input" required />
                      <span style={{ fontSize: '28px' }}>🪪</span>
                      <p style={{ fontSize: '13px', color: licenseBase64 ? 'var(--terracotta-primary)' : 'var(--text-muted)', marginTop: '8px', fontWeight: 600 }}>{licenseBase64 ? "License Added" : "Upload License"}</p>
                    </div>
                    
                    <div className={`file-upload-box ${rcBase64 ? 'has-file' : ''}`}>
                      <input disabled={isLoading} type="file" accept="image/*" onChange={(e) => handleFileChange(e, setRcBase64)} className="file-upload-input" required />
                      <span style={{ fontSize: '28px' }}>🚘</span>
                      <p style={{ fontSize: '13px', color: rcBase64 ? 'var(--terracotta-primary)' : 'var(--text-muted)', marginTop: '8px', fontWeight: 600 }}>{rcBase64 ? "RC Book Added" : "Upload RC Book"}</p>
                    </div>
                  </div>
                </div>
              </>
            )}

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Password</label>
              <div style={{ position: 'relative' }}>
                <input type={passVisible ? "text" : "password"} disabled={isLoading} placeholder="Enter your password" value={password} onChange={(e) => {setPassword(e.target.value); setErrorMessage('');}} required />
                <button type="button" disabled={isLoading} onClick={() => setPassVisible(!passVisible)} style={{ position: 'absolute', right: '16px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', width: 'auto', padding: 0, color: 'var(--text-muted)', fontSize: '14px', fontWeight: 600, boxShadow: 'none' }}>
                  {passVisible ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: 600, color: '#444' }}>Confirm Password</label>
              <div style={{ position: 'relative' }}>
                <input type={confirmPassVisible ? "text" : "password"} disabled={isLoading} placeholder="Confirm your password" value={confirmPassword} onChange={(e) => {setConfirmPassword(e.target.value); setErrorMessage('');}} required />
                <button type="button" disabled={isLoading} onClick={() => setConfirmPassVisible(!confirmPassVisible)} style={{ position: 'absolute', right: '16px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', width: 'auto', padding: 0, color: 'var(--text-muted)', fontSize: '14px', fontWeight: 600, boxShadow: 'none' }}>
                  {confirmPassVisible ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>

            {errorMessage && <p style={{ color: 'var(--error-red)', fontSize: '14px', fontWeight: 500, marginTop: '-8px' }}>{errorMessage}</p>}

            <button ref={buttonRef} type="submit" className="primary" disabled={isLoading} style={{ marginTop: '12px', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', transition: 'none' }} onMouseEnter={handleButtonHover} onMouseLeave={handleButtonLeave}>
              {isLoading ? <Spinner /> : 'Sign Up'}
            </button>
          </form>

          <div style={{ textAlign: 'center', marginTop: '32px' }}>
            <span style={{ color: 'var(--text-muted)', fontSize: '15px' }}>Already have an account? </span>
            <Link to={isLoading ? "#" : "/login"} style={{ color: 'var(--terracotta-primary)', fontWeight: 'bold', textDecoration: 'none', fontSize: '15px', opacity: isLoading ? 0.5 : 1 }}>
              Sign In
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Signup;
