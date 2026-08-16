// Shared constants to ensure state consistency across the application

export const ACTIVE_DRIVER_STATUSES = [
  'EN_ROUTE_TO_PICKUP', 
  'WAITING_AT_PICKUP', 
  'EN_ROUTE_TO_HOST_PICKUP', 
  'WAITING_AT_HOST_PICKUP',
  'EN_ROUTE_TO_CO_LOADER_PICKUP', 
  'WAITING_AT_CO_LOADER_PICKUP',
  'IN_TRANSIT', 
  'EN_ROUTE_TO_DROP', 
  'WAITING_AT_DROP',
  'EN_ROUTE_TO_HOST_DROP', 
  'WAITING_AT_HOST_DROP',
  'EN_ROUTE_TO_CO_LOADER_DROP', 
  'WAITING_AT_CO_LOADER_DROP'
];

export const formatCurrency = (value) => {
  const amount = Number(value || 0);
  return `₹${amount.toFixed(2)}`;
};

export const getOrderType = (orderId) => {
  if (!orderId) return 'STANDARD';
  const idStr = String(orderId).toUpperCase();
  if (idStr.startsWith('DEAL-DON-') || idStr.startsWith('GW-DON-')) return 'DONATION';
  if (idStr.startsWith('POOL')) return 'POOL';
  return 'STANDARD';
};
