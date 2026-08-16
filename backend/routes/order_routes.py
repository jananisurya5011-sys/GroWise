# routes/order_routes.py
from flask import Blueprint, request, jsonify
import random
import time
from firebase_admin import firestore
from routes.wallet import lock_escrow_sync, refund_escrow_sync  # Added Wallet Engine Import

# Create a Blueprint named 'order_bp'
order_bp = Blueprint('order_bp', __name__)
db = firestore.client()

@order_bp.route('/create_order', methods=['POST'])
def create_order():
    try:
        import math
        import logging
        data = request.json
        
        def safe_float(val):
            try:
                if val is None: return 0.0
                f = float(val)
                if math.isnan(f) or math.isinf(f): return 0.0
                return f
            except (TypeError, ValueError):
                return 0.0

        user_email = data.get('userEmail')
        total_paid = safe_float(data.get('totalPaid', 0.0))
        crop_name = data.get('cropName', 'Crop')
        is_donation = bool(data.get('isDonation', False))
        chat_id = data.get('chatId')
        deal_id = data.get('dealId')
        
        # 0. Generate Order ID Centrally
        # Ignore frontend orderId entirely. Backend is the source of truth.
        order_id = f"GW-DON-{random.randint(100000, 999999)}" if is_donation else f"GW-{random.randint(100000, 999999)}"

        pickup_otp = str(random.randint(1000, 9999))
        drop_otp = str(random.randint(1000, 9999)) if data.get('vehicleType') != 'Self Pickup' else None

        # Refs
        wallet_ref = db.collection('wallets').document(user_email) if user_email else None
        tx_ref = db.collection('transactions').document()
        item_id = data.get('itemId')
        if not item_id:
            raise Exception("Item ID is missing. Cannot process order without inventory reference.")
            
        inv_ref = db.collection('inventory').document(item_id)
        order_ref = db.collection('orders').document(order_id)
        
        if not chat_id or not deal_id:
            raise Exception("chatMessageId invalid (missing chatId or dealId).")
            
        chat_msg_ref = db.collection('chats').document(chat_id).collection('messages').document(deal_id)
        chat_ref = db.collection('chats').document(chat_id)

        @firestore.transactional
        def atomic_payment_workflow(txn):
            logging.info(f"Starting transaction for deal {deal_id}, order {order_id}")
            
            # --- PHASE 1: ALL READS ---
            wallet_doc = None
            if total_paid > 0 and wallet_ref:
                wallet_doc = wallet_ref.get(transaction=txn)
            
            weight_kg = safe_float(data.get('weightKg', 0.0))
            inv_doc = None
            if weight_kg > 0:
                inv_doc = inv_ref.get(transaction=txn)
                
            msg_doc = chat_msg_ref.get(transaction=txn)
            
            # --- PHASE 2: VALIDATION ---
            bal = 0.0
            if total_paid > 0 and wallet_ref:
                bal = float(wallet_doc.to_dict().get('balance', 0.0)) if (wallet_doc and wallet_doc.exists) else 0.0
                if bal < total_paid:
                    raise Exception("Insufficient wallet balance.")
                    
            new_available = 0.0
            reserved = 0.0
            moq = 0.0
            if weight_kg > 0:
                if not inv_doc or not inv_doc.exists:
                    raise Exception("Inventory document missing.")
                inv_data = inv_doc.to_dict()
                available = float(inv_data.get('availableKg', 0))
                if available < weight_kg:
                    raise Exception(f"Item is out of stock or insufficient quantity. Available: {available}kg, Requested: {weight_kg}kg")
                reserved = float(inv_data.get('reservedQuantity', 0))
                new_available = available - weight_kg
                moq = float(inv_data.get('moq', 0))

            if not msg_doc.exists:
                raise Exception("chatMessageId invalid (document does not exist).")

            # --- PHASE 3: ALL WRITES ---
            if total_paid > 0 and wallet_ref:
                txn.update(wallet_ref, {"balance": bal - total_paid})
                txn.set(tx_ref, {
                    "email": user_email, "type": "ESCROW_LOCK", "title": f"Escrow Hold for {crop_name}",
                    "amount": total_paid, "isCredit": False, "timestamp": int(time.time() * 1000), "orderId": order_id
                })
                logging.info(f"Escrow locked for {total_paid}")

            if weight_kg > 0:
                inv_updates = {
                    'availableKg': new_available,
                    'reservedQuantity': reserved + weight_kg
                }
                if new_available < moq:
                    inv_updates['moq'] = 0.0
                txn.update(inv_ref, inv_updates)
                logging.info(f"Inventory deducted for item {item_id}. New available: {new_available}")

            order_data = {
                "orderId": order_id,
                "farmerEmail": data.get('farmerEmail'),
                "userEmail": user_email,
                "cropName": crop_name,
                "weightKg": weight_kg,
                "cropValue": safe_float(data.get('cropValue')),
                "transportFare": safe_float(data.get('transportFare')),
                "totalPaid": total_paid,
                "isDonation": is_donation,
                "pickupAddress": data.get('pickupAddress'),
                "dropAddress": data.get('dropAddress'),
                "pickupLat": safe_float(data.get('pickupLat')),
                "pickupLon": safe_float(data.get('pickupLon')),
                "dropLat": safe_float(data.get('dropLat')),
                "dropLon": safe_float(data.get('dropLon')),
                "distanceKm": safe_float(data.get('distanceKm')),
                "vehicleType": data.get('vehicleType'),
                "pickupOtp": pickup_otp,
                "dropOtp": drop_otp,
                "status": "PENDING_DRIVER" if data.get('vehicleType') != 'Self Pickup' else "PICKUP_PENDING",
                "flags": 0,
                "timestamp": int(time.time() * 1000),
                "dealId": deal_id or '',
                "itemId": item_id or ''
            }
            txn.set(order_ref, order_data)
            logging.info(f"Order created: {order_id}")

            chat_updates = {
                "type": "RECEIPT_CARD",
                "status": "ORDER_CREATED",
                "orderId": order_id,
                "pickupOtp": pickup_otp,
                "dropOtp": drop_otp,
                "totalPrice": total_paid,
                "transportCost": safe_float(data.get('transportFare')),
                "pickupAddress": data.get('pickupAddress'),
                "deliveryAddress": data.get('dropAddress'),
                "vehicleType": data.get('vehicleType', 'Any'),
                "distanceKm": safe_float(data.get('distanceKm')),
                "timestamp": int(time.time() * 1000),
                "negotiatedPrice": safe_float(data.get('basePrice'))
            }
            txn.update(chat_msg_ref, chat_updates)
            logging.info(f"Chat {deal_id} updated to RECEIPT_CARD")
            
            txn.update(chat_ref, {
                "lastMessage": "Payment Secured. Order Placed.",
                "timestamp": chat_updates["timestamp"],
                "unreadCountFarmer": firestore.Increment(1)
            })

            return {"orderId": order_id, "pickupOtp": pickup_otp, "dropOtp": drop_otp}

        txn = db.transaction()
        result = atomic_payment_workflow(txn)
        return jsonify({"success": True, "message": "Order created securely.", **result}), 200

    except Exception as e:
        import logging
        logging.error(f"Order pipeline failed: {str(e)}")
        return jsonify({"success": False, "error": str(e)}), 400

from services.inventory_service import InventoryService

@order_bp.route('/verify_self_pickup', methods=['POST'])
def verify_self_pickup():
    try:
        data = request.json
        order_id = data.get('orderId')
        otp = data.get('otp')
        farmer_id = data.get('farmerId')

        if not order_id or not otp or not farmer_id:
            return jsonify({"success": False, "error": "Missing required fields"}), 400

        doc_ref = db.collection('orders').document(order_id)
        
        @firestore.transactional
        def do_verify(txn, d_ref):
            doc = d_ref.get(transaction=txn)
            if not doc.exists:
                return False, "Order not found"
            
            order_data = doc.to_dict()
            if order_data.get('farmerEmail') != farmer_id:
                return False, "Unauthorized farmer"
                
            if str(order_data.get('pickupOtp')) != str(otp):
                return False, "Invalid OTP"
                
            if order_data.get('status') == 'COMPLETED':
                return False, "Order already completed"
                
            # Deduct inventory
            item_id = order_data.get('itemId')
            req_qty = float(order_data.get('weightKg', 0))
            
            if item_id:
                inv_service = InventoryService(db)
                inv_service.deduct(item_id, req_qty, txn)
                
            txn.update(d_ref, {"status": "COMPLETED"})
            
            return True, "Verification successful"

        txn = db.transaction()
        success, result = do_verify(txn, doc_ref)
        if success:
            return jsonify({"success": True, "message": result}), 200
        else:
            return jsonify({"success": False, "error": result}), 400
            
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@order_bp.route('/cancel_order', methods=['POST'])
def cancel_standard_order():
    try:
        data = request.get_json()
        order_id = data.get('orderId')
        
        order_ref = db.collection('orders').document(order_id)
        doc = order_ref.get()
        if not doc.exists:
            return jsonify({"error": "Order not found"}), 404
            
        order = doc.to_dict()
        reason = data.get('reason', '')
        
        if order.get('driverEmail') is not None:
            # Allow cancellation IF it's a valid timeout
            if reason == 'TIMEOUT':
                arrival_ts = order.get('pickupArrivalTimestamp', 0)
                current_ts = int(time.time() * 1000)
                # Ensure 10 minutes (600,000 ms) have passed
                if current_ts - arrival_ts < 600000:
                    return jsonify({"success": False, "error": "Timeout period has not expired yet."}), 400
            else:
                return jsonify({"success": False, "error": "Cannot cancel: Driver already assigned."}), 400
                
        new_status = "CANCELLED_TIMEOUT" if reason == 'TIMEOUT' else "CANCELLED_BY_USER"
        
        order_ref.update({
            "status": new_status,
            "cancelReason": "Pickup OTP Expired" if reason == 'TIMEOUT' else "User Cancelled"
        })
        
        # 2. Process Escrow Refund for User
        total_paid = float(order.get('totalPaid', 0.0))
        user_email = order.get('userEmail')
        if total_paid > 0 and user_email:
            refund_escrow_sync(user_email, total_paid, order_id, f"Refund for Cancelled {order.get('cropName', 'Order')}")
            
        return jsonify({"success": True, "message": "Order cancelled and Escrow refunded."}), 200
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@order_bp.route('/request-donation', methods=['POST'])
def request_donation():
    try:
        data = request.json
        ngo_email = data.get('ngoEmail')
        farmer_email = data.get('farmerEmail')
        item_id = data.get('itemId')
        req_qty = float(data.get('requestedQuantity', 0.0))
        
        if not ngo_email or not farmer_email or not item_id or req_qty <= 0:
            return jsonify({"success": False, "error": "Missing fields"}), 400
            
        import random
        import time
        ts = int(time.time() * 1000)
        order_id = f"DEAL-DON-{ts}"
        order_ref = db.collection('orders').document(order_id)
        
        inv_ref = db.collection('inventory').document(item_id)
        inv_doc = inv_ref.get()
        if not inv_doc.exists or float(inv_doc.to_dict().get('availableKg', 0)) < req_qty:
            return jsonify({"success": False, "error": "Not enough inventory"}), 400
            
        chat_id = f"{ngo_email}_{farmer_email}" if ngo_email < farmer_email else f"{farmer_email}_{ngo_email}"
        ts = int(time.time() * 1000)
        
        db.collection('chats').document(chat_id).set({
            "lastMessage": "Donation Request Pending",
            "timestamp": ts,
            "userEmail": ngo_email,
            "farmerEmail": farmer_email
        }, merge=True)
        
        ngo_user_doc = db.collection('users').document(ngo_email).get()
        ngo_name = ngo_user_doc.to_dict().get('name', 'NGO') if ngo_user_doc.exists else 'NGO'
        
        farmer_user_doc = db.collection('users').document(farmer_email).get()
        farmer_name = farmer_user_doc.to_dict().get('name', 'Farmer') if farmer_user_doc.exists else 'Farmer'

        db.collection('chats').document(chat_id).collection('messages').document(order_id).set({
            "type": "DONATION_ORDER_CARD",
            "event": "REQUEST_CREATED",
            "dealId": order_id,
            "orderId": order_id,
            "senderId": ngo_email,
            "receiverId": farmer_email,
            "userName": ngo_name,
            "farmerName": farmer_name,
            "itemId": item_id,
            "imageUrl": inv_doc.to_dict().get('imageUrl', inv_doc.to_dict().get('cropImage', '')),
            "cropName": inv_doc.to_dict().get('cropName', 'Donation'),
            "kg": req_qty,
            "status": "PENDING_FARMER_APPROVAL",
            "pickupAddress": inv_doc.to_dict().get('farmAddress', ''),
            "pickupLat": float(inv_doc.to_dict().get('lat', 0.0)),
            "pickupLon": float(inv_doc.to_dict().get('lon', 0.0)),
            "timestamp": ts
        })
            
        order_data = {
            "orderId": order_id,
            "chatId": chat_id,
            "farmerEmail": farmer_email,
            "userEmail": ngo_email,
            "itemId": item_id,
            "imageUrl": inv_doc.to_dict().get('imageUrl', inv_doc.to_dict().get('cropImage', '')),
            "cropName": inv_doc.to_dict().get('cropName', 'Donation'),
            "weightKg": req_qty,
            "cropValue": 0.0,
            "transportFare": 0.0,
            "totalPaid": 0.0,
            "isDonation": True,
            "orderType": "DONATION",
            "status": "PENDING_FARMER_APPROVAL",
            "timestamp": ts,
            "pickupAddress": inv_doc.to_dict().get('farmAddress', ''),
            "pickupLat": float(inv_doc.to_dict().get('lat', 0.0)),
            "pickupLon": float(inv_doc.to_dict().get('lon', 0.0))
        }
        
        order_ref.set(order_data)
        return jsonify({"success": True, "orderId": order_id}), 200
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@order_bp.route('/accept-donation', methods=['POST'])
def accept_donation():
    try:
        data = request.json
        order_id = data.get('orderId')
        farmer_email = data.get('farmerEmail')
        
        order_ref = db.collection('orders').document(order_id)
        
        @firestore.transactional
        def atomic_accept(txn):
            doc = order_ref.get(transaction=txn)
            if not doc.exists:
                raise Exception("Order not found")
            order = doc.to_dict()
            
            if order.get('status') != 'PENDING_FARMER_APPROVAL':
                raise Exception("Order is not pending approval")
            if order.get('farmerEmail') != farmer_email:
                raise Exception("Unauthorized")
                
            item_id = order.get('itemId')
            req_qty = float(order.get('weightKg', 0))
            
            inv_ref = db.collection('inventory').document(item_id)
            inv_doc = inv_ref.get(transaction=txn)
            if not inv_doc.exists:
                raise Exception("Inventory item not found")
                
            avail_kg = float(inv_doc.to_dict().get('availableKg', 0))
            if avail_kg < req_qty:
                raise Exception("Not enough inventory available")
                
            new_avail = avail_kg - req_qty
            reserved = float(inv_doc.to_dict().get('reservedQuantity', 0)) + req_qty
            inv_updates = {'availableKg': new_avail, 'reservedQuantity': reserved}
            if new_avail < float(inv_doc.to_dict().get('moq', 0)):
                inv_updates['moq'] = 0.0
                
            txn.update(inv_ref, inv_updates)
            txn.update(order_ref, {"status": "WAITING_FOR_TRANSPORT_SELECTION"})
            return order.get('chatId')
            
        transaction = db.transaction()
        chat_id = atomic_accept(transaction)
        
        if chat_id:
            ts = int(time.time() * 1000)
            db.collection('chats').document(chat_id).set({
                "lastMessage": "Farmer Accepted Donation",
                "timestamp": ts
            }, merge=True)
            db.collection('chats').document(chat_id).collection('messages').document(order_id).update({
                "status": "WAITING_FOR_TRANSPORT_SELECTION",
                "event": "FARMER_ACCEPTED",
                "timestamp": ts
            })
            
        return jsonify({"success": True}), 200
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@order_bp.route('/reject-donation', methods=['POST'])
def reject_donation():
    try:
        data = request.json
        order_id = data.get('orderId')
        farmer_email = data.get('farmerEmail')
        
        order_ref = db.collection('orders').document(order_id)
        doc = order_ref.get()
        if not doc.exists:
            return jsonify({"success": False, "error": "Order not found"}), 404
            
        order = doc.to_dict()
        if order.get('status') != 'PENDING_FARMER_APPROVAL':
            return jsonify({"success": False, "error": "Order is not pending approval"}), 400
        if order.get('farmerEmail') != farmer_email:
            return jsonify({"success": False, "error": "Unauthorized"}), 403
            
        order_ref.update({"status": "REJECTED"})
        
        chat_id = order.get('chatId')
        if chat_id:
            ts = int(time.time() * 1000)
            db.collection('chats').document(chat_id).set({
                "lastMessage": "Farmer Rejected Donation",
                "timestamp": ts
            }, merge=True)
            db.collection('chats').document(chat_id).collection('messages').document(order_id).update({
                "status": "REJECTED",
                "event": "FARMER_REJECTED",
                "timestamp": ts
            })
            
        return jsonify({"success": True}), 200
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@order_bp.route('/confirm-donation-logistics', methods=['POST'])
def confirm_donation_logistics():
    try:
        data = request.json
        order_id = data.get('orderId')
        ngo_email = data.get('ngoEmail')
        vehicle_type = data.get('vehicleType', 'Self Pickup')
        transport_fare = float(data.get('transportFare', 0.0))
        drop_address = data.get('dropAddress', '')
        drop_lat = float(data.get('dropLat', 0.0))
        drop_lon = float(data.get('dropLon', 0.0))
        distance_km = float(data.get('distanceKm', 0.0))
        base_fare = float(data.get('baseFare', 0.0))
        per_km_rate = float(data.get('perKmRate', 0.0))
        weight_charge = float(data.get('weightCharge', 0.0))
        capacity = data.get('capacity', '')
        
        order_ref = db.collection('orders').document(order_id)
        doc = order_ref.get()
        if not doc.exists:
            return jsonify({"success": False, "error": "Order not found"}), 404
            
        order = doc.to_dict()
        if order.get('status') not in ['PENDING_NGO_LOGISTICS_SELECTION', 'WAITING_FOR_TRANSPORT_SELECTION']:
            return jsonify({"success": False, "error": "Order is not awaiting logistics"}), 400
        if order.get('userEmail') != ngo_email:
            return jsonify({"success": False, "error": "Unauthorized"}), 403
            
        is_self_pickup = vehicle_type in ['Self Pickup', 'Self Service', 'Self-Service']
        
        import random
        
        if is_self_pickup:
            # Self Service: Immediate OTP, no wallet deduction
            pickup_otp = str(random.randint(1000, 9999))
            updates = {
                "vehicleType": vehicle_type,
                "transportFare": 0.0,
                "totalPaid": 0.0,
                "dropAddress": drop_address,
                "dropLat": drop_lat,
                "dropLon": drop_lon,
                "distanceKm": distance_km,
                "pickupOtp": pickup_otp,
                "dropOtp": None,
                "status": "READY_FOR_PICKUP"
            }
            order_ref.update(updates)
            
            chat_id = order.get('chatId')
            if chat_id:
                import time
                ts = int(time.time() * 1000)
                db.collection('chats').document(chat_id).set({
                    "lastMessage": "Self Service Transport Selected",
                    "timestamp": ts
                }, merge=True)
                db.collection('chats').document(chat_id).collection('messages').document(order_id).update({
                    "status": "READY_FOR_PICKUP",
                    "vehicleType": vehicle_type,
                    "transportCost": 0.0,
                    "totalPrice": 0.0,
                    "dropAddress": drop_address,
                    "pickupOtp": pickup_otp,
                    "timestamp": ts
                })
            return jsonify({"success": True, "status": "READY_FOR_PICKUP"}), 200

        else:
            # GroWise Delivery: Requires Invoice & Wallet Payment Later
            updates = {
                "vehicleType": vehicle_type,
                "transportFare": transport_fare,
                "totalPaid": transport_fare,
                "dropAddress": drop_address,
                "dropLat": drop_lat,
                "dropLon": drop_lon,
                "distanceKm": distance_km,
                "status": "INVOICED"
            }
            order_ref.update(updates)
            
            chat_id = order.get('chatId')
            if chat_id:
                import time
                ts = int(time.time() * 1000)
                db.collection('chats').document(chat_id).set({
                    "lastMessage": "Donation Delivery Invoice Generated",
                    "timestamp": ts
                }, merge=True)
                
                # Update the main message to INVOICE_CARD
                db.collection('chats').document(chat_id).collection('messages').document(order_id).update({
                    "type": "INVOICE_CARD",
                    "status": "PENDING",
                    "vehicleType": vehicle_type,
                    "transportCost": transport_fare,
                    "totalPrice": transport_fare,
                    "dropAddress": drop_address,
                    "dealType": "DONATION",
                    "timestamp": ts,
                    "distanceKm": distance_km,
                    "baseFare": base_fare,
                    "perKmRate": per_km_rate,
                    "weightCharge": weight_charge,
                    "capacity": capacity
                })
            return jsonify({"success": True, "status": "INVOICED"}), 200

    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@order_bp.route('/pay-donation-invoice', methods=['POST'])
def pay_donation_invoice():
    try:
        data = request.json
        order_id = data.get('orderId')
        ngo_email = data.get('ngoEmail')
        transport_fare = float(data.get('transportFare', 0.0))
        
        order_ref = db.collection('orders').document(order_id)
        doc = order_ref.get()
        if not doc.exists:
            return jsonify({"success": False, "error": "Order not found"}), 404
            
        order = doc.to_dict()
        if order.get('status') != 'INVOICED':
            return jsonify({"success": False, "error": "Order is not invoiced"}), 400
            
        wallet_ref = db.collection('wallets').document(ngo_email)
        wallet_doc = wallet_ref.get()
        if not wallet_doc.exists or float(wallet_doc.to_dict().get('balance', 0)) < transport_fare:
            return jsonify({"success": False, "error": "Insufficient wallet balance"}), 400
            
        import time
        import random
        pickup_otp = str(random.randint(1000, 9999))
        drop_otp = str(random.randint(1000, 9999))
        
        @firestore.transactional
        def atomic_wallet_payment(txn):
            w_snap = wallet_ref.get(transaction=txn)
            bal = float(w_snap.to_dict().get('balance', 0))
            if bal < transport_fare:
                raise Exception("Insufficient wallet balance during transaction")
            txn.update(wallet_ref, {"balance": bal - transport_fare})
            
            tx_ref = db.collection('transactions').document()
            txn.set(tx_ref, {
                "email": ngo_email, "type": "ESCROW_LOCK", "title": f"Transport Fare Escrow (Donation)",
                "amount": transport_fare, "isCredit": False, "timestamp": int(time.time() * 1000), "orderId": order_id
            })
            return True
            
        transaction = db.transaction()
        atomic_wallet_payment(transaction)
        
        updates = {
            "pickupOtp": pickup_otp,
            "dropOtp": drop_otp,
            "status": "PENDING_DRIVER"
        }
        order_ref.update(updates)
        
        chat_id = order.get('chatId')
        if chat_id:
            ts = int(time.time() * 1000)
            db.collection('chats').document(chat_id).set({
                "lastMessage": "Payment Secured. Order Placed.",
                "timestamp": ts
            }, merge=True)
            
            # Transition the main message to RECEIPT_CARD
            db.collection('chats').document(chat_id).collection('messages').document(order_id).update({
                "type": "RECEIPT_CARD",
                "status": "ORDER_CREATED",
                "pickupOtp": pickup_otp,
                "dropOtp": drop_otp,
                "timestamp": ts
            })
            
        return jsonify({"success": True}), 200
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500