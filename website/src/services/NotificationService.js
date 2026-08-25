import toast from 'react-hot-toast';

const notify = {
  success: (message) => toast.success(message, {
    style: {
      background: '#ecfdf5',
      color: '#059669',
      border: '1px solid #10b981',
    },
    iconTheme: {
      primary: '#10b981',
      secondary: '#fff',
    },
  }),
  error: (message) => toast.error(message, {
    style: {
      background: '#fef2f2',
      color: '#dc2626',
      border: '1px solid #ef4444',
    },
    iconTheme: {
      primary: '#ef4444',
      secondary: '#fff',
    },
  }),
  warning: (message) => toast(message, {
    icon: '⚠️',
    style: {
      background: '#fffbeb',
      color: '#d97706',
      border: '1px solid #f59e0b',
    },
  }),
  info: (message) => toast(message, {
    icon: 'ℹ️',
    style: {
      background: '#eff6ff',
      color: '#2563eb',
      border: '1px solid #3b82f6',
    },
  }),
  loading: (message) => toast.loading(message, {
    style: {
      background: '#f8fafc',
      color: '#475569',
      border: '1px solid #cbd5e1',
    }
  }),
  dismiss: (id) => toast.dismiss(id)
};

export default notify;
