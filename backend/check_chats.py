import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('firebase_key.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

chats = db.collection('chats').get()
found = False
for chat in chats:
    data = chat.to_dict()
    f_email = data.get('farmerEmail')
    u_email = data.get('userEmail')
    if f_email == 'srimathi@gmail.com' or u_email == 'srimathi@gmail.com':
        print(f'Chat ID: {chat.id}')
        print(f'farmerEmail: {f_email}')
        print(f'userEmail: {u_email}')
        print(f'lastMessage: {data.get("lastMessage")}')
        found = True

if not found:
    print('srimathi@gmail.com not found in any chats.')
