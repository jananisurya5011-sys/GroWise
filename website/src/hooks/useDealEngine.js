import { doc, setDoc } from 'firebase/firestore';
import { db } from '../utils/firebase';
import apiClient from '../utils/apiClient';

export const useDealEngine = (chatId, userEmail, otherEmail) => {
  const updateHeader = async (msg) => {
    if (!chatId) return;
    try {
      await setDoc(doc(db, 'chats', chatId), { lastMessage: msg, timestamp: Date.now() }, { merge: true });
    } catch (e) {
      console.error("Failed to update chat header:", e);
    }
  };
  
  const withdrawDeal = async (messageId) => {
    try {
      await apiClient.post('/api/deals/transition', { chatId, dealId: messageId, status: 'WITHDRAWN' });
      await updateHeader("Offer Withdrawn");
    } catch (e) { console.error("Failed to withdraw deal:", e); }
  };

  const declineDeal = async (msg) => {
    const dealId = msg.id || msg;
    try {
      await apiClient.post('/api/deals/transition', { chatId, dealId, status: 'DECLINED' });
      await updateHeader("Offer Declined");
    } catch (e) { console.error("Failed to decline deal:", e); }
  };

  const acceptDeal = async (msg) => {
    const dealId = msg.id || msg;
    const isDonation = msg.dealType === 'DONATION' || msg.type === 'DONATION_REQUEST';
    try {
      await apiClient.post('/api/deals/transition', { 
        chatId, dealId, status: 'ACCEPTED', 
        itemId: msg.itemId || msg.id, kg: msg.kg || 0 
      });
      await updateHeader("Offer Accepted");
    } catch (e) { console.error("Failed to accept deal:", e); }
  };

  const submitCounter = async (oldMessageId, counterData) => {
    try {
      // Counter replaces the old card natively
      await apiClient.post('/api/deals/transition', {
        chatId, dealId: oldMessageId, status: 'PENDING', type: 'COUNTER_CARD',
        cropName: counterData.cropName, 
        kg: counterData.kg, 
        basePrice: counterData.basePrice,
        targetPrice: parseFloat(counterData.targetPrice), 
        imageUrl: counterData.imageUrl || '',
        reason: counterData.reason || ''
      });
      await updateHeader("Counter Offer Sent");
    } catch (e) { console.error("Failed to submit counter offer:", e); }
  };

  const updateLocation = async (messageId, locationData) => {
    try {
      await apiClient.post('/api/deals/transition', { chatId, dealId: messageId, status: 'LOCATION_UPDATED', ...locationData });
      await updateHeader("Location Updated");
    } catch (e) { console.error("Failed to update location:", e); }
  };

  const generateInvoice = async (oldMessageId, invoiceData) => {
    if (!userEmail || !otherEmail) {
      throw new Error("Missing user emails for invoice generation.");
    }
    try {
      const payload = {
        chatId, dealId: oldMessageId,
        type: 'INVOICE_CARD',
        ...invoiceData,
        status: 'PENDING'
      };
      await apiClient.post('/api/deals/transition', payload);
      await updateHeader("Invoice Generated");
    } catch (e) {
      console.error("[generateInvoice] FAILED to generate invoice:", e);
      throw e;
    }
  };

  return {
    withdrawDeal,
    declineDeal,
    acceptDeal,
    submitCounter,
    updateLocation,
    generateInvoice
  };
};
