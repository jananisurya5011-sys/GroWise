from flask import Blueprint, request, jsonify
from firebase_admin import firestore
import time
import math
import random
from routes.wallet import lock_escrow_sync
from routes.logistics import calculate_fare_internal

deals_bp = Blueprint('deals_bp', __name__)
db = firestore.client()

@deals_bp.route('/cron/expire-requests', methods=['POST'])
def expire_requests():
    """ 
    Donation deals now use standard marketplace chat architecture which does not auto-expire.
    Keeping endpoint alive for app.py scheduler backward compatibility.
    """
    return jsonify({"success": True, "expired": 0}), 200

from services.inventory_service import InventoryService

@deals_bp.route('/transition', methods=['POST'])
def transition_deal():
    try:
        data = request.json
        chat_id = data.get('chatId')
        deal_id = data.get('dealId')
        new_status = data.get('status')
        new_type = data.get('type')
        item_id = data.get('itemId')
        req_qty = float(data.get('kg', 0.0))
        
        if not chat_id or not deal_id or not new_status:
            print(f"DEBUG TRANSITION: Missing fields! chat_id={chat_id}, deal_id={deal_id}, new_status={new_status}, data={data}")
            return jsonify({"success": False, "error": f"Missing required fields. Received data: {data}"}), 400
            
        doc_ref = db.collection('chats').document(chat_id).collection('messages').document(deal_id)
        chat_ref = db.collection('chats').document(chat_id)
        
        @firestore.transactional
        def do_transition(txn, d_ref, c_ref):
            doc = d_ref.get(transaction=txn)
            if not doc.exists:
                print(f"DEBUG TRANSITION: Deal not found! chat_id={chat_id}, deal_id={deal_id}")
                return False, "Deal not found"
            
            deal_data = doc.to_dict()
            current_status = deal_data.get('status')
            deal_type = deal_data.get('dealType', '')
            
            # Inventory Operations
            if new_status == 'ACCEPTED' and current_status != 'ACCEPTED':
                if deal_type == 'DONATION' and item_id:
                    # Reserve Inventory
                    inv_service = InventoryService(db)
                    if not inv_service.reserve(item_id, req_qty, txn):
                        return False, "Not enough inventory to reserve."
            
            elif new_status == 'CANCELLED' and current_status != 'CANCELLED':
                if deal_type == 'DONATION' and item_id:
                    # Restore Inventory
                    inv_service = InventoryService(db)
                    inv_service.restore(item_id, req_qty, txn)
            
            updates = {"status": new_status, "timestamp": int(time.time() * 1000)}
            if new_type:
                updates["type"] = new_type
            
            # Merge any other provided fields
            for k, v in data.items():
                if k not in ['chatId', 'dealId', 'status', 'type', 'itemId', 'kg']:
                    updates[k] = v
                    
            txn.update(d_ref, updates)
            txn.update(c_ref, {"lastMessage": f"Deal updated to {new_status}", "timestamp": updates["timestamp"]})
            return True, updates

        txn = db.transaction()
        success, result = do_transition(txn, doc_ref, chat_ref)
        
        if success:
            return jsonify({"success": True, "updates": result}), 200
        else:
            return jsonify({"success": False, "error": result}), 400
            
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "error": str(e)}), 400

@deals_bp.route('/request-donation', methods=['POST'])
def request_donation():
    try:
        data = request.json
        ngo_email = data.get('ngoEmail')
        farmer_email = data.get('farmerEmail')
        item_id = data.get('itemId')
        req_qty = float(data.get('requestedQuantity', 0.0))
        lat = data.get('lat', 0.0)
        lon = data.get('lon', 0.0)
        
        if not ngo_email or not farmer_email or not item_id or req_qty <= 0:
            return jsonify({"success": False, "error": "Missing required fields"}), 400
            
        # Create or find chat between ngo and farmer for this item
        # To simplify, we generate a chat ID based on emails and itemId
        chat_id = f"{ngo_email}_{farmer_email}_{item_id}".replace('.', '_').replace('@', '_')
        
        chat_ref = db.collection('chats').document(chat_id)
        chat_doc = chat_ref.get()
        
        timestamp = int(time.time() * 1000)
        
        if not chat_doc.exists:
            chat_ref.set({
                'chatId': chat_id,
                'participants': [ngo_email, farmer_email],
                'itemId': item_id,
                'type': 'DONATION_CHAT',
                'createdAt': timestamp,
                'lastMessage': 'Donation Request Sent',
                'timestamp': timestamp
            })
            
        # Add the deal message
        messages_ref = chat_ref.collection('messages')
        deal_id = messages_ref.document().id
        messages_ref.document(deal_id).set({
            'messageId': deal_id,
            'senderId': ngo_email,
            'receiverId': farmer_email,
            'messageType': 'deal',
            'dealType': 'DONATION',
            'kg': req_qty,
            'itemId': item_id,
            'status': 'PENDING',
            'text': f"Requested {req_qty} kg for rescue.",
            'timestamp': timestamp
        })
        
        return jsonify({"success": True, "chatId": chat_id, "dealId": deal_id}), 200
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500
