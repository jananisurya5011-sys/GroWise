import os
import json
import datetime
import traceback
import uuid
from flask import Blueprint, request, jsonify
from werkzeug.utils import secure_filename
from PIL import Image
from firebase_admin import firestore
from flask import send_from_directory

from ai_engine.gemini_vision import analyze_crop_image

crop_doctor_bp = Blueprint('crop_doctor', __name__)
db = firestore.client()

# Ensure dedicated crop uploads directories exist
LEGACY_UPLOAD_FOLDER = os.path.join('uploads', 'crop')
DIAGNOSIS_UPLOAD_FOLDER = os.path.join('uploads', 'diagnosis')
os.makedirs(LEGACY_UPLOAD_FOLDER, exist_ok=True)
os.makedirs(DIAGNOSIS_UPLOAD_FOLDER, exist_ok=True)

ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'webp'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

@crop_doctor_bp.route('/diagnose', methods=['POST', 'OPTIONS'])
def diagnose_crop():
    # Legacy endpoint, left intact for backward compatibility if needed by older apps.
    if request.method == 'OPTIONS':
        return jsonify({}), 200
        
    return jsonify({"success": False, "error": "Deprecated. Use /ai-diagnose"}), 400

@crop_doctor_bp.route('/ai-diagnose', methods=['POST', 'OPTIONS'])
def ai_diagnose_crop():
    if request.method == 'OPTIONS':
        return jsonify({}), 200
        
    if 'file' not in request.files:
        return jsonify({"success": False, "error": "No file stream part detected"}), 400
        
    file = request.files['file']
    if file.filename == '':
        return jsonify({"success": False, "error": "No selected file"}), 400
        
    if not allowed_file(file.filename):
        return jsonify({"success": False, "error": "Unsupported file type. Use JPG, PNG, or WEBP."}), 415

    target_language = request.form.get('language', 'en')

    try:
        # Load image into memory (do NOT save to disk yet)
        img = Image.open(file.stream)
        
        # Pass to the dedicated Gemini Vision service
        result_json = analyze_crop_image(img, language=target_language)
        
        # If Gemini retries exhausted and returned an error dict, pass it cleanly to frontend
        if isinstance(result_json, dict) and result_json.get("success") is False:
            return jsonify(result_json), 200
        
        # Check if it was identified as a non-plant
        if result_json.get("disease", "").lower() == "not a plant" or result_json.get("healthStatus", "").lower() == "not a plant":
             return jsonify({"success": False, "error": "It is not a plant or crop. Show me the crop or plant."}), 400

        return jsonify({
            "success": True,
            "data": result_json
        }), 200
        
    except Exception as e:
        traceback.print_exc()
        error_msg = str(e) if "API" in str(e) or "parse" in str(e) else "Internal AI Inference Error"
        return jsonify({"success": False, "error": error_msg}), 500


@crop_doctor_bp.route('/save-diagnosis', methods=['POST'])
def save_diagnosis():
    # Now receives multipart/form-data
    email = request.form.get('email')
    
    if not email:
        return jsonify({"success": False, "error": "Missing email"}), 400
        
    if 'file' not in request.files:
        return jsonify({"success": False, "error": "No image file provided"}), 400
        
    file = request.files['file']
    if file.filename == '':
        return jsonify({"success": False, "error": "Empty file"}), 400
        
    if not allowed_file(file.filename):
        return jsonify({"success": False, "error": "Unsupported file type"}), 415

    try:
        # Generate secure unique filename
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        unique_id = str(uuid.uuid4())[:8]
        ext = file.filename.rsplit('.', 1)[1].lower()
        secure_name = f"diagnosis_{timestamp}_{unique_id}.{ext}"
        file_path = os.path.join(DIAGNOSIS_UPLOAD_FOLDER, secure_name)
        
        # Save image ONCE physically
        file.save(file_path)
        
        diagnosis_type = request.form.get('diagnosisType', 'ai')
        language = request.form.get('language', 'en')
        
        # Extract flat fields
        disease = request.form.get('disease', 'Unknown')
        confidence = request.form.get('confidence', 0.0)
        remedy = request.form.get('remedy', '')
        
        try:
            confidence = float(confidence)
        except:
            confidence = 0.0

        # Try to parse the rich JSON details if present (AI Mode)
        details_str = request.form.get('details', '{}')
        try:
            details = json.loads(details_str)
        except:
            details = {}
            
        crop_name = details.get('cropName', 'Unknown')
        species = details.get('species', 'Unknown')
        health_status = details.get('healthStatus', 'Unknown')
        severity = details.get('severity', 'None')

        # Structure for Firestore (flattened top-level fields for fast UI rendering, full details nested)
        doc_ref = db.collection('users').document(email).collection('diagnostic_history').document()
        doc_ref.set({
            "diagnosisType": diagnosis_type,
            "language": language,
            "disease": disease,
            "cropName": crop_name,
            "species": species,
            "healthStatus": health_status,
            "confidence": confidence,
            "severity": severity,
            "remedy": remedy, # Fallback/Offline
            "details": details, # Full AI Gemini JSON
            "imagePath": secure_name, # Storing just the basename
            "date": datetime.datetime.utcnow().isoformat(),
            "createdAt": firestore.SERVER_TIMESTAMP
        })
        
        return jsonify({"success": True, "message": "Diagnosis Saved"}), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500


@crop_doctor_bp.route('/history', methods=['POST', 'OPTIONS'])
def fetch_history():
    if request.method == 'OPTIONS':
        return jsonify({}), 200
        
    data = request.get_json()
    email = data.get('email')
    
    if not email:
        return jsonify({"success": False, "error": "Missing email"}), 400
        
    try:
        history_ref = db.collection('users').document(email).collection('diagnostic_history').order_by('date', direction=firestore.Query.DESCENDING).limit(10)
        docs = history_ref.stream()
        
        history = []
        for doc in docs:
            doc_dict = doc.to_dict()
            doc_dict['id'] = doc.id
            history.append(doc_dict)
            
        return jsonify({"success": True, "history": history}), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500

@crop_doctor_bp.route('/history/<doc_id>', methods=['DELETE', 'OPTIONS'])
def delete_history(doc_id):
    if request.method == 'OPTIONS':
        return jsonify({}), 200
        
    email = request.args.get('email')
    if not email:
        return jsonify({"success": False, "error": "Missing email"}), 400
        
    try:
        doc_ref = db.collection('users').document(email).collection('diagnostic_history').document(doc_id)
        doc = doc_ref.get()
        if doc.exists:
            data = doc.to_dict()
            image_path = data.get('imagePath')
            if image_path:
                clean_filename = os.path.basename(image_path)
                diag_path = os.path.join(DIAGNOSIS_UPLOAD_FOLDER, clean_filename)
                legacy_path = os.path.join(LEGACY_UPLOAD_FOLDER, clean_filename)
                if os.path.exists(diag_path):
                    os.remove(diag_path)
                elif os.path.exists(legacy_path):
                    os.remove(legacy_path)
            
            doc_ref.delete()
        
        return jsonify({"success": True, "message": "Diagnosis deleted"}), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500


@crop_doctor_bp.route('/serve-image/<path:filename>', methods=['GET'])
def serve_image(filename):
    try:
        clean_filename = os.path.basename(filename)
        
        # 1. Search in New Diagnosis Folder
        diag_path = os.path.join(DIAGNOSIS_UPLOAD_FOLDER, clean_filename)
        if os.path.exists(diag_path):
            return send_from_directory(os.path.abspath(DIAGNOSIS_UPLOAD_FOLDER), clean_filename)
            
        # 2. Fallback to Legacy Crop Folder
        legacy_path = os.path.join(LEGACY_UPLOAD_FOLDER, clean_filename)
        if os.path.exists(legacy_path):
            return send_from_directory(os.path.abspath(LEGACY_UPLOAD_FOLDER), clean_filename)
            
        # 3. 404
        return jsonify({"success": False, "error": "Image not found"}), 404
        
    except Exception as e:
        return jsonify({"success": False, "error": "Image access error"}), 500