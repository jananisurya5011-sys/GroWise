import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../utils/apiClient';
import gsap from 'gsap';
import { Users, Sprout, Truck, Briefcase, Filter } from 'lucide-react';
import { 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';

const AdminStatCard = ({ title, count, icon, color, onClick }) => (
  <div 
    onClick={onClick}
    style={{ 
      backgroundColor: '#fff', 
      borderRadius: '20px', 
      padding: '20px', 
      height: '130px',
      border: '2px solid var(--peach-background)',
      boxShadow: '0 4px 12px rgba(0,0,0,0.03)',
      cursor: 'pointer',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'space-between',
      transition: 'transform 0.2s',
      transform: 'scale(1)',
    }}
    onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.02)'}
    onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
  >
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
      <div style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: `${color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        {icon}
      </div>
    </div>
    <div>
      <h2 style={{ fontSize: '28px', fontWeight: 800, margin: '4px 0 0 0', color: '#111' }}>{count}</h2>
      <p style={{ fontSize: '13px', color: '#666', margin: 0, fontWeight: 500 }}>{title}</p>
    </div>
  </div>
);

const Dashboard = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState({ totalUsers: 0, activeFarmers: 0, verifiedDrivers: 0, activeDealsToday: 0 });
  const [graphData, setGraphData] = useState([]);
  const [timeframe, setTimeframe] = useState('7 Days');
  const [isGraphLoading, setIsGraphLoading] = useState(true);
  
  const containerRef = useRef(null);
  
  useEffect(() => {
    fetchStats();
  }, []);

  useEffect(() => {
    fetchGraphData();
  }, [timeframe]);

  useEffect(() => {
    if (containerRef.current) {
      gsap.fromTo(containerRef.current.children, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' }
      );
    }
  }, []);

  const fetchStats = async () => {
    try {
      const { data } = await apiClient.get('/api/admin/stats');
      if (data.success) {
        setStats(data);
      }
    } catch (error) {
      console.error("Failed to load stats", error);
    }
  };

  const fetchGraphData = async () => {
    setIsGraphLoading(true);
    try {
      const { data } = await apiClient.get(`/api/admin/graph-data?range=${timeframe}`);
      if (data.success) {
        // Map the backend array to Recharts format
        const mappedData = data.dataPoints.map((val, i) => {
          const daysAgo = (data.dataPoints.length - 1) - i;
          return {
            name: daysAgo === 0 ? 'Today' : `${daysAgo}d ago`,
            orders: val
          };
        });
        setGraphData(mappedData);
      }
    } catch (error) {
      console.error("Failed to load graph", error);
    } finally {
      setIsGraphLoading(false);
    }
  };

  return (
    <div ref={containerRef} style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <header style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '32px', fontWeight: 800, color: '#111', margin: '0 0 8px 0' }}>GroWise HQ</h1>
        <p style={{ color: '#666', fontSize: '15px', margin: 0 }}>System Overview & Real-time Metrics</p>
      </header>

      {/* STATS GRID */}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px', marginBottom: '40px' }}>
        <AdminStatCard 
          title="Total Users" 
          count={stats.totalUsers} 
          icon={<Users size={20} color="#3b82f6" />} 
          color="#3b82f6" 
          onClick={() => navigate('/admin/list/users')} 
        />
        <AdminStatCard 
          title="Active Farmers" 
          count={stats.activeFarmers} 
          icon={<Sprout size={20} color="#10b981" />} 
          color="#10b981" 
          onClick={() => navigate('/admin/list/farmers')} 
        />
        <AdminStatCard 
          title="Verified Drivers" 
          count={stats.verifiedDrivers} 
          icon={<Truck size={20} color="#f59e0b" />} 
          color="#f59e0b" 
          onClick={() => navigate('/admin/list/drivers')} 
        />
        <AdminStatCard 
          title="Active Deals (Today)" 
          count={stats.activeDealsToday} 
          icon={<Briefcase size={20} color="var(--terracotta-primary)" />} 
          color="var(--terracotta-primary)" 
          onClick={() => navigate('/admin/list/deals')} 
        />
      </section>

      {/* GRAPH SECTION */}
      <section style={{ backgroundColor: '#fff', borderRadius: '24px', padding: '32px', boxShadow: '0 4px 20px rgba(0,0,0,0.04)', border: '1px solid #eee' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
          <div>
            <h2 style={{ fontSize: '20px', fontWeight: 700, color: '#111', margin: '0 0 4px 0' }}>Order Volume Trends</h2>
            <p style={{ fontSize: '13px', color: '#888', margin: 0 }}>Standard, Rescue, & Pool Deals</p>
          </div>
          
          <div style={{ position: 'relative' }}>
            <select 
              value={timeframe}
              onChange={(e) => setTimeframe(e.target.value)}
              style={{
                appearance: 'none',
                padding: '10px 36px 10px 16px',
                borderRadius: '12px',
                border: '1px solid #ddd',
                backgroundColor: '#fafafa',
                fontSize: '14px',
                fontWeight: 600,
                color: '#333',
                cursor: 'pointer',
                outline: 'none'
              }}
            >
              <option value="7 Days">7 Days</option>
              <option value="1 Month">1 Month</option>
              <option value="6 Months">6 Months</option>
            </select>
            <Filter size={16} color="#888" style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }} />
          </div>
        </div>

        <div style={{ width: '100%', height: '350px', position: 'relative' }}>
          {isGraphLoading ? (
            <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div style={{ color: 'var(--terracotta-primary)', fontWeight: 600 }}>Loading Chart...</div>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={graphData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorOrders" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--terracotta-primary)" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="var(--terracotta-primary)" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eee" />
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: '#888' }} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: '#888' }} />
                <Tooltip 
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', backgroundColor: '#333', color: '#fff' }}
                  itemStyle={{ color: '#fff', fontWeight: 'bold' }}
                  labelStyle={{ color: '#aaa', fontSize: '12px' }}
                />
                <Area 
                  type="monotone" 
                  dataKey="orders" 
                  stroke="var(--terracotta-primary)" 
                  strokeWidth={4}
                  fillOpacity={1} 
                  fill="url(#colorOrders)" 
                  activeDot={{ r: 6, fill: 'var(--terracotta-primary)', stroke: '#fff', strokeWidth: 2 }}
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </div>
  );
};

export default Dashboard;
