import firebase_admin
from firebase_admin import credentials, firestore

def initialize_firebase():
    try:
        # Assumes your service account JSON file is named 'firebase_key.json' in the root directory
        cred = credentials.Certificate("firebase_key.json")
        firebase_admin.initialize_app(cred)
        print("SUCCESS: Connected to Firebase Firestore!")
        return firestore.client()
    except Exception as e:
        print(f"ERROR: Could not connect to Firebase: {e}")
        return None

# Export database instance
db = initialize_firebase()