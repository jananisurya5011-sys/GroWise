import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from firebase_config import db
import math
from datetime import datetime

def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0
    dLat = math.radians(lat2 - lat1)
    dLon = math.radians(lon2 - lon1)
    a = math.sin(dLat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

ngo_lat = 13.0827 # Default to Chennai roughly, or what user has
ngo_lon = 80.2707
ngo_email = "nithish@example.com"  # Using dummy, will fetch actual Nithish lat/lon

# Get Nithish lat/lon
nithish_doc = db.collection('users').where('email', '>=', 'nithish').where('email', '<=', 'nithish\uf8ff').limit(1).get()
if nithish_doc:
    n_data = nithish_doc[0].to_dict()
    ngo_email = n_data.get('email')
    ngo_lat = float(n_data.get('homeLat') or n_data.get('farmLat') or 0.0)
    ngo_lon = float(n_data.get('homeLon') or n_data.get('farmLon') or 0.0)
    print(f"NGO Found: {ngo_email} | Lat: {ngo_lat}, Lon: {ngo_lon}")
else:
    print("Nithish not found, using default lat/lon")

current_ts = datetime.now().timestamp()
ignored_item_ids = set()
if ngo_email:
    ignored_docs = db.collection('users').document(ngo_email).collection('ignoredProducts').stream()
    for i_doc in ignored_docs:
        ignored_item_ids.add(i_doc.id)

print(f"Ignored IDs: {ignored_item_ids}")

query = db.collection('inventory').stream()

for doc in query:
    item = doc.to_dict()
    item_id = doc.id
    crop = item.get('cropName', 'Unknown')
    
    if item.get('email', '').startswith('suriya'):
        print(f"\nEvaluating Item: {item_id} | Crop: {crop}")
        
        if item_id in ignored_item_ids:
            print("  -> REJECTED: In ignoredProducts")
            continue

        expiry_str = item.get('expiryDate', '')
        diff_hours = 1000
        if expiry_str:
            try:
                if "-" in expiry_str:
                    exp_date = datetime.strptime(expiry_str, "%Y-%m-%d")
                else:
                    exp_date = datetime.strptime(expiry_str, "%d/%m/%Y")
                diff_hours = (exp_date.timestamp() - current_ts) / 3600.0
            except Exception as e:
                print(f"  -> Error parsing expiry: {e}")
                pass

        discount_stage = item.get('discountStage', '')
        print(f"  -> Discount Stage: {discount_stage} | AvailableKg: {item.get('availableKg')} | ExpiryDiff: {diff_hours:.2f}h")
        
        if discount_stage == 'NGO_FEED' or item.get('isDonatedToNgo') == True:
            if diff_hours <= 0:
                print("  -> REJECTED: diff_hours <= 0")
                continue
            if float(item.get('availableKg', 0)) <= 0:
                print("  -> REJECTED: availableKg <= 0")
                continue
                
            farmer_email = item.get('email', '')
            print(f"  -> Farmer: {farmer_email}")
            farm_lat = 0.0
            farm_lon = 0.0
            home_lat = 0.0
            home_lon = 0.0
            
            if farmer_email:
                user_doc = db.collection('users').document(farmer_email).get()
                if user_doc.exists:
                    u_data = user_doc.to_dict()
                    farm_lat = float(u_data.get('farmLat') or 0.0)
                    farm_lon = float(u_data.get('farmLon') or 0.0)
                    home_lat = float(u_data.get('homeLat') or 0.0)
                    home_lon = float(u_data.get('homeLon') or 0.0)
                    print(f"  -> Farmer Location: Farm({farm_lat}, {farm_lon}), Home({home_lat}, {home_lon})")
            
            if ngo_lat != 0.0:
                dist_farm = haversine(ngo_lat, ngo_lon, farm_lat, farm_lon) if farm_lat != 0.0 else float('inf')
                dist_home = haversine(ngo_lat, ngo_lon, home_lat, home_lon) if home_lat != 0.0 else float('inf')
                min_dist = min(dist_farm, dist_home)
                print(f"  -> Distance: Farm({dist_farm:.2f}km), Home({dist_home:.2f}km) | Min: {min_dist:.2f}km")

                if min_dist <= 40.0:
                    print("  -> ACCEPTED!")
                else:
                    print("  -> REJECTED: Distance > 40km")
            else:
                print("  -> REJECTED: ngo_lat == 0.0")
        else:
            print("  -> REJECTED: discountStage != 'NGO_FEED'")
