import os
import json
import traceback
from flask import Blueprint, request, jsonify, send_from_directory
from werkzeug.utils import secure_filename
from firebase_config import db
from datetime import datetime
from google.cloud import firestore  # Added for SERVER_TIMESTAMP

profile_bp = Blueprint('profile', __name__)

@profile_bp.route('/fetch-details', methods=['GET', 'POST'])
def fetch_details():
    # Supports GET with query params or POST with JSON
    email = request.args.get('email')
    if not email and request.is_json:
        email = request.get_json().get('email')
        
    if not email:
        return jsonify({"error": "Email identifier is required"}), 400

    try:
        user_ref = db.collection('users').document(email.strip().lower())
        user_doc = user_ref.get()

        if not user_doc.exists:
            return jsonify({"error": "Profile not found"}), 404

        data = user_doc.to_dict()
        
        # Format the Member Since date (e.g., "Oct 2021")
        member_since = "New Member"
        if "createdAt" in data and data["createdAt"]:
            created_dt = data["createdAt"]
            # Handle both string dates and Firestore Datetime objects
            if isinstance(created_dt, datetime):
                member_since = created_dt.strftime("%b %Y")
            elif hasattr(created_dt, 'timestamp'): # Check for Firestore Datetime
                member_since = datetime.fromtimestamp(created_dt.timestamp()).strftime("%b %Y")

        # Package the secure response payload
        return jsonify({
            "name": data.get("name", "User"),
            "email": data.get("email", email),
            "role": data.get("role", "user"),
            "isNgo": data.get("isNgo", False),
            "member_since": member_since,
            "username": data.get("username", ""), 
            "primary_crop": data.get("primaryCrop", "Not Set"),
            "farm_address": data.get("farmAddress", ""),
            "home_address": data.get("homeAddress", ""),
            "is_same_address": data.get("isSameAddress", False),
            "soil_type": data.get("soilType", "Select Soil Type"),
            "total_acreage": data.get("totalAcreage", ""),
            "phone": data.get("phone", ""),
            "profile_image_url": data.get("profileImageUrl", ""),
            "farmLat": float(data.get("farmLat", 0.0)),
            "farmLon": float(data.get("farmLon", 0.0)),
            "homeLat": float(data.get("homeLat", 0.0)),
            "homeLon": float(data.get("homeLon", 0.0)),
            # Securely extracting only the last 4 digits for the frontend
            "aadhaar_masked": data.get("aadhaarNumber", "")[-4:] if len(data.get("aadhaarNumber", "")) >= 4 else "...",
            "darpan_masked": data.get("darpanId", "")[-4:] if data.get("darpanId") and len(data.get("darpanId", "")) >= 4 else "...",
            "addresses": data.get("addresses", []), # Infinite address list support
            # Driver-specific Document & Identity Integration
            "driverId": data.get("driverId", ""),
            "vehicleType": data.get("vehicleType", ""),
            "licenseUrl": data.get("licenseUrl", ""),
            "rcBookUrl": data.get("rcBookUrl", "")
        }), 200

    except Exception as e:
        print(f"Profile Fetch Error: {traceback.format_exc()}")
        return jsonify({"error": "Failed to retrieve profile data"}), 500


@profile_bp.route('/update-details', methods=['POST'])
def update_details():
    # Supports both multipart/form-data (for images) and application/json
    if request.content_type and request.content_type.startswith('multipart/form-data'):
        data = request.form
        file = request.files.get('profile_image')
    else:
        data = request.get_json() or {}
        file = None

    email = data.get('email')
    
    if not email:
        return jsonify({"error": "Email identifier is required"}), 400

    try:
        user_ref = db.collection('users').document(email.strip().lower())
        
        # Build update payload (ignoring read-only fields like email, role, aadhaar, darpanId)
        update_payload = {}
        
        # Standard String Fields
        standard_fields = [
            "primaryCrop", "farmAddress", "homeAddress", "soilType", "totalAcreage", 
            "username", "phone"
        ]
        for field in standard_fields:
            if field in data:
                update_payload[field] = data[field]

        # Float Coordinate Fields explicitly parsed
        coord_fields = ["farmLat", "farmLon", "homeLat", "homeLon"]
        for field in coord_fields:
            if field in data and data[field]:
                try:
                    update_payload[field] = float(data[field])
                except ValueError:
                    update_payload[field] = 0.0
                
        # Boolean Field Parsing
        if "isSameAddress" in data:
            val = data["isSameAddress"]
            update_payload["isSameAddress"] = str(val).lower() == 'true'

        # Parse infinite addresses array (sent as JSON string if multipart)
        if "addresses" in data:
            try:
                if isinstance(data["addresses"], str):
                    update_payload["addresses"] = json.loads(data["addresses"])
                else:
                    update_payload["addresses"] = data["addresses"]
            except Exception as e:
                print(f"Error parsing addresses JSON: {e}")

        # File Handling Logistics
        if file and file.filename:
            filename = secure_filename(file.filename)
            upload_folder = os.path.join(os.getcwd(), 'uploads', 'profiles')
            os.makedirs(upload_folder, exist_ok=True) # Creates directory safely if missing
            
            unique_filename = f"{email.split('@')[0]}_{int(datetime.now().timestamp())}_{filename}"
            filepath = os.path.join(upload_folder, unique_filename)
            file.save(filepath)
            
            update_payload["profileImageUrl"] = f"/api/profile/image/{unique_filename}"

        if not update_payload:
            return jsonify({"message": "No valid fields provided for update"}), 400

        user_ref.update(update_payload)
        return jsonify({"message": "Profile updated successfully", "success": True}), 200

    except Exception as e:
        print(f"Profile Update Error: {traceback.format_exc()}")
        return jsonify({"error": "Failed to update profile", "success": False}), 500


@profile_bp.route('/sync-telemetry', methods=['POST'])
def sync_telemetry():
    data = request.get_json()
    email = data.get('email')
    lat = data.get('latitude')
    lon = data.get('longitude')
    
    if not email or lat is None or lon is None:
        return jsonify({"error": "Missing telemetry data"}), 400

    try:
        user_ref = db.collection('users').document(email.strip().lower())
        # Silently update just the coordinate fields
        user_ref.update({
            "lastKnownLat": lat,
            "lastKnownLon": lon,
            "locationSyncedAt": firestore.SERVER_TIMESTAMP
        })
        return jsonify({"success": True}), 200

    except Exception as e:
        # If user doesn't exist yet or update fails, fail silently to not interrupt UI
        return jsonify({"error": str(e), "success": False}), 500
    
# NEW UPDATED CODE
@profile_bp.route('/image/<filename>', methods=['GET'])
def serve_image(filename):
    # Securely serves the uploaded profile images back to the Android Coil library
    upload_folder = os.path.join(os.getcwd(), 'uploads', 'profiles')
    return send_from_directory(upload_folder, filename)

@profile_bp.route('/toggle-favorite', methods=['POST'])
def toggle_favorite():
    data = request.get_json()
    email = data.get('email')
    farmer_email = data.get('farmerEmail')
    farmer_name = data.get('farmerName', '')
    profile_image_url = data.get('profileImageUrl', '')

    if not email or not farmer_email:
        return jsonify({"error": "Missing required fields"}), 400

    try:
        user_ref = db.collection('users').document(email.strip().lower())
        doc = user_ref.get()
        
        if not doc.exists:
            return jsonify({"error": "User not found"}), 404
            
        user_data = doc.to_dict()
        favorites = user_data.get('favoriteFarmers', [])
        
        # Check if farmer is already in the array
        existing_fav = next((f for f in favorites if f.get('farmerEmail') == farmer_email), None)
        
        if existing_fav:
            favorites.remove(existing_fav)
            user_ref.update({'favoriteFarmers': favorites})
            return jsonify({"message": "Removed from favorites", "isFavorite": False, "success": True}), 200
        else:
            new_fav = {
                "farmerEmail": farmer_email,
                "farmerName": farmer_name,
                "profileImageUrl": profile_image_url,
                "addedAt": int(datetime.now().timestamp() * 1000)
            }
            favorites.append(new_fav)
            user_ref.update({'favoriteFarmers': favorites})
            return jsonify({"message": "Added to favorites", "isFavorite": True, "success": True}), 200
            
    except Exception as e:
        print(f"Toggle Favorite Error: {e}")
        return jsonify({"error": str(e), "success": False}), 500

@profile_bp.route('/get-favorites', methods=['POST'])
def get_favorites():
    data = request.get_json()
    email = data.get('email')
    if not email:
        return jsonify({"error": "Email is required"}), 400
    try:
        doc = db.collection('users').document(email.strip().lower()).get()
        if doc.exists:
            favs = doc.to_dict().get('favoriteFarmers', [])
            return jsonify(favs), 200
        return jsonify([]), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500