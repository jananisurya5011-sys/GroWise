// Simple secure session storage using base64 encoding to mask data (for minimal security before full JWT/Cookies implementation)

export const saveSession = (role, email, driverId = null) => {
  const data = JSON.stringify({ role, email, driverId, timestamp: Date.now() });
  const encoded = btoa(data);
  sessionStorage.setItem('_gw_session', encoded);
};

export const getSession = () => {
  try {
    const encoded = sessionStorage.getItem('_gw_session');
    if (!encoded) return null;
    return JSON.parse(atob(encoded));
  } catch (error) {
    return null;
  }
};

export const clearSession = () => {
  sessionStorage.removeItem('_gw_session');
};
