import random
from datetime import datetime, time
from firebase_admin import firestore
from flask import Blueprint, request, jsonify
from firebase_config import db


admin_bp = Blueprint('admin', __name__)

@admin_bp.route('/stats', methods=['GET'])
def get_admin_stats():
    try:
        # 1. Total Users
        users_count = len(list(db.collection('users').where('role', 'in', ['user', 'ngo']).stream()))
        
        # 2. Active Farmers
        farmers_count = len(list(db.collection('users').where('role', '==', 'farmer').stream()))
        
        # 3. Verified Drivers
        drivers_count = len(list(db.collection('users').where('role', '==', 'driver').where('verificationStatus', '==', 'APPROVED').stream()))
        
        # 4. Active Deals (Created Today - Pending, Ongoing, Finished)
        from datetime import date
        today = date.today()
        today_start = int(datetime(today.year, today.month, today.day).timestamp() * 1000)
        
        deals_count = 0
        try:
            chats = db.collection('chats').stream()
            for chat in chats:
                # Count any order-related cards created today
                messages = db.collection('chats').document(chat.id).collection('messages').where('timestamp', '>=', today_start).stream()
                for msg in messages:
                    msg_dict = msg.to_dict()
                    if msg_dict.get('type') in ['INQUIRY_CARD', 'INVOICE_CARD', 'RECEIPT_CARD', 'RESCUE_CARD']:
                        deals_count += 1
            # Add Pool Orders
            orders = db.collection('orders').where('timestamp', '>=', today_start).stream()
            for order in orders:
                if 'POOL' in order.to_dict().get('orderId', ''):
                    deals_count += 1
        except Exception as e:
            print(f"Error fetching deals: {e}")

        return jsonify({
            "success": True,
            "totalUsers": users_count,
            "activeFarmers": farmers_count,
            "verifiedDrivers": drivers_count,
            "activeDealsToday": deals_count
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@admin_bp.route('/pending-drivers', methods=['GET'])
def get_pending_drivers():
    try:
        docs = db.collection('users').where('role', '==', 'driver').where('verificationStatus', '==', 'PENDING').stream()
        drivers = []
        for doc in docs:
            d = doc.to_dict()
            d['email'] = doc.id
            if 'passwordHash' in d: del d['passwordHash']
            drivers.append(d)
        return jsonify({"success": True, "drivers": drivers}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@admin_bp.route('/verify-driver', methods=['POST'])
def verify_driver():
    data = request.get_json()
    driver_email = data.get('email')
    action = data.get('action') # "APPROVE" or "REJECT"
    
    if not driver_email or not action:
        return jsonify({"error": "Missing parameters"}), 400
        
    user_ref = db.collection('users').document(driver_email)
    
    try:
        if action == "APPROVE":
            driver_id = f"GW-D{random.randint(1000, 9999)}"
            user_ref.update({"verificationStatus": "APPROVED", "driverId": driver_id})
            return jsonify({"success": True, "message": f"Approved. ID: {driver_id}"}), 200
            
        elif action == "REJECT":
            reason = data.get('reason', 'Failed background verification.')
            user_ref.update({"verificationStatus": "REJECTED", "rejectionReason": reason})
            return jsonify({"success": True, "message": "Rejected successfully."}), 200
            
        return jsonify({"error": "Invalid action"}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@admin_bp.route('/list', methods=['GET'])
def get_admin_list():
    list_type = request.args.get('type')
    try:
        results = []
        if list_type == "users":
            docs = db.collection('users').where('role', 'in', ['user', 'ngo']).stream()
            for doc in docs:
                d = doc.to_dict()
                numeric_id = str(abs(hash(doc.id)))[:4]
                results.append({"name": d.get("name", "Unknown"), "email": doc.id, "id": f"USER-{numeric_id}", "role": d.get("role", "user"), "status": "Active", "phone": d.get("phone", "N/A")})
        
        elif list_type == "farmers":
            docs = db.collection('users').where('role', '==', 'farmer').stream()
            for doc in docs:
                d = doc.to_dict()
                numeric_id = str(abs(hash(doc.id)))[:4]
                results.append({"name": d.get("name", "Unknown"), "email": doc.id, "id": f"FARM-{numeric_id}", "role": "farmer", "status": "Active", "phone": d.get("phone", "N/A")})
                
        elif list_type == "drivers":
            docs = db.collection('users').where('role', '==', 'driver').where('verificationStatus', '==', 'APPROVED').stream()
            for doc in docs:
                d = doc.to_dict()
                results.append({"name": d.get("name", "Unknown"), "email": doc.id, "id": d.get("driverId", "GW-D0000"), "role": "driver", "status": "Verified", "phone": d.get("phone", "N/A")})
                
        elif list_type == "deals":
            from datetime import date
            today = date.today()
            today_start = int(datetime(today.year, today.month, today.day).timestamp() * 1000)
            
            # Fetch Standard & Rescue
            chats = db.collection('chats').stream()
            for chat in chats:
                messages = db.collection('chats').document(chat.id).collection('messages').where('timestamp', '>=', today_start).stream()
                for msg in messages:
                    d = msg.to_dict()
                    msg_type = d.get('type')
                    if msg_type in ['INQUIRY_CARD', 'INVOICE_CARD', 'RECEIPT_CARD', 'RESCUE_CARD']:
                        title = "Hyperlocal Rescue" if msg_type == 'RESCUE_CARD' else "Standard Delivery"
                        subtitle = f"{d.get('cropName', 'Item')} - {d.get('kg', 0)}kg"
                        results.append({"name": title, "email": subtitle, "id": d.get("orderId", f"ORD-{str(d.get('timestamp', ''))[-4:]}"), "role": "deals", "status": d.get("status", "Active"), "phone": "N/A"})
            
            # Fetch Pools
            orders = db.collection('orders').where('timestamp', '>=', today_start).stream()
            for order in orders:
                d = order.to_dict()
                if 'POOL' in d.get('orderId', ''):
                    subtitle = f"{d.get('cropName', 'Item')} - {d.get('weightKg', 0)}kg"
                    results.append({"name": "Shared Pool", "email": subtitle, "id": d.get("orderId", f"POOL-{str(d.get('timestamp', ''))[-4:]}"), "role": "deals", "status": d.get("status", "Active"), "phone": "N/A"})

        return jsonify({"success": True, "items": results}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@admin_bp.route('/graph-data', methods=['GET'])
def get_graph_data():
    filter_range = request.args.get('range', '7 Days')
    try:
        from datetime import date, timedelta
        today = date.today()
        
        if filter_range == '7 Days':
            days_count = 7
        elif filter_range == '1 Month':
            days_count = 30
        elif filter_range == '6 Months':
            days_count = 180
        else:
            days_count = 7

        data_points = []
        for i in range(days_count - 1, -1, -1):
            target_date = today - timedelta(days=i)
            start_ts = int(datetime(target_date.year, target_date.month, target_date.day).timestamp() * 1000)
            end_ts = start_ts + 86400000
            
            day_count = 0
            chats = db.collection('chats').stream()
            for chat in chats:
                messages = db.collection('chats').document(chat.id).collection('messages').where('timestamp', '>=', start_ts).where('timestamp', '<', end_ts).stream()
                for msg in messages:
                    if msg.to_dict().get('type') in ['INQUIRY_CARD', 'INVOICE_CARD', 'RECEIPT_CARD', 'RESCUE_CARD']:
                        day_count += 1
            
            orders = db.collection('orders').where('timestamp', '>=', start_ts).where('timestamp', '<', end_ts).stream()
            for order in orders:
                if 'POOL' in order.to_dict().get('orderId', ''):
                    day_count += 1
                    
            data_points.append(float(day_count))

        if sum(data_points) == 0:
            data_points = [1.0] * days_count

        return jsonify({"success": True, "dataPoints": data_points}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500