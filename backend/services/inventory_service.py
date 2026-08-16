from firebase_admin import firestore

class InventoryService:
    def __init__(self, db=None):
        self.db = db if db else firestore.client()

    def reserve(self, item_id, quantity, transaction=None):
        """Reserves a specific quantity of an item."""
        if quantity <= 0:
            return False

        doc_ref = self.db.collection('inventory').document(item_id)
        
        def _do_reserve(txn, d_ref):
            doc = d_ref.get(transaction=txn)
            if not doc.exists:
                return False
            data = doc.to_dict()
            available = float(data.get('availableKg', 0))
            if available < quantity:
                return False
                
            reserved = float(data.get('reservedQuantity', 0))
            new_available = available - quantity
            updates = {
                'availableKg': new_available,
                'reservedQuantity': reserved + quantity
            }
            
            moq = float(data.get('moq', 0))
            if new_available < moq:
                updates['moq'] = 0.0
                
            txn.update(d_ref, updates)
            return True

        if transaction:
            return _do_reserve(transaction, doc_ref)
        else:
            @firestore.transactional
            def transactional_reserve(txn, d_ref):
                return _do_reserve(txn, d_ref)
            
            txn = self.db.transaction()
            return transactional_reserve(txn, doc_ref)

    def restore(self, item_id, quantity, transaction=None):
        """Restores a reserved quantity back to available."""
        if quantity <= 0:
            return False

        doc_ref = self.db.collection('inventory').document(item_id)

        def _do_restore(txn, d_ref):
            doc = d_ref.get(transaction=txn)
            if not doc.exists:
                return False
            data = doc.to_dict()
            reserved = float(data.get('reservedQuantity', 0))
            restore_amount = min(reserved, quantity)
            available = float(data.get('availableKg', 0))
            
            txn.update(d_ref, {
                'availableKg': available + restore_amount,
                'reservedQuantity': reserved - restore_amount
            })
            return True

        if transaction:
            return _do_restore(transaction, doc_ref)
        else:
            @firestore.transactional
            def transactional_restore(txn, d_ref):
                return _do_restore(txn, d_ref)
            
            txn = self.db.transaction()
            return transactional_restore(txn, doc_ref)

    def deduct(self, item_id, quantity, transaction=None):
        """Permanently deducts a previously reserved quantity."""
        if quantity <= 0:
            return False

        doc_ref = self.db.collection('inventory').document(item_id)

        def _do_deduct(txn, d_ref):
            doc = d_ref.get(transaction=txn)
            if not doc.exists:
                return False
            data = doc.to_dict()
            reserved = float(data.get('reservedQuantity', 0))
            
            deduct_amount = min(reserved, quantity)
            
            txn.update(d_ref, {
                'reservedQuantity': reserved - deduct_amount
            })
            return True

        if transaction:
            return _do_deduct(transaction, doc_ref)
        else:
            @firestore.transactional
            def transactional_deduct(txn, d_ref):
                return _do_deduct(txn, d_ref)
            
            txn = self.db.transaction()
            return transactional_deduct(txn, doc_ref)
