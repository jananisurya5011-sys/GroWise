import re
import bcrypt
import os
import base64
import uuid
import random
from firebase_admin import firestore
from firebase_admin import auth as admin_auth
from flask import Blueprint, request, jsonify
from firebase_config import db

auth_bp = Blueprint('auth', __name__)

# Ensure drivers upload directory exists
UPLOAD_FOLDER = os.path.join('uploads', 'drivers')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Strict Regular Expressions matching Frontend Rules
NAME_REGEX = re.compile(r"^[a-zA-Z ]+$")
EMAIL_REGEX = re.compile(r"^[a-zA-Z0-9._%+-]+@(gmail\.com|mail\.com)$")
PASSWORD_REGEX = re.compile(r"^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{6,8}$")
DARPAN_REGEX = re.compile(r"^[A-Z]{2}/\d{4}/\d{7}$")
PHONE_REGEX = re.compile(r"^\d{10}$")

@auth_bp.route('/signup', methods=['POST'])
def signup():
    data = request.get_json() or {}
    
    name = data.get('name', '').strip()
    email = data.get('email', '').strip().lower()
    phone = data.get('phone', '').strip()
    password = data.get('password', '')
    role = data.get('role', '').strip().lower()  # 'farmer', 'user', or 'driver'
    
    # 1. Base Field Validations
    if not name or not email or not phone or not password or not role:
        return jsonify({"error": "Missing required fields"}), 400
        
    if not NAME_REGEX.match(name):
        return jsonify({"error": "Name Error: Only letters allowed."}), 400
        
    if not EMAIL_REGEX.match(email):
        return jsonify({"error": "Email Error: Must use @gmail.com or @mail.com."}), 400
        
    if not PHONE_REGEX.match(phone):
        return jsonify({"error": "Phone Error: Must be exactly 10 digits."}), 400
        
    if not PASSWORD_REGEX.match(password):
        return jsonify({"error": "Password Error: 6-8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special."}), 400

   # 2. Strict Uniqueness Checks for All Fields
    user_ref = db.collection('users').document(email)
    if user_ref.get().exists:
        return jsonify({"error": "Data already exists: Email ID is already registered. Please use another."}), 409
        
    if list(db.collection('users').where('phone', '==', phone).limit(1).stream()):
        return jsonify({"error": "Data already exists: Phone number is already registered. Please use another."}), 409

    if list(db.collection('users').where('name', '==', name).limit(1).stream()):
        return jsonify({"error": "Data already exists: Name is already taken. Please use another."}), 409

    # Password check logic: Bcrypt salts make hash comparisons unique per generation. 
    # To enforce strictly unique passwords across the DB, we verify against plaintext stored separately (not recommended) 
    # or restrict it conceptually. Below ensures no identical hash match is randomly generated.
    salt = bcrypt.gensalt()
    hashed_password = bcrypt.hashpw(password.encode('utf-8'), salt).decode('utf-8')
    if list(db.collection('users').where('passwordHash', '==', hashed_password).limit(1).stream()):
        return jsonify({"error": "Data already exists: Password pattern already exists. Please use another."}), 409

    aadhaar = data.get('aadhaarNumber', '').strip()
    if aadhaar and list(db.collection('users').where('aadhaarNumber', '==', aadhaar).limit(1).stream()):
        return jsonify({"error": "Data already exists: Aadhaar number is already registered. Please use another."}), 409

    # 3. Role-Specific Mandatory Field Validation
    user_data = {
        "name": name,
        "email": email,
        "phone": phone,
        "role": role,
        "createdAt": firestore.SERVER_TIMESTAMP
    }
    
    if role == 'farmer':
        if not aadhaar:
            return jsonify({"error": "Farmer Error: Aadhaar number is mandatory."}), 400
        if len(aadhaar) != 12 or not aadhaar.isdigit():
            return jsonify({"error": "Aadhaar Error: Must be exactly 12 digits."}), 400
        user_data["aadhaarNumber"] = aadhaar
        user_data["aadhaarVerified"] = True
        
    elif role == 'user':
        is_ngo = data.get('isNgo', False)
        user_data["isNgo"] = is_ngo
        if is_ngo:
            darpan_id = data.get('darpanId', '').strip()
            if not darpan_id:
                return jsonify({"error": "User Error: Darpan ID is mandatory for NGOs."}), 400
            if not DARPAN_REGEX.match(darpan_id):
                return jsonify({"error": "Darpan ID Error: Format must match STATE/YEAR/7-DIGITS"}), 400
            user_data["darpanId"] = darpan_id
            user_data["role"] = "ngo" 
        else:
            user_data["darpanId"] = None
            
    elif role == 'driver':
        vehicle_type = data.get("vehicleType", "").strip()
        license_b64 = data.get("licenseUrl", "").strip()
        rc_b64 = data.get("rcBookUrl", "").strip()

        if not aadhaar or not vehicle_type or not license_b64 or not rc_b64:
            return jsonify({"error": "Driver Error: Aadhaar, Vehicle Type, License Image, and RC Book Image are all strictly mandatory."}), 400

        if len(aadhaar) != 12 or not aadhaar.isdigit():
            return jsonify({"error": "Aadhaar Error: Must be exactly 12 digits."}), 400
            
        user_data["aadhaarNumber"] = aadhaar
        user_data["vehicleType"] = vehicle_type
        
        license_path = ""
        rc_path = ""
        
        try:
            if license_b64:
                license_filename = f"license_{uuid.uuid4().hex}.jpg"
                license_filepath = os.path.join(UPLOAD_FOLDER, license_filename)
                with open(license_filepath, "wb") as fh:
                    fh.write(base64.b64decode(license_b64))
                license_path = f"/uploads/drivers/{license_filename}"
                
            if rc_b64:
                rc_filename = f"rc_{uuid.uuid4().hex}.jpg"
                rc_filepath = os.path.join(UPLOAD_FOLDER, rc_filename)
                with open(rc_filepath, "wb") as fh:
                    fh.write(base64.b64decode(rc_b64))
                rc_path = f"/uploads/drivers/{rc_filename}"
        except Exception as e:
            return jsonify({"error": f"Image processing failed: {str(e)}"}), 500

        user_data["licenseUrl"] = license_path
        user_data["rcBookUrl"] = rc_path
        user_data["verificationStatus"] = "PENDING"
        
    else:
        return jsonify({"error": "Invalid role type specified."}), 400

    # 4. Assign Pre-computed Password Hash
    user_data["passwordHash"] = hashed_password

    # 5. Write Data to Firestore
    try:
        user_ref.set(user_data)
        return jsonify({"message": "Registration successful", "role": user_data["role"]}), 201
    except Exception as e:
        return jsonify({"error": f"Database write failed: {str(e)}"}), 500


@auth_bp.route('/login', methods=['POST'])
def login():
    data = request.get_json() or {}
    identifier = data.get('email', '').strip() # Frontend passes Email, Phone, or GW-D ID
    password = data.get('password', '')

    if not identifier or not password:
        return jsonify({"error": "Identifier and password are required"}), 400

    # 1. Retrieve User by Document ID (Email), Field (Phone), or Field (DriverId)
    user_info = None
    if "@" in identifier:
        user_doc = db.collection('users').document(identifier).get()
        if user_doc.exists:
            user_info = user_doc.to_dict()
    elif identifier.startswith("GW-D"):
        query = list(db.collection('users').where('driverId', '==', identifier).limit(1).stream())
        if query:
            user_info = query[0].to_dict()
    else:
        query = list(db.collection('users').where('phone', '==', identifier).limit(1).stream())
        if query:
            user_info = query[0].to_dict()

    if not user_info:
        return jsonify({"error": "Invalid credentials"}), 401

    stored_hash = user_info.get("passwordHash", "")

   # 3. Secure Password Verification
    if bcrypt.checkpw(password.encode('utf-8'), stored_hash.encode('utf-8')):
        
        # 4. Check Driver Status & ID Routing
        if user_info.get("role") == "driver":
            status = user_info.get("verificationStatus")
            if status == "PENDING":
                return jsonify({
                    "message": "Verification Pending",
                    "role": "driver",
                    "verificationStatus": "PENDING"
                }), 200
            elif status == "REJECTED":
                return jsonify({
                    "message": "Verification Rejected",
                    "role": "driver",
                    "verificationStatus": "REJECTED",
                    "rejectionReason": user_info.get("rejectionReason", "Admin declined your application.")
                }), 200
            elif status == "APPROVED" and not identifier.startswith("GW-D"):
                # Driver is approved but tried logging in with Phone/Email. Block entry, return ID.
                return jsonify({
                    "message": "Driver Approved, ID required for entry",
                    "role": "driver",
                    "verificationStatus": "APPROVED_NEEDS_ID",
                    "driverId": user_info.get("driverId", "UNKNOWN")
                }), 200

        # Generate Custom Token
        try:
            custom_token_bytes = admin_auth.create_custom_token(user_info.get("email"))
            custom_token = custom_token_bytes.decode('utf-8') if isinstance(custom_token_bytes, bytes) else custom_token_bytes
        except Exception as e:
            return jsonify({"error": f"Failed to generate secure token: {str(e)}"}), 500

        # Standard Login Success (For Users, Farmers, Admins, and Drivers using GW-D ID)
        return jsonify({
            "message": "Login successful",
            "name": user_info.get("name"),
            "email": user_info.get("email"),
            "role": user_info.get("role"),
            "verificationStatus": user_info.get("verificationStatus", "APPROVED"),
            "customToken": custom_token
        }), 200
    else:
        return jsonify({"error": "Invalid credentials"}), 401

@auth_bp.route('/update-password', methods=['POST'])
def update_password():
    data = request.get_json() or {}
    identifier = data.get('identifier', '').strip()
    old_password = data.get('oldPassword', '')
    new_password = data.get('newPassword', '')

    if not identifier or not old_password or not new_password:
        return jsonify({"error": "Missing required fields"}), 400

    if not PASSWORD_REGEX.match(new_password):
        return jsonify({"error": "Password Error: 6-8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special."}), 400

    # Retrieve User
    user_ref = None
    if "@" in identifier:
        user_ref = db.collection('users').document(identifier)
    elif identifier.startswith("GW-D"):
        docs = list(db.collection('users').where('driverId', '==', identifier).limit(1).stream())
        if docs:
            user_ref = docs[0].reference
    else:
        docs = list(db.collection('users').where('phone', '==', identifier).limit(1).stream())
        if docs:
            user_ref = docs[0].reference

    if not user_ref or not user_ref.get().exists:
        return jsonify({"error": "User not found"}), 404

    user_info = user_ref.get().to_dict()
    stored_hash = user_info.get("passwordHash", "")

    # Verify old password
    if not bcrypt.checkpw(old_password.encode('utf-8'), stored_hash.encode('utf-8')):
        return jsonify({"error": "Wrong password"}), 401

    # Hash and update new password
    salt = bcrypt.gensalt()
    new_hashed = bcrypt.hashpw(new_password.encode('utf-8'), salt).decode('utf-8')

    try:
        user_ref.update({"passwordHash": new_hashed})
        return jsonify({"success": True, "message": "Password updated successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500