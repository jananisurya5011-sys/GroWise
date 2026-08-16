import os
import json
import datetime
import traceback
from flask import Blueprint, request, jsonify
from werkzeug.utils import secure_filename
from google import genai
from google.genai import errors
from PIL import Image
from deep_translator import GoogleTranslator
from firebase_admin import firestore
from flask import send_from_directory # NEW: Added to serve images to Coil

crop_doctor_bp = Blueprint('crop_doctor', __name__)
db = firestore.client()

# Ensure dedicated crop uploads directory exists
UPLOAD_FOLDER = os.path.join('uploads', 'crop')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

@crop_doctor_bp.route('/diagnose', methods=['POST', 'OPTIONS'])
def diagnose_crop():
    if request.method == 'OPTIONS':
        return jsonify({}), 200
        
    if 'file' not in request.files:
        return jsonify({"success": False, "error": "No file stream part detected"}), 400
        
    file = request.files['file']
    if file.filename == '':
        return jsonify({"success": False, "error": "No selected file"}), 400

    target_language = request.form.get('language', 'en')
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500

    if file:
        try:
            # Save physical image to local uploads directory
            filename = secure_filename(file.filename)
            file_path = os.path.join(UPLOAD_FOLDER, filename)
            file.save(file_path)

            img = Image.open(file_path)
            client = genai.Client(api_key=api_key)
            
            prompt = """
            You are an expert plant pathologist. Analyze the provided image.
            If the image contains a human body part, an animal, text, a vehicle, or any random non-plant object, you MUST return exact JSON: {"disease": "Not a plant", "confidence": 1.0, "remedy": "None"}.
            Otherwise, identify the crop disease. Return ONLY a valid JSON object. Do not use markdown like ```json.
            Keys required:
            - "disease" (String: Name of the disease or 'Healthy')
            - "confidence" (Float: decimal between 0 and 1)
            - "remedy" (String: Detailed solution or 'Maintain regular schedule' if healthy)
            """
            
            response = client.models.generate_content(
                model='gemini-3.5-flash',
                contents=[prompt, img]
            )
            
            # Extremely aggressive JSON extraction to prevent 500 crashes
            text_response = response.text.strip()
            if "{" in text_response and "}" in text_response:
                text_response = text_response[text_response.find("{"):text_response.rfind("}")+1]
            result = json.loads(text_response)

            disease_text = str(result.get("disease", "Unknown Disease"))
            remedy_text = str(result.get("remedy", "Maintain regular schedule"))

            if disease_text.strip().lower() == "not a plant":
                return jsonify({"success": False, "error": "It is not a plant or crop. Show me the crop or plant."}), 400

            if target_language != 'en':
                try:
                    translator = GoogleTranslator(source='auto', target=target_language)
                    disease_text = translator.translate(disease_text)
                    remedy_text = translator.translate(remedy_text)
                except Exception as t_err:
                    print("Translation Error:", t_err)

            # Bulletproof float conversion to prevent Retrofit Serialization Exception (the cause of your 200 OK -> UI Error)
            try:
                raw_conf = result.get("confidence", 1.0)
                if isinstance(raw_conf, str):
                    raw_conf = raw_conf.replace('%', '').replace(',', '').strip()
                confidence_val = float(raw_conf)
                if confidence_val > 1.0:
                    confidence_val = confidence_val / 100.0  # Normalize to 0.0 - 1.0 format required by Retrofit
            except (ValueError, TypeError):
                confidence_val = 0.95

            return jsonify({
                "success": True,
                "disease": disease_text,
                "confidence": confidence_val,
                "remedy": remedy_text,
                "imagePath": file_path
            }), 200
            
        except errors.ClientError as e:
            traceback.print_exc()
            return jsonify({"success": False, "error": "AI quota is over for today comeback tommorrow"}), 429
        except Exception as e:
            traceback.print_exc()
            return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500

@crop_doctor_bp.route('/save-diagnosis', methods=['POST'])
def save_diagnosis():
    data = request.get_json()
    email = data.get('email')
    
    if not email:
        return jsonify({"success": False, "error": "Missing email"}), 400
        
    try:
        doc_ref = db.collection('users').document(email).collection('diagnostic_history').document()
        doc_ref.set({
            "disease": data.get('disease', ''),
            "remedy": data.get('remedy', ''),
            "imagePath": data.get('imagePath', ''),
            "mode": data.get('mode', 'Online'),
            "language": data.get('language', 'en'),
            "date": datetime.datetime.utcnow().isoformat()
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
        
        history = [doc.to_dict() for doc in docs]
        return jsonify({"success": True, "history": history}), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500

# --- NEW: Serve Images to Android Coil Frontend ---
@crop_doctor_bp.route('/serve-image/<path:filename>', methods=['GET'])
def serve_image(filename):
    try:
        # Clean the filename to prevent directory traversal and target the crop folder
        clean_filename = os.path.basename(filename)
        base_dir = os.path.abspath(UPLOAD_FOLDER)
        return send_from_directory(base_dir, clean_filename)
    except Exception as e:
        return jsonify({"success": False, "error": "Image not found"}), 404