import traceback
from flask import Blueprint, request, jsonify
from firebase_config import db

reviews_bp = Blueprint('reviews', __name__)

@reviews_bp.route('/fetch', methods=['POST'])
def fetch_reviews():
    data = request.get_json()
    farmer_email = data.get('farmerEmail')
    if not farmer_email:
        return jsonify({"error": "Missing farmerEmail"}), 400
    try:
        # Removed Firestore order_by to bypass the 500 error
        reviews_ref = db.collection("reviews").where("farmerEmail", "==", farmer_email).stream()
        reviews = []
        for doc in reviews_ref:
            r = doc.to_dict()
            r["id"] = doc.id
            
            # Dynamically fetch the latest user profile photo for each review
            try:
                user_email = r.get("userEmail", "").strip().lower()
                user_doc = db.collection("users").document(user_email).get()
                if user_doc.exists:
                    r["userProfileImage"] = user_doc.to_dict().get("profileImageUrl", "")
                else:
                    r["userProfileImage"] = ""
            except Exception:
                r["userProfileImage"] = ""
                
            reviews.append(r)
            
        # Perform sorting securely in memory
        reviews.sort(key=lambda x: x.get("timestamp", 0), reverse=True)
        return jsonify(reviews), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@reviews_bp.route('/add', methods=['POST'])
def add_review():
    data = request.get_json()
    try:
        review_data = {
            "farmerEmail": data.get("farmerEmail"),
            "userEmail": data.get("userEmail"),
            "userName": data.get("userName"),
            "rating": float(data.get("rating", 0)),
            "text": data.get("text", ""),
            "timestamp": int(data.get("timestamp", 0))
        }
        db.collection("reviews").add(review_data)
        return jsonify({"success": True, "message": "Review added successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@reviews_bp.route('/update', methods=['POST'])
def update_review():
    data = request.get_json()
    try:
        rid = data.get("id")
        if not rid: return jsonify({"success": False, "message": "Missing ID"}), 400
        db.collection("reviews").document(rid).update({
            "rating": float(data.get("rating", 0)),
            "text": data.get("text", ""),
            "timestamp": int(data.get("timestamp", 0))
        })
        return jsonify({"success": True, "message": "Review updated successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@reviews_bp.route('/delete', methods=['POST'])
def delete_review():
    data = request.get_json()
    try:
        rid = data.get("id")
        if not rid: return jsonify({"success": False, "message": "Missing ID"}), 400
        db.collection("reviews").document(rid).delete()
        return jsonify({"success": True, "message": "Review deleted successfully."}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500