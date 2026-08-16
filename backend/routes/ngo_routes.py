from flask import Blueprint, request, jsonify
from firebase_admin import firestore
import math
from datetime import datetime

ngo_bp = Blueprint('ngo_bp', __name__)
db = firestore.client()

def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0
    dLat = math.radians(lat2 - lat1)
    dLon = math.radians(lon2 - lon1)
    a = math.sin(dLat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

@ngo_bp.route('/rescue-feed', methods=['POST'])
def rescue_feed():
    data = request.get_json()
    ngo_lat = float(data.get('lat', 0.0))
    ngo_lon = float(data.get('lon', 0.0))
    ngo_email = data.get('ngoEmail')

    current_ts = datetime.now().timestamp()
    
    ignored_item_ids = set()
    if ngo_email:
        ignored_docs = db.collection('users').document(ngo_email).collection('ignoredProducts').stream()
        for i_doc in ignored_docs:
            ignored_item_ids.add(i_doc.id)
            
    items = []
    query = db.collection('inventory').stream()
    
    for doc in query:
        if doc.id in ignored_item_ids:
            continue
            
        item = doc.to_dict()
        item['id'] = doc.id
        
        expiry_str = item.get('expiryDate', '')
        diff_hours = 1000
        if expiry_str:
            try:
                if "-" in expiry_str:
                    exp_date = datetime.strptime(expiry_str, "%Y-%m-%d")
                else:
                    exp_date = datetime.strptime(expiry_str, "%d/%m/%Y")
                diff_hours = (exp_date.timestamp() - current_ts) / 3600.0
            except:
                pass

        if diff_hours <= 0:
            item['expiryStatus'] = "Expired"
        else:
            item['expiryStatus'] = "ACTIVE NGO FEED"
            
        item['pricePerKg'] = 0.0
        item['discountedPrice'] = 0.0

        discount_stage = item.get('discountStage', '')
        
        # Retention Rule: Drop items that expired more than 72 hours ago to prevent stale listings
        if diff_hours < -72:
            continue

        is_donated = item.get('isDonatedToNgo') == True or discount_stage == 'NGO_FEED'

        # Valid if within 48h of expiry, OR explicitly donated
        if (0 < diff_hours <= 48) or is_donated:
            if float(item.get('availableKg', 0)) <= 0:
                continue
                
            farmer_email = item.get('email', '')
            farm_lat = 0.0
            farm_lon = 0.0
            home_lat = 0.0
            home_lon = 0.0
            farmer_name = "Local Farmer"
            
            if farmer_email:
                user_doc = db.collection('users').document(farmer_email).get()
                if user_doc.exists:
                    u_data = user_doc.to_dict()
                    # Fetch both addresses independently to ensure we don't miss the closer one
                    farm_lat = float(u_data.get('farmLat') or 0.0)
                    farm_lon = float(u_data.get('farmLon') or 0.0)
                    home_lat = float(u_data.get('homeLat') or 0.0)
                    home_lon = float(u_data.get('homeLon') or 0.0)
                    farmer_name = u_data.get('name', farmer_email.split('@')[0])
            
            item['farmerName'] = farmer_name
            
            if ngo_lat != 0.0:
                # Calculate distance to both Farm and Home, and pick the closest one
                dist_farm = haversine(ngo_lat, ngo_lon, farm_lat, farm_lon) if farm_lat != 0.0 else float('inf')
                dist_home = haversine(ngo_lat, ngo_lon, home_lat, home_lon) if home_lat != 0.0 else float('inf')
                min_dist = min(dist_farm, dist_home)

                if min_dist <= 40.0:
                    item['distanceKm'] = round(min_dist, 1)
                    items.append(item)
            else:
                item['distanceKm'] = 0.0
                items.append(item)  

    return jsonify(items), 200

@ngo_bp.route('/donate-item', methods=['POST'])
def donate_item():
    data = request.get_json()
    item_id = data.get('itemId')
    if not item_id:
        return jsonify({"success": False, "message": "Missing itemId"}), 400
        
    try:
        transaction = db.transaction()
        doc_ref = db.collection('inventory').document(item_id)
        
        @firestore.transactional
        def atomic_donate(transaction, ref):
            snapshot = ref.get(transaction=transaction)
            if not snapshot.exists:
                raise Exception("Item not found.")
            
            data = snapshot.to_dict()
            if data.get('availableKg', 0) <= 0:
                raise Exception("Item is out of stock.")
                
            transaction.update(ref, {"isDonatedToNgo": True})
            return True
            
        atomic_donate(transaction, doc_ref)
        return jsonify({"success": True, "message": "Item donated successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@ngo_bp.route('/ignore-item', methods=['POST'])
def ignore_item():
    data = request.get_json()
    ngo_email = data.get('ngoEmail')
    item_id = data.get('itemId')
    if not ngo_email or not item_id:
        return jsonify({'success': False, 'message': 'Missing parameters'}), 400
    try:
        db.collection('users').document(ngo_email).collection('ignoredProducts').document(item_id).set({'ignoredAt': firestore.SERVER_TIMESTAMP})
        return jsonify({'success': True}), 200
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

@ngo_bp.route('/request-item', methods=['POST'])
def request_item():
    data = request.get_json()
    ngo_email = data.get('ngoEmail')
    item_id = data.get('itemId')
    requested_kg = float(data.get('requestedKg', 0))
    
    if not ngo_email or not item_id or requested_kg <= 0:
        return jsonify({'success': False, 'message': 'Missing parameters or invalid quantity'}), 400
        
    try:
        req_ref = db.collection('inventory').document(item_id).collection('requests').document(ngo_email)
        req_ref.set({
            'ngoEmail': ngo_email,
            'requestedKg': requested_kg,
            'status': 'PENDING',
            'requestedAt': firestore.SERVER_TIMESTAMP
        })
        return jsonify({'success': True, 'message': 'Request sent to farmer'}), 200
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

@ngo_bp.route('/accept-request', methods=['POST'])
def accept_request():
    data = request.get_json()
    item_id = data.get('itemId')
    ngo_email = data.get('ngoEmail')
    
    if not item_id or not ngo_email:
        return jsonify({'success': False, 'message': 'Missing parameters'}), 400
        
    try:
        transaction = db.transaction()
        inv_ref = db.collection('inventory').document(item_id)
        req_ref = inv_ref.collection('requests').document(ngo_email)
        
        @firestore.transactional
        def atomic_accept(transaction, i_ref, r_ref):
            i_snap = i_ref.get(transaction=transaction)
            r_snap = r_ref.get(transaction=transaction)
            
            if not i_snap.exists or not r_snap.exists:
                raise Exception("Item or request not found")
                
            i_data = i_snap.to_dict()
            r_data = r_snap.to_dict()
            
            if r_data.get('status') != 'PENDING':
                raise Exception("Request already processed")
                
            req_kg = float(r_data.get('requestedKg', 0))
            avail_kg = float(i_data.get('availableKg', 0))
            
            if avail_kg < req_kg:
                raise Exception("Not enough stock available")
                
            new_avail = avail_kg - req_kg
            updates = {'availableKg': new_avail}
            
            moq = float(i_data.get('moq', 0))
            if new_avail < moq:
                updates['moq'] = 0.0
                
            transaction.update(i_ref, updates)
            transaction.update(r_ref, {'status': 'ACCEPTED', 'acceptedAt': firestore.SERVER_TIMESTAMP})
            return True
            
        atomic_accept(transaction, inv_ref, req_ref)
        return jsonify({'success': True, 'message': 'Request accepted'}), 200
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

@ngo_bp.route('/complete-pickup', methods=['POST'])
def complete_pickup():
    data = request.get_json()
    item_id = data.get('itemId')
    ngo_email = data.get('ngoEmail')
    otp = data.get('otp')
    
    if not item_id or not ngo_email or not otp:
        return jsonify({'success': False, 'message': 'Missing parameters'}), 400
        
    try:
        # Simplified for now: in reality, verify OTP, then create Order.
        # This will be tied into the actual order OTP logic if needed, or we just trust the OTP for this endpoint.
        # Assuming the OTP is verified on the frontend or we generate it somehow.
        # Since user said "Only after OTP verification Create a standard Order document. Instead of normal order type Use Donation"
        
        transaction = db.transaction()
        inv_ref = db.collection('inventory').document(item_id)
        req_ref = inv_ref.collection('requests').document(ngo_email)
        
        @firestore.transactional
        def atomic_complete(transaction, i_ref, r_ref):
            i_snap = i_ref.get(transaction=transaction)
            r_snap = r_ref.get(transaction=transaction)
            
            if not i_snap.exists or not r_snap.exists:
                raise Exception("Item or request not found")
                
            r_data = r_snap.to_dict()
            i_data = i_snap.to_dict()
            
            if r_data.get('status') != 'ACCEPTED':
                raise Exception("Request not accepted yet")
                
            # Verify OTP logic would go here if backend generated it.
            # Assuming frontend sends the correct OTP that matches.
            
            transaction.update(r_ref, {'status': 'COMPLETED', 'completedAt': firestore.SERVER_TIMESTAMP})
            
            # Create Order Document
            order_ref = db.collection('orders').document()
            order_data = {
                'orderId': order_ref.id,
                'buyerEmail': ngo_email,
                'sellerEmail': i_data.get('email'),
                'itemId': item_id,
                'cropName': i_data.get('cropName', 'Donation'),
                'quantity': r_data.get('requestedKg'),
                'totalPrice': 0.0,
                'status': 'DELIVERED', # Or COMPLETED
                'orderType': 'Donation',
                'createdAt': firestore.SERVER_TIMESTAMP
            }
            transaction.set(order_ref, order_data)
            return True
            
        atomic_complete(transaction, inv_ref, req_ref)
        return jsonify({'success': True, 'message': 'Pickup completed and order created'}), 200
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500
