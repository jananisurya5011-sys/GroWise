import firebase_admin
from firebase_admin import credentials, firestore

if not firebase_admin._apps:
    cred = credentials.Certificate('firebase_key.json')
    firebase_admin.initialize_app(cred)

db = firestore.client()

query = db.collection('orders').where('orderId', '>=', 'GW-').where('orderId', '<', 'GW.').stream()

for doc in query:
    data = doc.to_dict()
    status = data.get('status', '')
    if 'CANCEL' in status or 'EXPIRE' in status:
        print(f"Order ID: {data.get('orderId')}")
        print(f"Status: {status}")
        print(f"Cancel Reason: {data.get('cancelReason')}")
        print(f"Order Status: {data.get('orderStatus')}")
        print(f"Driver Email: {data.get('driverEmail')}")
        print('-' * 40)
