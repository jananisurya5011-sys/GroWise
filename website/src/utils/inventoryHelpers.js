export const calculateExpiryStatus = (item) => {
  if (!item.expiryDate) {
    return {
      isVisibleToUser: true,
      discountPercent: 0,
      badge: null,
      status: 'FRESH',
      hoursRemaining: null,
      timeString: null
    };
  }

  const expParts = item.expiryDate.includes('-') ? item.expiryDate.split('-') : item.expiryDate.split('/');
  let expDate;
  if (item.expiryDate.includes('T')) {
    expDate = new Date(item.expiryDate);
  } else if (item.expiryDate.includes('-')) {
    // YYYY-MM-DD
    expDate = new Date(item.expiryDate);
  } else {
    // DD/MM/YYYY
    expDate = new Date(`${expParts[2]}-${expParts[1]}-${expParts[0]}`);
  }

  const currentMs = Date.now();
  const diffMs = expDate.getTime() - currentMs;
  const diffHours = diffMs / (1000 * 60 * 60);

  let isVisibleToUser = true;
  let discountPercent = 0;
  let badge = item.expiryStatus;
  let status = item.expiryStatus || 'Fresh Batch';
  let badgeColor = 'Green';

  if (status === 'Expired') {
    isVisibleToUser = false;
    badgeColor = 'Grey';
  } else if (status === 'ACTIVE NGO FEED') {
    isVisibleToUser = false; // Hidden from Marketplace, NGO feed is handled separately
    badgeColor = 'Purple';
    discountPercent = 0;
  } else if (status === 'Clearance Sale') {
    isVisibleToUser = true;
    discountPercent = 50;
    badgeColor = '#d32f2f'; // Deep Orange-Red
  } else if (status === 'Near Expiry') {
    isVisibleToUser = true;
    discountPercent = 30;
    badgeColor = 'Orange';
  } else {
    isVisibleToUser = true;
    badgeColor = 'Green';
  }

  // Calculate formatted time remaining string
  let timeString = null;
  if (diffHours > 0) {
    const d = Math.floor(diffHours / 24);
    const h = Math.floor(diffHours % 24);
    if (d > 0) timeString = `${d}d ${h}h`;
    else timeString = `${h}h`;
  }

  return {
    isVisibleToUser,
    discountPercent,
    badge,
    status,
    badgeColor,
    hoursRemaining: diffHours,
    timeString
  };
};
