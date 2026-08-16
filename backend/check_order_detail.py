import firebase_admin
from firebase_admin import credentials, firestore

if not firebase_admin._apps:
    cred = credentials.Certificate('firebase_key.json')
    firebase_admin.initialize_app(cred)

db = firestore.client()

doc = db.collection('orders').document('GW-245260').get()
if doc.exists:
    d = doc.to_dict()
    print('Order:', d.get('orderId'))
    print('Status:', d.get('status'))
    print('Cancel Reason:', d.get('cancelReason'))
    print('Timestamp:', d.get('timestamp'))
    print('Accepted Timestamp:', d.get('acceptedTimestamp'))
    print('Refund Time:', d.get('refundTime'))
    print('Keys:', d.keys())
