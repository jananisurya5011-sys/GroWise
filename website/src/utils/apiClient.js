import axios from 'axios';

/**
 * UNIVERSAL API UTILITY
 * 
 * This Axios instance acts as a centralized hub for all outgoing HTTP requests.
 * By using relative paths (e.g., apiClient.post('/login')), the browser treats 
 * the request as same-origin, naturally avoiding CORS errors. The Vite Proxy 
 * then picks up the request and forwards it to the Python backend.
 */
const apiClient = axios.create({
  // No baseURL is set so it uses the local Vite dev server origin to trigger the proxy
  headers: {
    'Content-Type': 'application/json',
  },
  // Automatically throw errors for non-2xx HTTP responses
  validateStatus: function (status) {
    return status >= 200 && status < 300; 
  },
});

// Optional: Add Request Interceptors (e.g., attaching Bearer Tokens in the future)
apiClient.interceptors.request.use((config) => {
  // const session = getSession(); // From session.js
  // if (session && session.token) config.headers.Authorization = `Bearer ${session.token}`;
  return config;
}, (error) => {
  return Promise.reject(error);
});

// Optional: Add Response Interceptors (e.g., globally handling 401 Unauthorized errors)
apiClient.interceptors.response.use((response) => {
  return response;
}, (error) => {
  // Extract custom error message from Flask backend if available
  const message = error.response?.data?.error || error.response?.data?.message || error.message || 'An unexpected error occurred';
  // Attach the message to the original error object to maintain compatibility
  // while preserving error.response for components that need it.
  error.message = message;
  return Promise.reject(error);
});

export default apiClient;
