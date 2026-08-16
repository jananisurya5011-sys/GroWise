import math
import requests

def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0 # Earth radius in kilometers
    dLat = math.radians(lat2 - lat1)
    dLon = math.radians(lon2 - lon1)
    a = math.sin(dLat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

def osrm_distance(lat1, lon1, lat2, lon2):
    try:
        url = f"https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false"
        response = requests.get(url, headers={"User-Agent": "GroWise/1.0 (Android)"}, timeout=5)
        if response.status_code == 200:
            data = response.json()
            distance_meters = data["routes"][0]["distance"]
            return distance_meters / 1000.0
    except Exception as e:
        print(e)
    return haversine(lat1, lon1, lat2, lon2)

lat1 = 12.830215553303542
lon1 = 79.71347084647303
lat2 = 12.8344
lon2 = 79.7042

print("Haversine:", haversine(lat1, lon1, lat2, lon2))
print("OSRM Normal:", osrm_distance(lat1, lon1, lat2, lon2))
print("OSRM Swapped:", osrm_distance(lon1, lat1, lon2, lat2))
