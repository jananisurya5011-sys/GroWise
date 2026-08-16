import os
import uuid
import math
from flask import Blueprint, request, jsonify, send_from_directory
from werkzeug.utils import secure_filename
from firebase_admin import firestore

rental_bp = Blueprint('rental', __name__)
db = firestore.client()

# Fix: Use absolute path to backend/uploads instead of backend/routes/uploads
BASE_DIR = os.path.abspath(os.path.dirname(os.path.dirname(__file__)))
UPLOAD_FOLDER = os.path.join(BASE_DIR, 'uploads', 'rental_photos')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0 # Earth radius in kilometers
    dLat = math.radians(lat2 - lat1)
    dLon = math.radians(lon2 - lon1)
    a = math.sin(dLat / 2) * math.sin(dLat / 2) + \
        math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * \
        math.sin(dLon / 2) * math.sin(dLon / 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

@rental_bp.route('/uploads/rental_photos/<filename>', methods=['GET'])
def serve_image(filename):
    return send_from_directory(UPLOAD_FOLDER, filename)

@rental_bp.route('/add', methods=['POST'])
def add_rental():
    if request.content_type and 'multipart/form-data' in request.content_type:
        data = request.form
        file = request.files.get('image')
    else:
        return jsonify({"success": False, "message": "Invalid request format. Multipart required."}), 400

    email = data.get('email', '').strip()
    if not email:
        return jsonify({"success": False, "message": "Email is required."}), 400

    doc_ref = db.collection('rentals').document()

    image_url = ""
    if file and file.filename != '':
        filename = secure_filename(f"{doc_ref.id}_{uuid.uuid4().hex[:8]}_{file.filename}")
        filepath = os.path.join(UPLOAD_FOLDER, filename)
        file.save(filepath)
        image_url = f"/api/rental/uploads/rental_photos/{filename}"

    # Fetch user details to embed profile image & verified phone automatically
    owner_name = "Farmer"
    owner_profile_url = ""
    owner_phone = ""
    city = ""
    try:
        user_doc = db.collection('users').document(email.lower()).get()
        if user_doc.exists:
            user_data = user_doc.to_dict()
            owner_name = user_data.get('name', 'Farmer')
            owner_profile_url = user_data.get('profileImageUrl', '')
            owner_phone = user_data.get('phone', '')
            city = user_data.get('farmAddress', user_data.get('homeAddress', 'Unknown Location'))
    except Exception as e:
        print(f"Error fetching user profile: {e}")

    new_rental = {
        "id": doc_ref.id,
        "email": email,
        "equipmentName": data.get('equipmentName', 'Equipment'),
        "category": data.get('category', 'Tractors'),
        "ratePerHour": float(data.get('ratePerHour', 0.0)),
        "ratePerDay": float(data.get('ratePerDay', 0.0)),
        "ownerPhone": owner_phone,
        "city": city,
        "latitude": float(data.get('latitude', 0.0)),
        "longitude": float(data.get('longitude', 0.0)),
        "imageUrl": image_url,
        "ownerName": owner_name,
        "ownerProfileUrl": owner_profile_url,
        "isVerified": True,
        "isLocked": False,
        "timestamp": firestore.SERVER_TIMESTAMP
    }

    doc_ref.set(new_rental)
    return jsonify({"success": True, "message": "Equipment posted successfully."}), 200

@rental_bp.route('/search', methods=['GET', 'POST'])
def search_rentals():
    try:
        req_lat = float(request.args.get('latitude', 0.0))
        req_lon = float(request.args.get('longitude', 0.0))
        category_filter = request.args.get('category', 'All')
        sort_by = request.args.get('sortBy', 'Nearest')

        rentals_ref = db.collection('rentals').stream()
        results = []

        for doc in rentals_ref:
            item = doc.to_dict()
            if not item.get('id'):
                item['id'] = doc.id
            
            if 'isLocked' not in item:
                item['isLocked'] = False
            
            if category_filter != 'All' and item.get('category') != category_filter:
                continue

            item_lat = float(item.get('latitude', 0.0))
            item_lon = float(item.get('longitude', 0.0))
            
            if req_lat == 0.0 and req_lon == 0.0:
                item['distanceKm'] = 0.0
            else:
                dist = haversine(req_lat, req_lon, item_lat, item_lon)
                item['distanceKm'] = round(dist, 1)

            results.append(item)

        if sort_by == 'Price: Low to High':
            results.sort(key=lambda x: float(x.get('ratePerDay', 0.0)))
        elif req_lat != 0.0 or req_lon != 0.0:
            results.sort(key=lambda x: x.get('distanceKm', 0.0))

        return jsonify(results), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@rental_bp.route('/my-items', methods=['POST'])
def fetch_my_items():
    data = request.get_json()
    email = data.get('email')
    if not email:
        return jsonify([]), 400
    
    try:
        docs = db.collection('rentals').where('email', '==', email).stream()
        results = []
        for doc in docs:
            item = doc.to_dict()
            if 'isLocked' not in item:
                item['isLocked'] = False
            results.append(item)
        return jsonify(results), 200
    except Exception as e:
        return jsonify([]), 500

@rental_bp.route('/delete', methods=['POST'])
def delete_rental():
    data = request.get_json()
    item_id = data.get('itemId')
    if not item_id:
        return jsonify({"success": False, "message": "Item ID missing."}), 400
    
    try:
        doc_ref = db.collection('rentals').document(item_id)
        doc = doc_ref.get()
        if doc.exists:
            image_url = doc.to_dict().get('imageUrl', '')
            if image_url:
                filename = image_url.split('/')[-1]
                filepath = os.path.join(UPLOAD_FOLDER, filename)
                if os.path.exists(filepath):
                    os.remove(filepath)
            doc_ref.delete()
        return jsonify({"success": True, "message": "Equipment deleted successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@rental_bp.route('/toggle-lock', methods=['POST'])
def toggle_lock():
    data = request.get_json()
    item_id = data.get('itemId')
    is_locked = data.get('isLocked')
    
    if not item_id or is_locked is None:
        return jsonify({"success": False, "message": "Item ID and lock status required."}), 400
        
    try:
        db.collection('rentals').document(item_id).update({"isLocked": is_locked})
        return jsonify({"success": True, "message": "Equipment status updated successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500