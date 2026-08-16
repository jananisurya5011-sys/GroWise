import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';

// Auth & Routing
import { AuthProvider } from './contexts/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';

// Public Pages
import Splash from './pages/Splash';
import Login from './pages/Login';
import Signup from './pages/Signup';

// Layout
import Layout from './components/layout/Layout';
import DriverLayout from './components/layout/DriverLayout';

// Profile Shells
import AdminProfile from './pages/admin/Profile';
import AdminDashboard from './pages/admin/Dashboard';
import AdminListScreen from './pages/admin/ListScreen';
import AdminVerification from './pages/admin/Verification';
import AdminDriverDetails from './pages/admin/DriverDetails';
import DriverProfile from './pages/driver/Profile';
import DriverHome from './pages/driver/DriverHome';
import DriverWallet from './pages/driver/DriverWallet';
import DriverActiveTrip from './pages/driver/DriverActiveTrip';
import DriverStandardDelivery from './pages/driver/DriverStandardDelivery';
import FarmerProfile from './pages/farmer/Profile';
import FarmerHome from './pages/farmer/Home';
import FarmerDiagnose from './pages/farmer/Diagnose';
import FarmerListProduct from './pages/farmer/ListProduct';
import FarmerRentalHub from './pages/farmer/RentalHub';
import FarmerSmartCultivation from './pages/farmer/SmartCultivation';
import FarmerWeather from './pages/farmer/Weather';
import CropPoolPage from './pages/farmer/CropPoolPage';
import PoolDetails from './pages/farmer/PoolDetails';
import UserProfile from './pages/user/Profile';
import UHomeScreen from './pages/user/Home';
import UFarmerMenuScreen from './pages/user/FarmerMenu';
import WeatherDetails from './pages/user/WeatherDetails';
import Deals from './pages/shared/Deals';
import TrackModule from './pages/shared/TrackModule';
import TrackOrder from './pages/shared/TrackOrder';
import DonationHub from './pages/ngo/DonationHub';
import ChangePassword from './pages/shared/ChangePassword';
import SelfPickupTracker from './pages/shared/SelfPickupTracker';
const PlaceholderDash = ({ title }) => (
  <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center' }}>
    <h1 style={{ color: '#ccc' }}>{title}</h1>
  </div>
);

function AnimatedRoutes() {
  const location = useLocation();
  
  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        {/* Public Routes */}
        <Route path="/" element={<Splash />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />

        {/* --- ADMIN --- */}
        <Route path="/home/admin" element={<ProtectedRoute allowedRoles={['admin']}><Layout><AdminDashboard /></Layout></ProtectedRoute>} />
        <Route path="/admin/list/:type" element={<ProtectedRoute allowedRoles={['admin']}><Layout><AdminListScreen /></Layout></ProtectedRoute>} />
        <Route path="/admin/verify" element={<ProtectedRoute allowedRoles={['admin']}><Layout><AdminVerification /></Layout></ProtectedRoute>} />
        <Route path="/admin/verify/:email" element={<ProtectedRoute allowedRoles={['admin']}><Layout><AdminDriverDetails /></Layout></ProtectedRoute>} />
        <Route path="/profile/admin" element={<ProtectedRoute allowedRoles={['admin']}><Layout><AdminProfile /></Layout></ProtectedRoute>} />

        {/* --- DRIVER --- */}
        <Route path="/home/driver" element={<ProtectedRoute allowedRoles={['driver']}><DriverLayout><DriverHome /></DriverLayout></ProtectedRoute>} />
        <Route path="/driver/wallet" element={<ProtectedRoute allowedRoles={['driver']}><DriverLayout><DriverWallet /></DriverLayout></ProtectedRoute>} />
        <Route path="/driver/status" element={<ProtectedRoute allowedRoles={['driver']}><DriverLayout><DriverActiveTrip /></DriverLayout></ProtectedRoute>} />
        <Route path="/driver/standard-delivery" element={<ProtectedRoute allowedRoles={['driver']}><DriverLayout><DriverStandardDelivery /></DriverLayout></ProtectedRoute>} />
        <Route path="/profile/driver" element={<ProtectedRoute allowedRoles={['driver']}><DriverLayout><DriverProfile /></DriverLayout></ProtectedRoute>} />

        {/* --- FARMER --- */}
        <Route path="/home/farmer" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerHome /></Layout></ProtectedRoute>} />
        <Route path="/profile/farmer" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerProfile /></Layout></ProtectedRoute>} />
        <Route path="/farmer/diagnose" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerDiagnose /></Layout></ProtectedRoute>} />
        <Route path="/farmer/inventory" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerListProduct /></Layout></ProtectedRoute>} />
        <Route path="/farmer/rental" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerRentalHub /></Layout></ProtectedRoute>} />
        <Route path="/farmer/cultivation" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerSmartCultivation /></Layout></ProtectedRoute>} />
        <Route path="/farmer/weather" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><FarmerWeather /></Layout></ProtectedRoute>} />
        <Route path="/farmer/crop-pool" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><CropPoolPage /></Layout></ProtectedRoute>} />
        <Route path="/farmer/pool/:poolId" element={<ProtectedRoute allowedRoles={['farmer']}><Layout><PoolDetails /></Layout></ProtectedRoute>} />

        {/* --- USER / NGO --- */}
        <Route path="/home/user" element={<ProtectedRoute allowedRoles={['user', 'ngo']}><Layout><UHomeScreen /></Layout></ProtectedRoute>} />
        <Route path="/user/shop" element={<ProtectedRoute allowedRoles={['user', 'ngo']}><Layout><UHomeScreen /></Layout></ProtectedRoute>} />
        <Route path="/user/farmer/:farmerEmail" element={<ProtectedRoute allowedRoles={['user', 'ngo']}><Layout><UFarmerMenuScreen /></Layout></ProtectedRoute>} />
        <Route path="/user/ngo-rescue" element={<ProtectedRoute allowedRoles={['ngo']}><Layout><UHomeScreen isNgoView={true} /></Layout></ProtectedRoute>} />
        <Route path="/profile/user" element={<ProtectedRoute allowedRoles={['user', 'ngo']}><Layout><UserProfile /></Layout></ProtectedRoute>} />
        <Route path="/profile/ngo" element={<ProtectedRoute allowedRoles={['user', 'ngo']}><Layout><UserProfile /></Layout></ProtectedRoute>} />
        <Route path="/weather-details" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer']}><Layout><WeatherDetails /></Layout></ProtectedRoute>} />
        
        {/* --- SHARED --- */}
        <Route path="/deals" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer']}><Layout><Deals /></Layout></ProtectedRoute>} />
        <Route path="/deals/:email" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer']}><Layout><Deals /></Layout></ProtectedRoute>} />
        <Route path="/track" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer']}><Layout><TrackModule /></Layout></ProtectedRoute>} />
        <Route path="/track/:orderId" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer']}><TrackOrder /></ProtectedRoute>} />
        <Route path="/track-self-pickup/:orderId" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer']}><SelfPickupTracker /></ProtectedRoute>} />
        <Route path="/ngo/donation-hub" element={<ProtectedRoute allowedRoles={['ngo', 'user']}><Layout><DonationHub /></Layout></ProtectedRoute>} />
        
        {/* NEW SETTINGS/PASSWORD ROUTe */}
        <Route path="/change-password" element={<ProtectedRoute allowedRoles={['user', 'ngo', 'farmer', 'driver', 'admin']}><ChangePassword /></ProtectedRoute>} />

      </Routes>
    </AnimatePresence>
  );
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <AnimatedRoutes />
      </AuthProvider>
    </Router>
  );
}

export default App;
