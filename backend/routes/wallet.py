from flask import Blueprint, request, jsonify
from firebase_admin import firestore
import time

wallet_bp = Blueprint('wallet', __name__)
db = firestore.client()

# --- INTERNAL WALLET HELPER ENGINE ---
@firestore.transactional
def lock_escrow_transaction(transaction, wallet_ref, tx_ref, email, amount, order_id, title):
    wallet_doc = wallet_ref.get(transaction=transaction)
    bal = float(wallet_doc.to_dict().get('balance', 0.0)) if wallet_doc.exists else 0.0
    if bal < amount:
        return False
    transaction.update(wallet_ref, {"balance": bal - amount})
    transaction.set(tx_ref, {
        "email": email, "type": "ESCROW_LOCK", "title": title,
        "amount": amount, "isCredit": False, "timestamp": int(time.time() * 1000), "orderId": order_id
    })
    return True

def lock_escrow_sync(email, amount, order_id, title="Escrow Hold"):
    wallet_ref = db.collection('wallets').document(email)
    tx_ref = db.collection('transactions').document()
    transaction = db.transaction()
    return lock_escrow_transaction(transaction, wallet_ref, tx_ref, email, amount, order_id, title)

def refund_escrow_sync(email, amount, order_id, title="Escrow Refund"):
    wallet_ref = db.collection('wallets').document(email)
    wallet_doc = wallet_ref.get()
    bal = float(wallet_doc.to_dict().get('balance', 0.0)) if wallet_doc.exists else 0.0
    db.collection('wallets').document(email).update({"balance": bal + amount})
    db.collection('transactions').document().set({
        "email": email, "type": "ESCROW_REFUND", "title": title,
        "amount": amount, "isCredit": True, "timestamp": int(time.time() * 1000), "orderId": order_id
    })
    return True

def finalize_escrow_sync(order_id, new_title="Paid for Order"):
    tx_docs = db.collection('transactions').where('orderId', '==', order_id).where('type', '==', 'ESCROW_LOCK').get()
    batch = db.batch()
    for doc in tx_docs:
        batch.update(doc.reference, {"type": "PAID_ORDER", "title": new_title, "isCredit": False})
    batch.commit()

def credit_wallet_sync(email, amount, title):
    wallet_ref = db.collection('wallets').document(email)
    db.collection('wallets').document(email).set({"balance": firestore.Increment(amount)}, merge=True)
    db.collection('transactions').document().set({
        "email": email, "type": "PAYOUT", "title": title,
        "amount": amount, "isCredit": True, "timestamp": int(time.time() * 1000)
    })
# -------------------------------------

@wallet_bp.route('/fetch-transactions', methods=['POST'])
def fetch_wallet_transactions():
    try:
        data = request.get_json() or {}
        email = data.get('email', '')

        if not email:
            return jsonify([]), 200

        transactions = []

        # 1. Fetch manual top-ups or custom wallet logs from the 'transactions' collection
        tx_docs = db.collection('transactions').where('email', '==', email).get()
        for doc in tx_docs:
            d = doc.to_dict()
            transactions.append({
                "type": d.get("type", "UNKNOWN"),
                "title": d.get("title", "Wallet Transaction"),
                "amount": float(d.get("amount", 0.0)),
                "isCredit": bool(d.get("isCredit", True)),
                "timestamp": int(d.get("timestamp", 0))
            })

        # 2. Automatically generate transaction history from 'orders' (Delivered/Cancelled)
        # Orders where user paid money (Debit) or got refunded (Credit)
        user_orders = db.collection('orders').where('userEmail', '==', email).get()
        for doc in user_orders:
            d = doc.to_dict()
            status = d.get('status', '')
            crop_name = d.get('cropName', 'Crop Order')
            total_paid = float(d.get('totalPaid', 0.0))
            ts = int(d.get('timestamp', 0))

            if status == 'DELIVERED':
                transactions.append({
                    "title": f"Paid for {crop_name} Order",
                    "amount": total_paid,
                    "isCredit": False,
                    "timestamp": ts
                })
            elif status == 'CANCELLED_FARMER_FAULT':
                transactions.append({
                    "title": f"Refund for {crop_name} Cancellation",
                    "amount": total_paid,
                    "isCredit": True,
                    "timestamp": ts
                })

        # Orders where farmer earned money (Credit)
        farmer_orders = db.collection('orders').where('farmerEmail', '==', email).where('status', '==', 'DELIVERED').get()
        for doc in farmer_orders:
            d = doc.to_dict()
            crop_name = d.get('cropName', 'Crop Order')
            crop_val = float(d.get('cropValue', 0.0))
            ts = int(d.get('timestamp', 0))

            transactions.append({
                "title": f"Payout for {crop_name} Order",
                "amount": crop_val,
                "isCredit": True,
                "timestamp": ts
            })

        # Sort transactions by newest first
        transactions.sort(key=lambda x: x['timestamp'], reverse=True)

        return jsonify(transactions), 200

    except Exception as e:
        print(f"Error fetching wallet transactions: {str(e)}")
        return jsonify([]), 200