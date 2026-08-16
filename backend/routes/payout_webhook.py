from flask import Blueprint, request, jsonify
from firebase_admin import firestore
import time
from routes.wallet import finalize_escrow_sync, credit_wallet_sync

payout_bp = Blueprint('payout', __name__)
db = firestore.client()

@payout_bp.route('/process-delivery', methods=['POST'])
def process_delivery_payout():
    try:
        data = request.get_json()
        order_id = data.get('orderId')

        if not order_id:
            return jsonify({"error": "Missing orderId"}), 400

        order_ref = db.collection('orders').document(order_id)
        order_doc = order_ref.get()

        if not order_doc.exists:
            return jsonify({"error": "Order not found"}), 404

        order_data = order_doc.to_dict()
        if order_data.get('status') != 'DELIVERED':
            return jsonify({"error": "Order not marked DELIVERED"}), 400

        # Payout amounts
        crop_value = float(order_data.get('cropValue', 0.0))
        transport_fare = float(order_data.get('transportFare', 0.0))
        farmer_email = order_data.get('farmerEmail')
        driver_email = order_data.get('driverEmail')
        crop_name = order_data.get('cropName', 'Crop')
        timestamp = int(time.time() * 1000)

       # 1. Pay Farmer (100% Crop Value)
        if farmer_email:
            credit_wallet_sync(farmer_email, crop_value, f"Payout for {crop_name} Order")

        # 2. Pay Driver (100% Transport Fare)
        if driver_email:
            credit_wallet_sync(driver_email, transport_fare, f"Transport Fare for {crop_name}")

        # 3. Finalize User's Escrow to "Paid" (Red Deduction)
        finalize_escrow_sync(order_id, f"Paid for {crop_name} Order")

        return jsonify({"success": True, "message": "Escrow payouts complete"}), 200
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500