import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const ProtectedRoute = ({ children, allowedRoles }) => {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    // Could return a global Skeleton or spinner here
    return <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}>Loading...</div>;
  }

  // 1. If no user is found in Context (which reads from sessionStorage), redirect to Login
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // 2. If the user's role isn't allowed to access this specific route, boot them to their designated home
  if (allowedRoles && !allowedRoles.includes(user.role)) {
    // Determine fallback based on their role
    switch (user.role) {
      case 'farmer': return <Navigate to="/home/farmer" replace />;
      case 'driver': return <Navigate to="/driver/status" replace />;
      case 'admin': return <Navigate to="/home/admin" replace />;
      case 'ngo':
      case 'user': return <Navigate to="/home/user" replace />;
      default: return <Navigate to="/login" replace />;
    }
  }

  return children;
};

export default ProtectedRoute;
