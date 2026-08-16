export const getEffectivePrice = (item) => {
  if (!item) return 0;
  
  // 1. Active discounted price (e.g. Flash Sale, Expiry Discount)
  if (item.expiryStatus?.discountPercent) {
    return item.pricePerKg * (1 - (item.expiryStatus.discountPercent / 100));
  }
  if (item.discountPercent) {
    return item.pricePerKg * (1 - (item.discountPercent / 100));
  }
  if (item.flashSalePrice) {
    return item.flashSalePrice;
  }
  
  // 2. Current inventory selling price / Regular selling price
  if (item.pricePerKg) {
    return item.pricePerKg;
  }
  if (item.currentPrice) {
    return item.currentPrice;
  }
  
  // 3. Original market price only if nothing else exists
  if (item.marketPrice) {
    return item.marketPrice;
  }
  if (item.basePrice) {
    return item.basePrice;
  }
  
  return 0;
};
