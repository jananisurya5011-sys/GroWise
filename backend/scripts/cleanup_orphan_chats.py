import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from firebase_config import db
from datetime import datetime

def analyze_and_cleanup():
    print("Starting Deep Audit of Deal Inbox (Chats Collection)...\n")
    chats_ref = db.collection('chats')
    docs = chats_ref.stream()

    report = []
    category_a_count = 0
    
    for doc in docs:
        chat = doc.to_dict()
        chat_id = doc.id
        
        user_email = chat.get('userEmail')
        farmer_email = chat.get('farmerEmail')
        last_message = chat.get('lastMessage', '')
        
        # Check subcollections
        messages_ref = chats_ref.document(chat_id).collection('messages').stream()
        messages = [m.to_dict() for m in messages_ref]
        
        category = "Category D - Unknown"
        reason = "Fully intact chat"
        
        if not user_email or not farmer_email:
            reason = "Missing userEmail or farmerEmail metadata"
            if len(messages) == 0:
                category = "Category A - Legacy NGO Donation Chat (Blank)"
            else:
                # check if the only messages are DONATION deals
                all_donation = all(m.get('dealType') == 'DONATION' or m.get('type') == 'DONATION_REQUEST' or 'Donation' in str(m.get('text', '')) for m in messages)
                if all_donation:
                    category = "Category A - Legacy NGO Donation Chat (Orphaned Subcollection)"
                else:
                    category = "Category D - Unknown (Has valid non-donation messages but missing metadata)"
        else:
            if "pool" in chat_id.lower() or chat.get('type') == 'SHARED':
                category = "Category C - Shared Deal Chat"
            else:
                category = "Category B - Commercial Chat"

        # Safe Delete Execution
        if category.startswith("Category A"):
            category_a_count += 1
            # Delete messages first
            for m_doc in chats_ref.document(chat_id).collection('messages').stream():
                chats_ref.document(chat_id).collection('messages').document(m_doc.id).delete()
            # Delete chat
            chats_ref.document(chat_id).delete()
            
        report.append({
            "Chat ID": chat_id,
            "Messages Count": len(messages),
            "Category": category,
            "Reason": reason
        })
        
    print(f"Audit Complete. Scanned {len(report)} total chats.")
    print(f"Deleted {category_a_count} Category A (Orphan NGO Donation) chats.\n")
    
    for item in report:
        if item['Category'].startswith("Category A"):
            print(f"DELETED -> Chat ID: {item['Chat ID']} | Reason: {item['Reason']}")
        elif item['Category'].startswith("Category D"):
            print(f"KEPT (D) -> Chat ID: {item['Chat ID']} | Reason: {item['Reason']}")

if __name__ == '__main__':
    analyze_and_cleanup()
