import os
import json
import datetime
import traceback
from flask import Blueprint, request, jsonify
from google import genai
from google.genai import errors
from firebase_admin import firestore

cultivation_bp = Blueprint('cultivation', __name__)
db = firestore.client()

@cultivation_bp.route('/generate', methods=['POST'])
def generate_roadmap():
    data = request.get_json()
    if not data or 'crop_name' not in data or 'email' not in data:
        return jsonify({"success": False, "error": "Missing crop_name or email"}), 400

    crop_name = data['crop_name']
    email = data['email']
    
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500
        
    try:
        client = genai.Client(api_key=api_key)
        
        prompt = f"""
        Create a professional day-by-day cultivation roadmap for growing {crop_name}.
        Return ONLY a valid JSON array of objects. Do not use markdown blocks like ```json.
        Each object must have exactly these keys:
        - "day" (integer representing the day number, e.g., 1, 5, 12)
        - "title" (string, short action title like "Soil Preparation")
        - "description" (string, detailed professional farming instruction)
        Keep it strictly to 5 to 7 major milestones across the growth cycle.
        """
        
        response = client.models.generate_content(
            model='gemini-3.5-flash',
            contents=prompt
        )
        
        text_response = response.text.strip()
        if text_response.startswith("```json"):
            text_response = text_response[7:]
        if text_response.startswith("```"):
            text_response = text_response[3:]
        if text_response.endswith("```"):
            text_response = text_response[:-3]
            
        roadmap = json.loads(text_response.strip())
        
        # Inject status 0 (Pending) into all newly generated tasks
        for task in roadmap:
            task['status'] = 0
        
        doc_ref = db.collection('users').document(email).collection('active_crops').document(crop_name)
        doc_ref.set({
            "cropName": crop_name,
            "currentDay": roadmap[0]['day'] if len(roadmap) > 0 else 1,
            "consecutiveMisses": 0,
            "processStatus": "Active",
            "startDate": datetime.datetime.utcnow().isoformat(), # FIX: Anchor timestamp for calendar lock
            "lastUpdated": datetime.datetime.utcnow().isoformat(),
            "roadmap": roadmap
        })
        
        return jsonify({"success": True, "roadmap": roadmap}), 200
        
    except errors.ClientError as e:
        traceback.print_exc()
        return jsonify({
            "success": False, 
            "error": "AI quota is over for today comeback tommorrow"
        }), 429
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False, 
            "error": "Heavy traffic occurs try again after some times"
        }), 500

@cultivation_bp.route('/fetch-active-crops', methods=['POST'])
def fetch_active_crops():
    data = request.get_json()
    email = data.get('email') if data else None
    
    if not email:
        return jsonify({"success": False, "error": "Missing email"}), 400
        
    try:
        crops_ref = db.collection('users').document(email).collection('active_crops')
        docs = crops_ref.stream()
        
        active_crops = []
        for doc in docs:
            active_crops.append(doc.to_dict())
            
        return jsonify({"success": True, "active_crops": active_crops}), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500

@cultivation_bp.route('/mark-task-done', methods=['POST'])
def mark_task_done():
    data = request.get_json()
    if not data or 'email' not in data or 'crop_name' not in data or 'next_day' not in data:
        return jsonify({"success": False, "error": "Missing required fields"}), 400
        
    email = data['email']
    crop_name = data['crop_name']
    next_day = data['next_day']
    
    try:
        doc_ref = db.collection('users').document(email).collection('active_crops').document(crop_name)
        doc = doc_ref.get()
        
        if doc.exists:
            doc_data = doc.to_dict()
            roadmap = doc_data.get('roadmap', [])
            current_day = doc_data.get('currentDay', 1)
            all_completed = True
            
            # Find current task and mark it 1 (Completed)
            for task in roadmap:
                if task.get('day') == current_day:
                    task['status'] = 1
                if task.get('status', 0) == 0:
                    all_completed = False
            
            status_to_set = "Completed" if all_completed else "Active"
            
            doc_ref.update({
                "currentDay": next_day,
                "consecutiveMisses": 0,
                "processStatus": status_to_set,
                "roadmap": roadmap,
                "lastUpdated": datetime.datetime.utcnow().isoformat()
            })
            
        return jsonify({"success": True, "message": "Task marked done"}), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500

@cultivation_bp.route('/daily-maintenance', methods=['POST'])
def daily_maintenance():
    try:
        users_ref = db.collection('users').stream()
        archived_crops = 0
        updated_crops = 0
        
        for user in users_ref:
            email = user.id
            crops_ref = db.collection('users').document(email).collection('active_crops').stream()
            
            for crop in crops_ref:
                crop_data = crop.to_dict()
                if crop_data.get('processStatus') != 'Active':
                    continue
                
                misses = crop_data.get('consecutiveMisses', 0) + 1
                roadmap = crop_data.get('roadmap', [])
                current_day = crop_data.get('currentDay', 1)
                
                next_day = current_day
                
                # Mark current day task as -1 (Missed) and find the next day
                for task in roadmap:
                    if task.get('day') == current_day:
                        task['status'] = -1
                    elif task.get('day') > current_day and next_day == current_day:
                        next_day = task.get('day')
                
                if misses >= 3:
                    # 3 Strikes = Keep in DB but mark Failed to remove from UI
                    crop.reference.update({
                        "processStatus": "Failed",
                        "roadmap": roadmap,
                        "lastUpdated": datetime.datetime.utcnow().isoformat()
                    })
                    archived_crops += 1
                else:
                    if next_day == current_day:
                        # Reached the end with misses
                        crop.reference.update({
                            "processStatus": "Completed", 
                            "roadmap": roadmap,
                            "lastUpdated": datetime.datetime.utcnow().isoformat()
                        })
                    else:
                        crop.reference.update({
                            "consecutiveMisses": misses,
                            "currentDay": next_day,
                            "roadmap": roadmap,
                            "lastUpdated": datetime.datetime.utcnow().isoformat()
                        })
                    updated_crops += 1
                    
        return jsonify({
            "success": True, 
            "message": f"Maintenance complete. Checked {updated_crops} active tasks. Archived {archived_crops} crops due to 3-strike rule."
        }), 200
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": "Heavy traffic occurs try again after some times"}), 500