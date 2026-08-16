# logistics.py
import math
from flask import Blueprint, request, jsonify
from firebase_config import db
from datetime import datetime, timezone
import uuid
import requests
from firebase_admin import firestore
from routes.wallet import lock_escrow_sync, refund_escrow_sync

logistics_bp = Blueprint('logistics', __name__)

def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0 # Earth radius in kilometers
    dLat = math.radians(lat2 - lat1)
    dLon = math.radians(lon2 - lon1)
    a = math.sin(dLat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

def normalize_vehicle_type(v_type):
    if not v_type:
        return ""
    v_clean = str(v_type).lower().strip()
    if v_clean in ["bike", "two-wheeler", "two wheeler"]:
        return "bike"
    elif v_clean in ["auto", "auto-rickshaw", "three-wheeler", "three wheeler", "three-wheeler (auto)", "auto/three-wheeler", "auto / three-wheeler"]:
        return "auto"
    elif v_clean in ["mini truck", "mini-truck"]:
        return "mini-truck"
    elif v_clean in ["lorry", "truck"]:
        return "lorry"
    return v_clean

def osrm_distance(lat1, lon1, lat2, lon2):
    try:
        url = f"https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false"
        response = requests.get(url, headers={"User-Agent": "GroWise/1.0 (Android)"}, timeout=5)
        if response.status_code == 200:
            data = response.json()
            distance_meters = data["routes"][0]["distance"]
            return distance_meters / 1000.0
    except Exception:
        pass
    return haversine(lat1, lon1, lat2, lon2)

def calculate_fare_internal(weight, p_lat, p_lon, d_lat, d_lon):
    if p_lat == 0.0 or d_lat == 0.0:
        raise ValueError("Invalid GPS coordinates.")

    distance_km = osrm_distance(p_lat, p_lon, d_lat, d_lon)
    distance_km = max(1.0, distance_km) # Minimum 1 km

    print(f"DEBUG ROUTING -> Weight: {weight} kg | Distance: {distance_km} km")

    # Strict Routing
    if weight <= 20.0 and distance_km <= 15.0:
        vehicle = "Bike"
        base_fare = 30.0
        per_km = 10.0
        capacity = "20 KG"
        fare = base_fare + (per_km * distance_km)
        mode = "FIXED"
    elif weight <= 500.0 and distance_km <= 40.0:
        vehicle = "Auto"
        base_fare = 150.0
        per_km = 18.0
        capacity = "500 KG"
        fare = base_fare + (per_km * distance_km)
        mode = "FIXED"
    else:
        raise ValueError("OVER_LIMIT: Exceeds Auto limits. Please use Crop Pool.")
        
    return {
        "distanceKm": round(distance_km, 2),
        "vehicleType": vehicle,
        "suggestedFare": round(fare, 2),
        "fareMode": mode,
        "baseFare": base_fare,
        "perKmRate": per_km,
        "capacity": capacity,
        "weightCharge": 0.0
    }

@logistics_bp.route('/calculate-fare', methods=['POST'])
def calculate_fare():
    data = request.get_json()
    weight = float(data.get('weight', 0))
    p_lat = float(data.get('pickupLat', 0))
    p_lon = float(data.get('pickupLon', 0))
    d_lat = float(data.get('dropLat', 0))
    d_lon = float(data.get('dropLon', 0))

    try:
        result = calculate_fare_internal(weight, p_lat, p_lon, d_lat, d_lon)
        result["success"] = True
        return jsonify(result), 200
    except ValueError as e:
        if str(e).startswith("OVER_LIMIT"):
            return jsonify({"error": "OVER_LIMIT", "message": "Exceeds Auto limits. Please use Crop Pool."}), 400
        return jsonify({"error": str(e)}), 400
def expire_pool_if_needed(doc):
    p = doc.to_dict()
    pid = p.get('orderId', '')
    
    # Standard Orders, Donation Orders, and Deals must NEVER use Pool expiry logic
    if pid.startswith('GW-') or pid.startswith('DEAL-DON-') or pid.startswith('DEAL-'):
        return p
        
    current_ts = datetime.now().timestamp() * 1000
    
    # Auto-Expire and Refund (Ensures Host triggers refund even if no one else queries)
    is_pending = p.get('status') in ['PENDING_CO_LOADER', 'PENDING_DRIVER']
    is_driver_pickup_timeout = p.get('status') in ['EN_ROUTE_TO_PICKUP_A', 'WAITING_AT_PICKUP_A', 'EN_ROUTE_TO_PICKUP', 'WAITING_AT_PICKUP']

    if p.get('dispatchTimestamp', 0) < current_ts and (is_pending or is_driver_pickup_timeout):
        if p.get('escrowStatus') == 'REFUNDED':
            return p

        batch = db.batch()
        
        cancel_reason = "Pickup OTP expired" if is_driver_pickup_timeout else "Pool Expired"
        new_status = 'CANCELLED' if is_driver_pickup_timeout else 'EXPIRED'
        
        batch.update(doc.reference, {
            'status': new_status,
            'orderStatus': 'Cancelled' if is_driver_pickup_timeout else 'Expired',
            'escrowStatus': 'REFUNDED',
            'paymentStatus': 'Refunded',
            'cancelReason': cancel_reason,
            'refundCompleted': True,
            'refundTime': firestore.SERVER_TIMESTAMP,
            'driverEmail': None,
            'driverName': None,
            'driverPhone': None,
            'driverId': None,
            'driverProfilePic': None
        })
        p['status'] = new_status
        p['escrowStatus'] = 'REFUNDED'
        
        refund_reason = "Pickup OTP expired" if is_driver_pickup_timeout else "Refunded - Pool Expired"
        refund_title = "Refunded - Escrow Released"
        
        # Refund Host (Update Original Escrow Card)
        if p.get('totalPayment', 0) > 0:
            email_a = p.get('hostEmail')
            w_ref = db.collection('wallets').document(email_a)
            w_doc = w_ref.get()
            bal = w_doc.to_dict().get('balance', 0) if w_doc.exists else 0
            batch.update(w_ref, {'balance': bal + p['totalPayment']})
            
            tx_q_a = db.collection('transactions').where('email', '==', email_a).where('orderId', '==', pid).where('type', '==', 'ESCROW_LOCK').limit(1).get()
            if len(tx_q_a) > 0:
                batch.update(tx_q_a[0].reference, {"type": "ESCROW_REFUND", "title": refund_title, "reason": refund_reason, "isCredit": True, "timestamp": int(current_ts)})
            else:
                batch.set(db.collection('transactions').document(), {
                    "email": email_a, "type": "ESCROW_REFUND", "title": refund_title, "reason": refund_reason,
                    "amount": p['totalPayment'], "isCredit": True, "timestamp": int(current_ts), "orderId": pid
                })
            
        # Refund Co-loader (Update Original Escrow Card)
        if p.get('coLoaderPayment', 0) > 0:
            email_b = p.get('coLoaderEmail')
            w_ref = db.collection('wallets').document(email_b)
            w_doc = w_ref.get()
            bal = w_doc.to_dict().get('balance', 0) if w_doc.exists else 0
            batch.update(w_ref, {'balance': bal + p['coLoaderPayment']})
            
            tx_q_b = db.collection('transactions').where('email', '==', email_b).where('orderId', '==', pid).where('type', '==', 'ESCROW_LOCK').limit(1).get()
            if len(tx_q_b) > 0:
                batch.update(tx_q_b[0].reference, {"type": "ESCROW_REFUND", "title": refund_title, "reason": refund_reason, "isCredit": True, "timestamp": int(current_ts)})
            else:
                batch.set(db.collection('transactions').document(), {
                    "email": email_b, "type": "ESCROW_REFUND", "title": refund_title, "reason": refund_reason,
                    "amount": p['coLoaderPayment'], "isCredit": True, "timestamp": int(current_ts), "orderId": pid
                })
        batch.commit()
        
    return p

@logistics_bp.route('/available-loads', methods=['GET'])
def available_loads():
    try:
        driver_lat = float(request.args.get('lat', 0.0))
        driver_lon = float(request.args.get('lon', 0.0))
        driver_email = request.args.get('email', '')
        
        # EXPIRE ACTIVE TRIP IF TIMEOUT (driver UI sync)
        if driver_email:
            active_q = db.collection('orders').where('driverEmail', '==', driver_email).stream()
            for doc in active_q:
                expire_pool_if_needed(doc)
        
        # 1. Fetch driver profile for strict vehicle matching
        driver_vehicle = "Any"
        if driver_email:
            driver_doc = db.collection('users').document(driver_email).get()
            if driver_doc.exists:
                driver_vehicle = driver_doc.to_dict().get('vehicleType', 'Any')

        loads = []
      # Unified Fetch for all Available Loads (Standard & Pool)
        query = db.collection('orders').where('status', 'in', ['PENDING_DRIVER', 'PENDING_CO_LOADER']).stream()
        current_ts = datetime.now().timestamp() * 1000

        for doc in query:
            order = doc.to_dict()
            
            # Filter expired pools if driver isn't assigned
            if 'POOL' in order.get('orderId', ''):
                if order.get('status') == 'PENDING_CO_LOADER' and order.get('dispatchTimestamp', 0) < current_ts:
                    continue
                if order.get('status') == 'EXPIRED':
                    continue

            declined_by = order.get('declinedBy', [])
            if driver_email and driver_email in declined_by:
                continue

            p_lat = order.get("pickupLat", 0.0)
            p_lon = order.get("pickupLon", 0.0)
            vehicle = order.get("vehicleType", "Any")
            
            # Unify Fare Display for Driver
            if 'POOL' in order.get('orderId', ''):
                t_fare = float(order.get("totalPayment", 0.0))
                c_fare = float(order.get("coLoaderPayment", 0.0))
                order["transportFare"] = round(t_fare + c_fare, 2)

            if driver_lat != 0.0 and driver_lon != 0.0 and p_lat != 0.0:
                dist_to_pickup = haversine(driver_lat, driver_lon, p_lat, p_lon)
                if "Mini-Truck" in vehicle and dist_to_pickup > 150.0:
                    continue
                if "Lorry" in vehicle and dist_to_pickup > 300.0:
                    continue
                if vehicle == "Bike" and dist_to_pickup > 15.0:
                    continue
                if vehicle == "Auto" and dist_to_pickup > 40.0:
                    continue

            # 3. Strict Backend Vehicle Filtering with Normalization (Prevent Cross-Vehicle Loads)
            if driver_vehicle != "Any" and vehicle != "Any":
                norm_driver = normalize_vehicle_type(driver_vehicle)
                norm_load = normalize_vehicle_type(vehicle)
                
                # Compare normalized types
                if norm_driver not in norm_load and norm_load not in norm_driver:
                    continue

            loads.append(order)
            
        return jsonify({"success": True, "loads": loads}), 200
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@logistics_bp.route('/accept-order', methods=['POST'])
def accept_order():
    data = request.get_json()
    order_id = data.get('orderId')
    driver_email = data.get('email')
    driver_lat = float(data.get('lat', 0.0))
    driver_lon = float(data.get('lon', 0.0))

    if not order_id or not driver_email:
        return jsonify({"success": False, "error": "Missing parameters."}), 400

    order_ref = db.collection('orders').document(order_id)
    order_doc = order_ref.get()
    
    if not order_doc.exists:
        return jsonify({"success": False, "error": "Order not found."}), 404
        
    order = order_doc.to_dict()
    if order.get('driverEmail'):
        return jsonify({"success": False, "error": "Order already accepted."}), 400

    driver_doc = db.collection('users').document(driver_email).get()
    if not driver_doc.exists:
        return jsonify({"success": False, "error": "Driver not found."}), 404
        
    driver = driver_doc.to_dict()
    
    is_pool = order.get('coLoaderEmail') is not None or 'POOL' in order_id
    
    route_sequence = []
    
    if not is_pool:
        # Standard Order
        route_sequence = [
            {"id": "HOST_PICKUP", "type": "PICKUP", "role": "HOST", "lat": order.get('pickupLat'), "lon": order.get('pickupLon'), "address": order.get('pickupAddress'), "farmerName": "Farmer", "farmerPhone": "", "profilePic": "", "cropName": order.get('cropName')},
            {"id": "HOST_DROP", "type": "DROP", "role": "HOST", "lat": order.get('dropLat'), "lon": order.get('dropLon'), "address": order.get('dropAddress'), "farmerName": "Farmer", "farmerPhone": "", "profilePic": "", "cropName": order.get('cropName')}
        ]
        # Enrich with farmer details
        f_doc = db.collection('users').document(order.get('farmerEmail') or order.get('hostEmail', '')).get()
        if f_doc.exists:
            f_data = f_doc.to_dict()
            route_sequence[0]['farmerName'] = f_data.get('name', 'Farmer')
            route_sequence[0]['farmerPhone'] = f_data.get('phone', '')
            route_sequence[0]['profilePic'] = f_data.get('profileImageUrl', '')
            route_sequence[1]['farmerName'] = f_data.get('name', 'Farmer')
            route_sequence[1]['farmerPhone'] = f_data.get('phone', '')
            route_sequence[1]['profilePic'] = f_data.get('profileImageUrl', '')
    else:
        # Pool Order: TSP Optimization
        h_email = order.get('hostEmail')
        c_email = order.get('coLoaderEmail')
        
        h_doc = db.collection('users').document(h_email).get() if h_email else None
        c_doc = db.collection('users').document(c_email).get() if c_email else None
        
        h_data = h_doc.to_dict() if h_doc and h_doc.exists else {}
        c_data = c_doc.to_dict() if c_doc and c_doc.exists else {}
        
        h_pickup = {"id": "HOST_PICKUP", "type": "PICKUP", "role": "HOST", "lat": order.get('pickupLat'), "lon": order.get('pickupLon'), "address": order.get('pickupAddress'), "farmerName": h_data.get('name', 'Host Farmer'), "farmerPhone": h_data.get('phone', ''), "profilePic": h_data.get('profileImageUrl', ''), "cropName": order.get('cropName')}
        h_drop = {"id": "HOST_DROP", "type": "DROP", "role": "HOST", "lat": order.get('dropLat'), "lon": order.get('dropLon'), "address": order.get('dropAddress'), "farmerName": h_data.get('name', 'Host Farmer'), "farmerPhone": h_data.get('phone', ''), "profilePic": h_data.get('profileImageUrl', ''), "cropName": order.get('cropName')}
        
        c_pickup = {"id": "CO_LOADER_PICKUP", "type": "PICKUP", "role": "CO_LOADER", "lat": order.get('coLoaderPickupLat'), "lon": order.get('coLoaderPickupLon'), "address": order.get('coLoaderPickupAddress'), "farmerName": c_data.get('name', 'Co-loader'), "farmerPhone": c_data.get('phone', ''), "profilePic": c_data.get('profileImageUrl', ''), "cropName": order.get('coLoaderCropName')}
        c_drop = {"id": "CO_LOADER_DROP", "type": "DROP", "role": "CO_LOADER", "lat": order.get('coLoaderDropLat'), "lon": order.get('coLoaderDropLon'), "address": order.get('coLoaderDropAddress'), "farmerName": c_data.get('name', 'Co-loader'), "farmerPhone": c_data.get('phone', ''), "profilePic": c_data.get('profileImageUrl', ''), "cropName": order.get('coLoaderCropName')}
        
        # Valid Permutations (Pickup before Drop)
        perms = [
            [h_pickup, c_pickup, h_drop, c_drop],
            [h_pickup, c_pickup, c_drop, h_drop],
            [c_pickup, h_pickup, h_drop, c_drop],
            [c_pickup, h_pickup, c_drop, h_drop]
        ]
        
        min_dist = float('inf')
        best_perm = perms[0]
        
        for perm in perms:
            # Distance from driver to first stop
            dist = haversine(driver_lat, driver_lon, perm[0]['lat'], perm[0]['lon'])
            # Distance between stops
            for i in range(3):
                dist += haversine(perm[i]['lat'], perm[i]['lon'], perm[i+1]['lat'], perm[i+1]['lon'])
                
            if dist < min_dist:
                min_dist = dist
                best_perm = perm
                
        route_sequence = best_perm

    update_data = {
        "driverEmail": driver_email,
        "driverName": driver.get('username') or driver.get('name') or 'Driver',
        "driverPhone": driver.get('phone', ''),
        "driverId": driver.get('driverId', ''),
        "driverProfilePic": driver.get('profileImageUrl', ''),
        "status": "EN_ROUTE_TO_" + route_sequence[0]['id'] if is_pool else "EN_ROUTE_TO_PICKUP",
        "acceptedTimestamp": int(datetime.now().timestamp() * 1000),
        "routeSequence": route_sequence,
        "currentRouteIndex": 0
    }
    
    order_ref.update(update_data)
    return jsonify({"success": True, "message": "Order accepted."}), 200
    
@logistics_bp.route('/available-pools', methods=['GET'])
def get_available_pools():
    user_email = request.args.get('email', '')
    
    # 0. Fetch Farmer Profile for robust dual-address matching
    farmer_home_lat, farmer_home_lon = 0.0, 0.0
    farmer_farm_lat, farmer_farm_lon = 0.0, 0.0
    
    if user_email:
        user_doc = db.collection('users').document(user_email).get()
        if user_doc.exists:
            udata = user_doc.to_dict()
            try: farmer_home_lat = float(udata.get('homeLat') or 0.0)
            except: farmer_home_lat = 0.0
            try: farmer_home_lon = float(udata.get('homeLon') or 0.0)
            except: farmer_home_lon = 0.0
            try: farmer_farm_lat = float(udata.get('farmLat') or 0.0)
            except: farmer_farm_lat = 0.0
            try: farmer_farm_lon = float(udata.get('farmLon') or 0.0)
            except: farmer_farm_lon = 0.0
    pools = []
    pool_ids_seen = set()

    # 1. Direct query for Host and Co-loader pools across ALL statuses so Review Tab card ALWAYS renders
    if user_email:
        host_query = db.collection('orders').where('hostEmail', '==', user_email).stream()
        for doc in host_query:
            p = expire_pool_if_needed(doc)
            pid = p.get('orderId')
            
            # Filter pools hidden/deleted by the Host
            if user_email in p.get('deletedBy', []):
                continue
                
            if pid not in pool_ids_seen:
                p['distanceToMe'] = 0.0
                pools.append(p)
                pool_ids_seen.add(pid)
                
        co_query = db.collection('orders').where('coLoaderEmail', '==', user_email).stream()
        for doc in co_query:
            p = expire_pool_if_needed(doc)
            pid = p.get('orderId')
            
            # Filter pools hidden/deleted by the Co-Loader
            if user_email in p.get('deletedBy', []):
                continue
                
            if pid not in pool_ids_seen:
                p['distanceToMe'] = 0.0
                pools.append(p)
                pool_ids_seen.add(pid)

    # 2. Query available nearby pools for discovery (Only active ones)
    # Temporarily remove .where() filter to debug why 0 documents are returned
    query = list(db.collection('orders').stream())
    
    total_pools_checked = 0
    total_pools_passed = 0
    total_in_firestore = len(query)

    for doc in query:
        total_pools_checked += 1
        p = expire_pool_if_needed(doc)
        pid = p.get('orderId')
        
        print("-" * 40)
        print(f"Pool ID: {pid}")
        
        # Check if it's a Crop Pool
        is_pool = "POOL-" in str(pid)
        if not is_pool:
            print(f"{pid} skipped -> not a pool")
            print("-" * 40)
            continue
            
        status = p.get('status', '')
        if 'CANCELLED' in status or 'COMPLETED' in status or 'EXPIRED' in status or status == 'DELIVERED':
            print(f"{pid} skipped -> expired")
            print("-" * 40)
            continue
            
        rem_cap = float(p.get('remainingCapacity', 0))
        if rem_cap <= 0:
            print(f"{pid} skipped -> remainingCapacity <= 0")
            print("-" * 40)
            continue

        if pid in pool_ids_seen:
            print(f"{pid} skipped -> already seen")
            print("-" * 40)
            continue

        if user_email and user_email in p.get('deletedBy', []):
            print(f"{pid} skipped -> deleted")
            print("-" * 40)
            continue
        
        if user_email and p.get('hostEmail') == user_email:
            print(f"{pid} skipped -> host itself")
            print("-" * 40)
            continue
            
        if p.get('coLoaderEmail') == user_email:
            print(f"{pid} skipped -> already joined")
            print("-" * 40)
            continue
            
        p_lat = p.get('pickupLat', 0)
        p_lon = p.get('pickupLon', 0)
        
        # Check Both Home and Farm Addresses for 15km Radius
        dist_home = haversine(farmer_home_lat, farmer_home_lon, p_lat, p_lon) if (farmer_home_lat != 0.0 and p_lat != 0.0) else float('inf')
        dist_farm = haversine(farmer_farm_lat, farmer_farm_lon, p_lat, p_lon) if (farmer_farm_lat != 0.0 and p_lat != 0.0) else float('inf')
        
        min_dist = min(dist_home, dist_farm)
        
        if min_dist <= 15.0:
            print(f"{pid} accepted")
            p['distanceToMe'] = round(min_dist, 2)
            pools.append(p)
            pool_ids_seen.add(pid)
            total_pools_passed += 1
        else:
            print(f"{pid} skipped -> outside radius")
            
        print("-" * 40)
            
    print("========================================")
    print("Total pools:")
    print(f"Checked: {total_pools_checked}")
    print(f"Accepted: {total_pools_passed}")
    print(f"Returned IDs: {[p.get('orderId') for p in pools]}")
    print("========================================")
    
    return jsonify({"success": True, "pools": pools}), 200

@logistics_bp.route('/quote-join-pool', methods=['POST'])
def quote_join_pool():
    data = request.get_json()
    pool_id = data.get('poolId')
    coload_email = data.get('farmerEmail')
    add_weight = float(data.get('weightKg', 0))
    cp_lat = float(data.get('pickupLat', 0))
    cp_lon = float(data.get('pickupLon', 0))
    cd_lat = float(data.get('dropLat', 0))
    cd_lon = float(data.get('dropLon', 0))

    pool_ref = db.collection('orders').document(pool_id)
    pool_doc = pool_ref.get()
    if not pool_doc.exists:
        return jsonify({"success": False, "error": "Pool not found."}), 404
        
    pool = pool_doc.to_dict()
    
    remaining_capacity = float(pool.get('remainingCapacity', 0))
    if add_weight > remaining_capacity:
        return jsonify({
            "success": False, 
            "error": "Requested quantity exceeds remaining pool capacity.",
            "message": "Requested quantity exceeds remaining pool capacity."
        }), 400
    
    base_cost = float(pool.get('totalPayment', 0.0))
    w_A = float(pool.get('weightKg', 0))
    total_weight = w_A + add_weight
    
    # Calculate TSP / Multi-Stop Route Detour
    pA_lat, pA_lon = pool['pickupLat'], pool['pickupLon']
    dA_lat, dA_lon = pool['dropLat'], pool['dropLon']

    # Original Direct Route Distance
    direct_dist = osrm_distance(pA_lat, pA_lon, dA_lat, dA_lon)

    # Multi-Stop TSP Option 1: Pick A -> Pick B -> Drop B -> Drop A
    seq1_dist = osrm_distance(pA_lat, pA_lon, cp_lat, cp_lon) + \
                osrm_distance(cp_lat, cp_lon, cd_lat, cd_lon) + \
                osrm_distance(cd_lat, cd_lon, dA_lat, dA_lon)

    # Multi-Stop TSP Option 2: Pick A -> Pick B -> Drop A -> Drop B
    seq2_dist = osrm_distance(pA_lat, pA_lon, cp_lat, cp_lon) + \
                osrm_distance(cp_lat, cp_lon, dA_lat, dA_lon) + \
                osrm_distance(dA_lat, dA_lon, cd_lat, cd_lon)

    optimal_multi_dist = min(seq1_dist, seq2_dist)
    extra_detour_km = max(0.0, optimal_multi_dist - direct_dist)

    per_km_rate = 45.0 if "Lorry" in pool.get('vehicleType', '') else 25.0
    detour_cost = round(extra_detour_km * per_km_rate, 2)

    total_vehicle_charge = round(base_cost + detour_cost, 2)
    
    # Weight Proportional Shares
    share_A_base = round(total_vehicle_charge * (w_A / total_weight), 2)
    share_B_base = round(total_vehicle_charge * (add_weight / total_weight), 2)

    # The refund for A is their original payment minus their new share
    refund_A = round(base_cost - share_A_base, 2)
    
    # Ensure no negative refund due to rounding
    if refund_A < 0:
        refund_A = 0.0

    return jsonify({
        "success": True,
        "proportionalShare": share_B_base,
        "detourKm": round(extra_detour_km, 2),
        "detourCost": detour_cost,
        "totalShareB": share_B_base,
        "refundA": refund_A,
        "newShareA": share_A_base,
        
        "cropName": data.get('cropName', pool.get('cropName', '')),
        "joinedQuantity": add_weight,
        "remainingCapacityAfter": round(remaining_capacity - add_weight, 2),
        "totalDistance": round(optimal_multi_dist, 2),
        "totalDriverPayout": total_vehicle_charge,
        "escrowAmount": share_B_base,
        
        "vehicleType": pool.get('vehicleType', ''),
        "platformFee": 0.0,
        "totalPayable": share_B_base
    }), 200

@logistics_bp.route('/delete-pool', methods=['POST'])
def delete_pool():
    data = request.get_json()
    pool_id = data.get('orderId')
    user_email = data.get('email')

    if not pool_id or not user_email:
        return jsonify({"success": False, "error": "Missing parameters."}), 400

    pool_ref = db.collection('orders').document(pool_id)
    pool_doc = pool_ref.get()
    if not pool_doc.exists:
        return jsonify({"success": False, "error": "Pool not found."}), 404

    pool_data = pool_doc.to_dict()
    status = pool_data.get('status', '')

    if status in ['EXPIRED', 'CANCELLED']:
        pool_ref.delete()
        return jsonify({"success": True, "message": "Pool permanently deleted."}), 200
    else:
        return jsonify({"success": False, "error": "Only expired or cancelled pools can be deleted."}), 400

@logistics_bp.route('/quote-pool', methods=['POST'])
def quote_pool():
    data = request.get_json()
    weight = float(data.get('weight', 0))
    p_lat = float(data.get('pickupLat', 0))
    p_lon = float(data.get('pickupLon', 0))
    d_lat = float(data.get('dropLat', 0))
    d_lon = float(data.get('dropLon', 0))

    distance_km = max(1.0, osrm_distance(p_lat, p_lon, d_lat, d_lon))

    if weight <= 1500.0 and distance_km <= 300.0:
        vehicle_type = "Mini-Truck (Tata Ace)"
        base_cost = 500.0
        per_km = 25.0
        capacity = 1500.0
    else:
        vehicle_type = "Heavy Lorry"
        base_cost = 2500.0
        per_km = 45.0
        capacity = 25000.0

    total_amount = round(base_cost + (per_km * distance_km), 2)

    return jsonify({
        "success": True,
        "distanceKm": round(distance_km, 2),
        "vehicleType": vehicle_type,
        "baseFare": base_cost,
        "perKmRate": per_km,
        "totalAmount": total_amount,
        "capacity": capacity
    }), 200

@logistics_bp.route('/create-pool', methods=['POST'])
def create_pool():
    data = request.get_json()
    host_email = data.get('farmerEmail')
    weight = float(data.get('weightKg', 0))
    p_lat = float(data.get('pickupLat', 0))
    p_lon = float(data.get('pickupLon', 0))
    d_lat = float(data.get('dropLat', 0))
    d_lon = float(data.get('dropLon', 0))
    dispatch_time = int(data.get('dispatchTime', 0))
    pickup_address = data.get('pickupAddress', 'Host Farm')
    drop_address = data.get('dropAddress', 'Destination')
    
    # Trust the exact frontend quoted distance & total to prevent mismatch
    distance_km = float(data.get('distanceKm', 0.0))
    base_cost = float(data.get('totalAmount', 0.0))
    vehicle_type = data.get('vehicleType', 'Mini-Truck')

    # Fallback if UI didn't calculate
    if distance_km == 0.0 or base_cost == 0.0:
        distance_km = max(1.0, osrm_distance(p_lat, p_lon, d_lat, d_lon))
        if vehicle_type == "Mini-Truck" or (weight <= 1500.0 and distance_km <= 300.0):
            vehicle_type = "Mini-Truck (Tata Ace)"
            base_cost = round(500.0 + (25.0 * distance_km), 2)
        else:
            vehicle_type = "Heavy Lorry"
            base_cost = round(2500.0 + (45.0 * distance_km), 2)

    if "Lorry" in vehicle_type:
        capacity = 25000.0
    else:
        capacity = 1500.0

    if weight > capacity:
        return jsonify({"success": False, "error": f"Weight exceeds max capacity of {capacity}kg."}), 400

    pool_id = f"POOL-{uuid.uuid4().hex[:6].upper()}"
    pickup_otp = str(uuid.uuid4().int)[:4]
    drop_otp = str(uuid.uuid4().int)[4:8]

    # Delegate Escrow to Wallet Engine
    success = lock_escrow_sync(host_email, base_cost, pool_id, "Crop Pool Escrow Hold")
    if not success:
        return jsonify({"success": False, "error": "Insufficient wallet balance for Escrow lock."}), 400

    batch = db.batch()
    pool_data = {
        "orderId": pool_id,
        "hostEmail": host_email,
        "cropName": data.get('cropName', 'Mixed Crops'),
        "weightKg": weight,
        "pickupAddress": pickup_address,
        "dropAddress": drop_address,
        "pickupLat": p_lat,
        "pickupLon": p_lon,
        "dropLat": d_lat,
        "dropLon": d_lon,
        "dispatchTimestamp": dispatch_time,
        "vehicleType": vehicle_type,
        "totalCapacity": capacity,
        "remainingCapacity": capacity - weight,
        "totalPayment": base_cost,
        "pickupOtp_A": pickup_otp,
        "dropOtp_A": drop_otp,
        "coLoaderEmail": None,
        "status": "PENDING_CO_LOADER",
        "driverEmail": None,
        "timestamp": int(datetime.now().timestamp() * 1000)
    }
    
    batch.set(db.collection('orders').document(pool_id), pool_data)
    batch.commit()

    return jsonify({"success": True, "message": "Pool created and Escrow locked.", "poolId": pool_id}), 200

@logistics_bp.route('/join-pool', methods=['POST'])
def join_pool():
    data = request.get_json()
    pool_id = data.get('poolId')
    coload_email = data.get('farmerEmail')
    add_weight = float(data.get('weightKg', 0))
    cp_lat = float(data.get('pickupLat', 0))
    cp_lon = float(data.get('pickupLon', 0))
    cd_lat = float(data.get('dropLat', 0))
    cd_lon = float(data.get('dropLon', 0))
    p_addr = data.get('pickupAddress', 'Co-loader Farm')
    d_addr = data.get('dropAddress', 'Destination')

    pool_ref = db.collection('orders').document(pool_id)
    pool_doc = pool_ref.get()
    if not pool_doc.exists:
        return jsonify({"success": False, "error": "Pool not found."}), 404
        
    pool = pool_doc.to_dict()
    if pool.get('coLoaderEmail') is not None:
        print("========== JOIN POOL ERROR ==========")
        print("Reason:", "POOL_FULL")
        print("User:", coload_email)
        print("Pool:", pool_id)
        print("Wallet:", "N/A")
        print("Required:", "N/A")
        print("Quote:", "N/A")
        print("Timestamp:", datetime.utcnow())
        print("=====================================")
        return jsonify({
            "success": False,
            "error": "POOL_FULL",
            "message": "Pool is already full.",
            "details": {}
        }), 400
        
    remaining_capacity = float(pool.get('remainingCapacity', 0))
    if add_weight > remaining_capacity:
        print("========== JOIN POOL ERROR ==========")
        print("Reason:", "CAPACITY_EXCEEDED")
        print("User:", coload_email)
        print("Pool:", pool_id)
        print("Wallet:", "N/A")
        print("Required:", "N/A")
        print("Quote:", "N/A")
        print("Timestamp:", datetime.utcnow())
        print("=====================================")
        return jsonify({
            "success": False, 
            "error": "CAPACITY_EXCEEDED",
            "message": "Requested quantity exceeds remaining pool capacity.",
            "details": {"requested": add_weight, "remaining": remaining_capacity}
        }), 400

    base_cost = float(pool.get('totalPayment', 0.0))
    w_A = float(pool.get('weightKg', 0))
    total_weight = w_A + add_weight
    
    # Calculate TSP / Multi-Stop Route Detour
    pA_lat, pA_lon = pool['pickupLat'], pool['pickupLon']
    dA_lat, dA_lon = pool['dropLat'], pool['dropLon']

    direct_dist = osrm_distance(pA_lat, pA_lon, dA_lat, dA_lon)

    seq1_dist = osrm_distance(pA_lat, pA_lon, cp_lat, cp_lon) + \
                osrm_distance(cp_lat, cp_lon, cd_lat, cd_lon) + \
                osrm_distance(cd_lat, cd_lon, dA_lat, dA_lon)

    seq2_dist = osrm_distance(pA_lat, pA_lon, cp_lat, cp_lon) + \
                osrm_distance(cp_lat, cp_lon, dA_lat, dA_lon) + \
                osrm_distance(dA_lat, dA_lon, cd_lat, cd_lon)

    optimal_multi_dist = min(seq1_dist, seq2_dist)
    extra_detour_km = max(0.0, optimal_multi_dist - direct_dist)

    per_km_rate = 45.0 if "Lorry" in pool.get('vehicleType', '') else 25.0
    detour_cost = round(extra_detour_km * per_km_rate, 2)

    total_vehicle_charge = round(base_cost + detour_cost, 2)

    # Weight Proportional Shares
    share_A = round(total_vehicle_charge * (w_A / total_weight), 2)
    share_B = round(total_vehicle_charge * (add_weight / total_weight), 2)

    refund_A = round(base_cost - share_A, 2)
    if refund_A < 0:
        refund_A = 0.0

    # 1. Lock Farmer B's Escrow Share
    success = lock_escrow_sync(coload_email, share_B, pool_id, "Crop Pool Escrow Hold")
    if not success:
        # Debug Logging for Wallet Rejection
        w_doc = db.collection('wallets').document(coload_email).get()
        current_bal = float(w_doc.to_dict().get('balance', 0.0)) if w_doc.exists else 0.0
        
        print("========== JOIN POOL ERROR ==========")
        print("Reason:", "INSUFFICIENT_BALANCE")
        print("User:", coload_email)
        print("Pool:", pool_id)
        print("Wallet:", current_bal)
        print("Required:", share_B)
        print("Quote:", total_vehicle_charge)
        print("Timestamp:", datetime.utcnow())
        print("=====================================")
        
        return jsonify({
            "success": False, 
            "error": "INSUFFICIENT_BALANCE",
            "message": "Wallet balance is insufficient.",
            "details": {"wallet_balance": current_bal, "required_amount": share_B}
        }), 400

    # 2. Refund Farmer A (Creates a green transaction for transparency)
    refund_escrow_sync(pool['hostEmail'], refund_A, pool_id, f"Refund: Co-loader Joined Pool")
    
    batch = db.batch()
    
    # 3. Reduce original Escrow Lock amount for Farmer A to avoid UI confusion
    tx_locks = db.collection('transactions').where('orderId', '==', pool_id).where('email', '==', pool['hostEmail']).where('type', '==', 'ESCROW_LOCK').get()
    for tx in tx_locks:
        batch.update(tx.reference, {"amount": share_A})

    pickup_otp_b = str(uuid.uuid4().int)[:4]
    drop_otp_b = str(uuid.uuid4().int)[4:8]

    batch.update(pool_ref, {
        "coLoaderEmail": coload_email,
        "coLoaderCropName": data.get('cropName', 'Mixed'),
        "coLoaderWeightKg": add_weight,
        "coLoaderPickupAddress": p_addr,
        "coLoaderDropAddress": d_addr,
        "coLoaderPickupLat": cp_lat, "coLoaderPickupLon": cp_lon,
        "coLoaderDropLat": cd_lat, "coLoaderDropLon": cd_lon,
        "totalPayment": share_A,
        "coLoaderPayment": share_B,
        "remainingCapacity": pool.get('remainingCapacity', 0) - add_weight,
        "status": "PENDING_DRIVER",
        "pickupOtp_B": pickup_otp_b,
        "dropOtp_B": drop_otp_b
    })
    batch.commit()

    return jsonify({"success": True, "message": "Successfully joined the pool."}), 200

@logistics_bp.route('/cancel-order', methods=['POST'])
def cancel_order():
    data = request.get_json()
    order_id = data.get('orderId')
    
    # Handling Crop Pools Cancel & Refund
    if 'POOL' in order_id:
        order_ref = db.collection('orders').document(order_id)
        doc = order_ref.get()
        if not doc.exists:
            return jsonify({"error": "Not found"}), 404
        pool = doc.to_dict()
        
        # Rule: Lock cancel if Driver OR Co-loader is assigned
        if pool.get('coLoaderEmail') is not None or pool.get('driverEmail') is not None:
            return jsonify({"success": False, "error": "Cannot cancel: Co-loader or Driver already assigned. Funds locked."}), 400
        
      # Process Escrow Refund for Host Farmer
        host_email = pool.get('hostEmail')
        refund_amount = pool.get('totalPayment', 0.0)
        
        batch = db.batch()
        batch.update(order_ref, {"status": "CANCELLED_BY_HOST"})
        
        if refund_amount > 0 and host_email:
            # Delegate to Secure Wallet Engine
            refund_escrow_sync(host_email, refund_amount, order_id, "Refunded - Pool Cancelled")
            
        batch.commit()
        return jsonify({"success": True, "message": "Pool cancelled and Escrow refunded."}), 200
    
    return jsonify({"error": "Use the standard route for standard orders."}), 400

@logistics_bp.route('/arrive-stop', methods=['POST'])
def arrive_stop():
    data = request.get_json()
    order_id = data.get('orderId')
    stop_id = data.get('stopId')
    
    if not order_id or not stop_id:
        return jsonify({"success": False, "error": "Missing parameters."}), 400
        
    try:
        order_ref = db.collection('orders').document(order_id)
        update_data = {
            "status": f"WAITING_AT_{stop_id}"
        }
        
        if stop_id == 'PICKUP' or stop_id == 'HOST_PICKUP':
            update_data["pickupArrivalTimestamp"] = int(datetime.now(timezone.utc).timestamp() * 1000)
            
        order_ref.update(update_data)
        return jsonify({"success": True, "message": "Arrival recorded securely."}), 200
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

def _complete_donation_settlement(order_data):
    """
    Private helper to finalize the Donation Deals pipeline upon driver drop completion.
    Isolates donation cleanup logic from the standard delivery route.
    """
    deal_id = order_data.get('dealId')
    if not deal_id:
        return
        
    try:
        transaction = db.transaction()
        deal_ref = db.collection('deals').document(deal_id)
        
        @firestore.transactional
        def process_settlement(transaction, d_ref):
            doc = d_ref.get(transaction=transaction)
            if not doc.exists:
                return None
                
            d_data = doc.to_dict()
            if d_data.get('status') != "DELIVERY_PENDING":
                return None
                
            item_id = d_data.get('itemId')
            req_qty = float(d_data.get('requestedQuantity', 0))
            
            # Atomic Inventory Deduction
            inv_ref = db.collection('inventory').document(item_id)
            inv_doc = inv_ref.get(transaction=transaction)
            if inv_doc.exists:
                i_data = inv_doc.to_dict()
                avail = float(i_data.get('availableKg', 0))
                resv = float(i_data.get('reservedQuantity', 0))
                
                transaction.update(inv_ref, {
                    "availableKg": max(0, avail - req_qty),
                    "reservedQuantity": max(0, resv - req_qty)
                })
                
            # Finalize Deal
            transaction.update(d_ref, {"status": "COMPLETED"})
            return d_data
            
        deal_data = process_settlement(transaction, deal_ref)
        if deal_data:
            # Append final system message to Chat thread
            chat_id = f"{deal_data['ngoEmail']}_{deal_data['farmerEmail']}_{deal_id}"
            chat_ref = db.collection('chats').document(chat_id)
            chat_ref.update({
                "donationSummary.status": "COMPLETED",
                "lastUpdated": firestore.SERVER_TIMESTAMP
            })
            
            chat_ref.collection('messages').document().set({
                "senderId": "SYSTEM",
                "text": "Donation Delivered Successfully.",
                "timestamp": firestore.SERVER_TIMESTAMP,
                "type": "SYSTEM_MESSAGE"
            })
    except Exception as e:
        print(f"[DONATION SETTLEMENT ERROR] Failed to sync deal {deal_id}: {e}")

@logistics_bp.route('/complete-delivery', methods=['POST'])
def complete_delivery():
    data = request.get_json()
    order_id = data.get('orderId')
    driver_email = data.get('driverEmail')
    is_pool = data.get('isPool', False)
    crop_value = float(data.get('cropValue', 0.0))
    transport_fare = float(data.get('transportFare', 0.0))
    crop_name = data.get('cropName', 'Crop')
    farmer_email = data.get('farmerEmail')

    stop_id = data.get('stopId')
    entered_otp = data.get('enteredOtp')
    is_final_stop = data.get('isFinalStop', True)
    next_status = data.get('nextStatus')
    next_index = data.get('nextIndex')

    if not order_id or not driver_email:
        return jsonify({"success": False, "error": "Missing parameters."}), 400

    order_ref = db.collection('orders').document(order_id)
    order_doc = order_ref.get()
    if not order_doc.exists:
        return jsonify({"success": False, "error": "Order not found."}), 404
        
    order_data = order_doc.to_dict()
    
    # 0. Autonomous Route Progression Calculation
    route_sequence = order_data.get('routeSequence', [])
    current_index = order_data.get('currentRouteIndex', 0)
    
    if route_sequence and current_index < len(route_sequence):
        expected_stop = route_sequence[current_index]
        expected_stop_id = expected_stop.get('id')
        
        # Security: Never trust frontend stopId
        if stop_id and stop_id != expected_stop_id:
            return jsonify({"success": False, "error": f"Route mismatch. Expected {expected_stop_id} but got {stop_id}."}), 400
            
        stop_id = expected_stop_id
        is_final_stop = (current_index == len(route_sequence) - 1)
        next_index = current_index + 1 if not is_final_stop else None
        next_status = f"EN_ROUTE_TO_{route_sequence[next_index]['id']}" if not is_final_stop else None
    
    # 1. Dynamic OTP Verification
    if stop_id and entered_otp:
        expected_otp = ""
        if stop_id == 'HOST_PICKUP':
            expected_otp = order_data.get('pickupOtp_A') or order_data.get('pickupOtp', '')
        elif stop_id == 'CO_LOADER_PICKUP':
            expected_otp = order_data.get('pickupOtp_B') or order_data.get('coLoaderPickupOtp', '')
        elif stop_id == 'HOST_DROP':
            expected_otp = order_data.get('dropOtp_A') or order_data.get('dropOtp', '')
        elif stop_id == 'CO_LOADER_DROP':
            expected_otp = order_data.get('dropOtp_B') or order_data.get('coLoaderDropOtp', '')
            
        if entered_otp != expected_otp:
            return jsonify({"success": False, "error": f"Invalid OTP for {stop_id}"}), 400
            
    # 2. Intermediate Stop Progression
    if not is_final_stop:
        if next_status and next_index is not None:
            order_ref.update({
                "status": next_status,
                "currentRouteIndex": next_index
            })
        return jsonify({"success": True, "message": "Stop verified successfully."}), 200

    # 3. Final Delivery & Payout Logic
    try:
        batch = db.batch()
        
        # Update Order
        update_data = {
            "status": "DELIVERED",
            "completedAt": firestore.SERVER_TIMESTAMP
        }
        if is_pool:
            update_data["coLoaderDropVerified"] = True
            
        batch.update(order_ref, update_data)

        # Payout Farmer for Crop Value (Only applicable for standard orders if requested)
        if farmer_email and crop_value > 0:
            farmer_wallet_ref = db.collection('wallets').document(farmer_email)
            batch.set(farmer_wallet_ref, {"balance": firestore.Increment(crop_value)}, merge=True)
            
            tx_ref = db.collection('transactions').document()
            batch.set(tx_ref, {
                "email": farmer_email,
                "title": f"Payout for {crop_name}",
                "amount": crop_value,
                "isCredit": True,
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
                "orderId": order_id
            })

        # Payout Driver for Transport Fare
        if transport_fare > 0:
            driver_wallet_ref = db.collection('wallets').document(driver_email)
            batch.set(driver_wallet_ref, {"balance": firestore.Increment(transport_fare)}, merge=True)
            
            tx_title = f"Transport Earnings – {crop_name} Pool" if is_pool else "Transport Earnings"
            
            tx_ref = db.collection('transactions').document()
            batch.set(tx_ref, {
                "email": driver_email,
                "title": tx_title,
                "amount": transport_fare,
                "isCredit": True,
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
                "orderId": order_id
            })

        # Process Escrow: Convert ESCROW_LOCK to PAID with the correct final amount
        escrow_docs = db.collection('transactions').where('orderId', '==', order_id).where('type', '==', 'ESCROW_LOCK').stream()
        order_doc = order_ref.get()
        order_data = order_doc.to_dict() if order_doc.exists else {}
        
        for edoc in escrow_docs:
            tx = edoc.to_dict()
            tx_email = tx.get('email')
            final_amount = tx.get('amount')
            
            # Ensure the exact paid amount is used
            if is_pool:
                if tx_email == order_data.get('hostEmail'):
                    final_amount = order_data.get('totalPayment', final_amount)
                elif tx_email == order_data.get('coLoaderEmail'):
                    final_amount = order_data.get('coLoaderPayment', final_amount)
                    
            batch.update(edoc.reference, {
                "type": "PAID",
                "title": f"Paid for {crop_name} Order",
                "amount": final_amount,
                "isCredit": False
            })

        batch.commit()
        
        # 4. Phase 4 Hook: Donation Deal Settlement
        if order_data.get("isDonation") == True:
            _complete_donation_settlement(order_data)
            
        return jsonify({"success": True, "message": "Delivery completed successfully."}), 200

    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@logistics_bp.route('/cron/expire-pools', methods=['POST'])
def auto_expire_pools():
    try:
        now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
        pools_ref = db.collection('orders').where('orderId', '>=', 'POOL').where('orderId', '<', 'POOM').stream()
        
        batch = db.batch()
        expired_count = 0
        repaired_count = 0
        
        for pool_doc in pools_ref:
            pool = pool_doc.to_dict()
            status = pool.get('status', '')
            dispatch_time = pool.get('dispatchTimestamp', 0)
            pid = pool.get('orderId')
            
            # --- LEGACY REPAIR MECHANISM ---
            # If the pool is successfully delivered but stuck in ESCROW_LOCK because it was completed before the backend route existed
            if status == 'DELIVERED':
                stuck_escrows = db.collection('transactions').where('orderId', '==', pid).where('type', '==', 'ESCROW_LOCK').get()
                for tx_doc in stuck_escrows:
                    tx = tx_doc.to_dict()
                    tx_email = tx.get('email')
                    correct_amount = tx.get('amount')
                    
                    if tx_email == pool.get('hostEmail'):
                        correct_amount = pool.get('totalPayment', correct_amount)
                    elif tx_email == pool.get('coLoaderEmail'):
                        correct_amount = pool.get('coLoaderPayment', correct_amount)
                        
                    batch.update(tx_doc.reference, {
                        "type": "PAID",
                        "title": f"Paid for {pool.get('cropName', 'Crop')} Order",
                        "amount": correct_amount,
                        "isCredit": False
                    })
                    repaired_count += 1
                continue
            # -------------------------------
            
            # Skip if already in a terminal state
            if status in ['PAID', 'CANCELLED_BY_HOST', 'EXPIRED', 'FAILED_NO_DRIVER', 'FAILED']:
                continue
                
            # Skip if driver already assigned and in transit
            if status not in ['PENDING_CO_LOADER', 'PENDING_DRIVER']:
                continue
                
            # Check if overdue
            if dispatch_time > 0 and dispatch_time <= now_ms:
                batch.update(pool_doc.reference, {
                    "status": "FAILED_NO_DRIVER",
                    "reason": "Backend Cron Auto-Expired: No driver found before dispatch time"
                })
                
                # Refund Host if any escrow was held
                refund_amount = pool.get('totalPayment', 0.0)
                if refund_amount > 0 and pool.get('hostEmail'):
                    tx_ref = db.collection('transactions').document()
                    batch.set(tx_ref, {
                        "email": pool.get('hostEmail'),
                        "type": "ESCROW_REFUND",
                        "title": "Refunded - Pool Expired",
                        "amount": refund_amount,
                        "isCredit": True,
                        "timestamp": now_ms
                    })
                    wallet_ref = db.collection('wallets').document(pool.get('hostEmail'))
                    batch.set(wallet_ref, {"balance": firestore.Increment(refund_amount)}, merge=True)
                    
                # Refund Co-loader if any escrow was held
                coload_amount = pool.get('coLoaderPayment', 0.0)
                if coload_amount > 0 and pool.get('coLoaderEmail'):
                    ctx_ref = db.collection('transactions').document()
                    batch.set(ctx_ref, {
                        "email": pool.get('coLoaderEmail'),
                        "type": "ESCROW_REFUND",
                        "title": "Refunded - Pool Expired",
                        "amount": coload_amount,
                        "isCredit": True,
                        "timestamp": now_ms
                    })
                    cwallet_ref = db.collection('wallets').document(pool.get('coLoaderEmail'))
                    batch.set(cwallet_ref, {"balance": firestore.Increment(coload_amount)}, merge=True)
                    
                expired_count += 1
                
        if expired_count > 0:
            batch.commit()
            
        return jsonify({"success": True, "message": f"Auto-expired {expired_count} overdue pools."}), 200
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500