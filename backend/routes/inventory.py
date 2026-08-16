import os
import uuid
from datetime import datetime, timedelta, timezone
from flask import Blueprint, request, jsonify, send_from_directory
from werkzeug.utils import secure_filename
from firebase_admin import firestore
import math

def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0 # Earth radius in kilometers
    dLat = math.radians(lat2 - lat1)
    dLon = math.radians(lon2 - lon1)
    a = math.sin(dLat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

inventory_bp = Blueprint('inventory', __name__)
db = firestore.client()

# Ensure product_photos folder exists inside uploads folder for all farmers
# Fix: Use absolute path to backend/uploads instead of backend/routes/uploads
BASE_DIR = os.path.abspath(os.path.dirname(os.path.dirname(__file__)))
UPLOAD_FOLDER = os.path.join(BASE_DIR, 'uploads', 'product_photos')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def parse_iso_date(date_str):
    try:
        if len(date_str) == 10:
            return datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=timezone.utc)
        return datetime.fromisoformat(date_str.replace('Z', '+00:00'))
    except Exception:
        return datetime.now(timezone.utc)

# Route to serve the images to the Android UI
@inventory_bp.route('/uploads/product_photos/<filename>', methods=['GET'])
def serve_image(filename):
    return send_from_directory(UPLOAD_FOLDER, filename)

@inventory_bp.route('/add', methods=['POST'])
def add_item():
    # Supports both multipart form-data (for photo uploads) and standard JSON
    if request.content_type and 'multipart/form-data' in request.content_type:
        data = request.form
        file = request.files.get('image')
    else:
        data = request.get_json() or {}
        file = None

    if not data or 'cropName' not in data:
        return jsonify({"success": False, "message": "Invalid stock data input parameters."}), 400
    
    farmer_email = data.get('email', '').strip()
    
    # FIX: Generating a unique ID that includes the farmer's email to prevent overwriting existing products!
    if farmer_email:
        safe_email = farmer_email.replace('@', '_at_').replace('.', '_')
        unique_id = f"{safe_email}_{uuid.uuid4().hex[:8]}"
        doc_ref = db.collection('inventory').document(unique_id)
    else:
        doc_ref = db.collection('inventory').document()
    
    available_kg = float(data.get('availableKg', 0.0))
    moq = float(data.get('moq', 0.0))
    if moq > available_kg and available_kg > 0:
        moq = available_kg

    # Handle Photo Storage directly into uploads/product_photos without reducing quality
    image_url = ""
    if file and file.filename != '':
        filename = secure_filename(f"{doc_ref.id}_{uuid.uuid4().hex[:8]}_{file.filename}")
        filepath = os.path.join(UPLOAD_FOLDER, filename)
        file.save(filepath)
        image_url = f"/api/inventory/uploads/product_photos/{filename}"

    new_item = {
        "id": doc_ref.id,
        "email": farmer_email,
        "category": data.get('category', 'Vegetables'),
        "cropName": data['cropName'],
        "pricePerKg": float(data.get('pricePerKg', 0.0)),
        "availableKg": available_kg,
        "moq": moq,
        "harvestDate": data.get('harvestDate', datetime.now(timezone.utc).strftime("%Y-%m-%d")),
        "expiryDate": data.get('expiryDate', (datetime.now(timezone.utc) + timedelta(days=7)).strftime("%Y-%m-%d")),
        "expiryStatus": "FRESH",
        "isDonatedToNgo": False,
        "imageUrl": image_url
    }
    
    doc_ref.set(new_item)
    return jsonify({"success": True, "message": "Inventory allocation logged successfully."}), 200

@inventory_bp.route('/fetch', methods=['GET', 'POST'])
def fetch_items():
    try:
        # Check if an email was sent in the request
        data = request.get_json(silent=True) or {}
        req_email = data.get('email')
        
        # Filter by email if provided, otherwise fetch all
        if req_email:
            items_ref = db.collection('inventory').where('email', '==', req_email).stream()
        else:
            items_ref = db.collection('inventory').stream()
            
        active_inventory = []
        now_utc = datetime.now(timezone.utc)

        for doc in items_ref:
            item = doc.to_dict()
            
            if 'id' not in item:
                item['id'] = doc.id
            if 'pricePerKg' not in item:
                item['pricePerKg'] = 0.0
            if 'availableKg' not in item:
                item['availableKg'] = 0.0
            if 'moq' not in item:
                item['moq'] = 0.0
            if 'cropName' not in item:
                item['cropName'] = "Unnamed Produce"
            if 'category' not in item:
                item['category'] = "Vegetables"
            if 'imageUrl' not in item:
                item['imageUrl'] = ""
                
            expiry_dt = parse_iso_date(item.get('expiryDate', ''))
            
            if now_utc >= expiry_dt:
                db.collection('inventory').document(item['id']).delete()
                continue
            
            time_left = expiry_dt - now_utc
            hours_left = time_left.total_seconds() / 3600.0
            base_price = float(item.get('pricePerKg', 0.0))
            
            if hours_left <= 0:
                item['expiryStatus'] = "Expired"
                item['discountedPrice'] = base_price
            elif item.get('isDonatedToNgo') == True or hours_left <= 48:
                item['expiryStatus'] = "ACTIVE NGO FEED"
                item['pricePerKg'] = 0.0
                item['discountedPrice'] = 0.0
            elif hours_left <= 72:
                item['discountedPrice'] = round(base_price * 0.50, 2)
                item['expiryStatus'] = "Clearance Sale"
            elif hours_left <= 94:
                item['discountedPrice'] = round(base_price * 0.70, 2)
                item['expiryStatus'] = "Near Expiry"
            else:
                item['discountedPrice'] = base_price
                item['expiryStatus'] = "Fresh Batch"

            if item.get('availableKg', 0.0) < item.get('moq', 0.0):
                item['moq'] = item.get('availableKg', 0.0)
                db.collection('inventory').document(item['id']).update({"moq": item['moq']})

            active_inventory.append(item)

        return jsonify(active_inventory), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

# FIX: Added global market fetch route to retrieve all active farmers' inventory
@inventory_bp.route('/market', methods=['GET', 'POST'])
def fetch_market():
    try:
        data = request.get_json(silent=True) or {}
        is_ngo = data.get('isNgo', False)
        ngo_email = data.get('ngoEmail', '')
        ngo_lat = float(data.get('lat', 0.0))
        ngo_lon = float(data.get('lon', 0.0))

        items_ref = db.collection('inventory').stream()
        active_inventory = []
        now_utc = datetime.now(timezone.utc)

        for doc in items_ref:
            item = doc.to_dict()
            
            if 'id' not in item:
                item['id'] = doc.id
            if 'pricePerKg' not in item:
                item['pricePerKg'] = 0.0
            if 'availableKg' not in item:
                item['availableKg'] = 0.0
            if 'moq' not in item:
                item['moq'] = 0.0
            if 'cropName' not in item:
                item['cropName'] = "Unnamed Produce"
            if 'category' not in item:
                item['category'] = "Vegetables"
            if 'imageUrl' not in item:
                item['imageUrl'] = ""
                
            expiry_dt = parse_iso_date(item.get('expiryDate', ''))
            
            if now_utc >= expiry_dt:
                db.collection('inventory').document(item['id']).delete()
                continue
            
            time_left = expiry_dt - now_utc
            hours_left = time_left.total_seconds() / 3600.0
            
            if item.get('isDonatedToNgo') == True or hours_left <= 48:
                continue

            base_price = float(item.get('pricePerKg', 0.0))
            
            if hours_left <= 0:
                item['expiryStatus'] = "Expired"
            elif hours_left <= 72:
                item['discountedPrice'] = round(base_price * 0.50, 2)
                item['expiryStatus'] = "Clearance Sale"
            elif hours_left <= 94:
                item['discountedPrice'] = round(base_price * 0.70, 2)
                item['expiryStatus'] = "Near Expiry"
            else:
                item['discountedPrice'] = base_price
                item['expiryStatus'] = "Fresh Batch"

            if item.get('availableKg', 0.0) < item.get('moq', 0.0):
                item['moq'] = item.get('availableKg', 0.0)
                db.collection('inventory').document(item['id']).update({"moq": item['moq']})

            is_donated = item.get('isDonatedToNgo', False)

            if is_ngo:
                rejected_by = item.get('rejectedBy', [])
                if ngo_email in rejected_by:
                    continue
                if not is_donated and hours_left > 48:
                    continue
            else:
                # Regular users should still see the item as long as stock > 0
                pass

            if item.get('availableKg', 0.0) <= 0:
                continue

            active_inventory.append(item)

        return jsonify(active_inventory), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500


@inventory_bp.route('/update', methods=['POST'])
def update_item():
    if request.content_type and 'multipart/form-data' in request.content_type:
        data = request.form
        file = request.files.get('image')
    else:
        data = request.get_json() or {}
        file = None

    item_id = data.get("itemId")
    if not item_id:
        return jsonify({"success": False, "message": "Item ID missing."}), 400
        
    try:
        doc_ref = db.collection('inventory').document(item_id)
        doc = doc_ref.get()
        if not doc.exists:
            return jsonify({"success": False, "message": "Item not found."}), 404
            
        current_data = doc.to_dict()
        updates = {}
        
        if file and file.filename != '':
            old_image_url = current_data.get('imageUrl', '')
            if old_image_url:
                old_filename = old_image_url.split('/')[-1]
                old_filepath = os.path.join(UPLOAD_FOLDER, old_filename)
                if os.path.exists(old_filepath):
                    try:
                        os.remove(old_filepath)
                    except Exception as e:
                        pass
            
            filename = secure_filename(f"{doc_ref.id}_{uuid.uuid4().hex[:8]}_{file.filename}")
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            file.save(filepath)
            updates['imageUrl'] = f"/api/inventory/uploads/product_photos/{filename}"
        
        if 'availableKg' in data and data['availableKg']:
            new_avail = float(data['availableKg'])
            updates['availableKg'] = new_avail
            current_moq = float(current_data.get('moq', 50.0))
            if 'moq' in data and data['moq']:
                current_moq = float(data['moq'])
            if current_moq > new_avail:
                updates['moq'] = new_avail
            elif 'moq' in data and data['moq']:
                updates['moq'] = current_moq
        elif 'moq' in data and data['moq']:
            new_moq = float(data['moq'])
            if new_moq > float(current_data.get('availableKg', 0.0)):
                new_moq = float(current_data.get('availableKg', 0.0))
            updates['moq'] = new_moq
            
        if 'pricePerKg' in data and data['pricePerKg']:
            updates['pricePerKg'] = float(data['pricePerKg'])
            
        if 'harvestDate' in data and data['harvestDate']:
            updates['harvestDate'] = data['harvestDate']
        if 'expiryDate' in data and data['expiryDate']:
            updates['expiryDate'] = data['expiryDate']
            
        doc_ref.update(updates)
        return jsonify({"success": True, "message": "Inventory updated successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@inventory_bp.route('/delete', methods=['POST'])
def delete_item():
    data = request.get_json()
    item_id = data.get("itemId")
    if not item_id:
        return jsonify({"success": False, "message": "Item ID missing."}), 400
    try:
        doc_ref = db.collection('inventory').document(item_id)
        doc = doc_ref.get()
        if doc.exists:
            image_url = doc.to_dict().get('imageUrl', '')
            if image_url:
                filename = image_url.split('/')[-1]
                filepath = os.path.join(UPLOAD_FOLDER, filename)
                if os.path.exists(filepath):
                    os.remove(filepath)
                    
        doc_ref.delete()
        return jsonify({"success": True, "message": "Item deleted successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@inventory_bp.route('/cron/clean-expired', methods=['POST', 'GET'])
def scheduled_cleanup():
    try:
        items_ref = db.collection('inventory').stream()
        now_utc = datetime.now(timezone.utc)
        deleted_count = 0
        
        for doc in items_ref:
            item = doc.to_dict()
            expiry_dt = parse_iso_date(item.get('expiryDate', ''))
            if now_utc >= expiry_dt:
                image_url = item.get('imageUrl', '')
                if image_url:
                    filename = image_url.split('/')[-1]
                    filepath = os.path.join(UPLOAD_FOLDER, filename)
                    if os.path.exists(filepath):
                        os.remove(filepath)
                db.collection('inventory').document(item['id']).delete()
                deleted_count += 1
                
        return jsonify({"success": True, "deleted_count": deleted_count, "message": f"Hourly cron sweep removed {deleted_count} expired records."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@inventory_bp.route('/donate-to-ngo', methods=['POST'])
def donate_to_ngo():
    data = request.get_json()
    item_id = data.get('itemId')
    
    if not item_id:
        return jsonify({"success": False, "message": "Item ID missing."}), 400
        
    try:
        transaction = db.transaction()
        doc_ref = db.collection('inventory').document(item_id)
        
        @firestore.transactional
        def atomic_donate(transaction, ref):
            snapshot = ref.get(transaction=transaction)
            if not snapshot.exists:
                raise Exception("Target inventory log track missing.")
            
            data = snapshot.to_dict()
            if data.get('availableKg', 0) <= 0:
                raise Exception("Item is out of stock.")
                
            transaction.update(ref, {"isDonatedToNgo": True})
            return True
            
        atomic_donate(transaction, doc_ref)
        return jsonify({"success": True, "message": "Rescue Ping routed successfully to nearby non-profit registries."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@inventory_bp.route('/reject-ngo', methods=['POST'])
def reject_ngo():
    data = request.get_json()
    item_id = data.get("itemId")
    ngo_email = data.get("ngoEmail")
    
    if not item_id or not ngo_email:
        return jsonify({"success": False, "message": "Missing required fields."}), 400
        
    try:
        doc_ref = db.collection('inventory').document(item_id)
        if doc_ref.get().exists:
            doc_ref.update({"rejectedBy": firestore.ArrayUnion([ngo_email])})
            return jsonify({"success": True, "message": "Item rejected and hidden from your feed."}), 200
        else:
            return jsonify({"success": False, "message": "Item not found."}), 404
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500
@inventory_bp.route('/cron/lifecycle', methods=['POST'])
def cron_lifecycle():
    try:
        from datetime import datetime
        import time
        query = db.collection('inventory').stream()
        current_ts = datetime.now().timestamp()
        batch = db.batch()
        updates_count = 0
        for doc in query:
            data = doc.to_dict()
            if data.get('isDonatedToNgo') or float(data.get('availableKg', 0)) <= 0:
                continue
            expiry_str = data.get('expiryDate', '')
            if not expiry_str: continue
            try:
                if '-' in expiry_str:
                    exp_date = datetime.strptime(expiry_str, '%Y-%m-%d')
                else:
                    exp_date = datetime.strptime(expiry_str, '%d/%m/%Y')
                diff_hours = (exp_date.timestamp() - current_ts) / 3600.0
            except: continue
            if diff_hours <= 0:
                if data.get('discountStage') != 'EXPIRED':
                    batch.update(doc.reference, {'discountStage': 'EXPIRED', 'isActive': False})
                    updates_count += 1
                continue
            new_stage = None
            new_price = None
            is_active = data.get('isActive', True)
            original_price = float(data.get('originalPrice') or data.get('pricePerKg', 0))
            if original_price == 0: continue
            if diff_hours <= 48:
                new_stage = 'NGO_FEED'
                # Keep active for Marketplace
            elif diff_hours <= 72:
                new_stage = '50_OFF'
                new_price = original_price * 0.50
            elif diff_hours <= 94:
                new_stage = 'NEAR_EXPIRY'
                new_price = original_price * 0.70
            current_stage = data.get('discountStage')
            update_dict = {}
            if new_stage and current_stage != new_stage:
                update_dict['discountStage'] = new_stage
                update_dict['isActive'] = is_active
                if new_price:
                    update_dict['pricePerKg'] = str(round(new_price, 2))
                    update_dict['originalPrice'] = str(original_price)
            if update_dict:
                batch.update(doc.reference, update_dict)
                updates_count += 1
                if updates_count >= 400:
                    batch.commit()
                    batch = db.batch()
                    updates_count = 0
                    time.sleep(1)
        if updates_count > 0: batch.commit()
        return jsonify({'success': True, 'message': 'Lifecycle rules applied'}), 200
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
