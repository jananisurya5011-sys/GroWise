import os
import json
import numpy as np
from PIL import Image

try:
    import tensorflow as tf
except ImportError:
    tf = None

_model = None
_class_indices = None
_class_names = None

def initialize():
    global _model, _class_indices, _class_names
    if _model is not None:
        return  # Already initialized

    print("Initializing Disease Classification Engine...")
    base_dir = os.path.dirname(os.path.abspath(__file__))
    models_dir = os.path.join(base_dir, 'models')
    
    model_path = os.path.join(models_dir, 'GroWise_Model.keras')
    class_indices_path = os.path.join(models_dir, 'class_indices.json')
    
    if not os.path.exists(model_path):
        print(f"Warning: Model not found at {model_path}. Inference will not work.")
        return
        
    if not os.path.exists(class_indices_path):
        print(f"Warning: Class indices not found at {class_indices_path}. Inference will not work.")
        return

    if tf is None:
        print("Warning: TensorFlow is not installed. Inference will not work.")
        return

    # Load Model
    print("Loading GroWise_Model.keras...")
    _model = tf.keras.models.load_model(model_path)
    
    # Load Class Indices
    print("Loading class_indices.json...")
    with open(class_indices_path, "r", encoding="utf-8") as f:
        _class_indices = json.load(f)
        
    # Reverse the mapping from { "Disease_Name": index } to { index: "Disease_Name" }
    # Handle if it's already { index: "Disease_Name" } or list
    if isinstance(_class_indices, dict):
        if all(isinstance(k, str) and str(k).isdigit() for k in _class_indices.keys()):
            # Already mapped index as string to name
            _class_names = {int(k): v for k, v in _class_indices.items()}
        elif all(isinstance(v, int) for v in _class_indices.values()):
            # Typical Keras flow { "Class": 0 }
            _class_names = {v: k for k, v in _class_indices.items()}
        else:
            _class_names = _class_indices
    elif isinstance(_class_indices, list):
        _class_names = {i: name for i, name in enumerate(_class_indices)}

    print("Disease Classification Engine initialized successfully!")

def predict_image(image_path):
    if _model is None:
        raise RuntimeError("Model is not loaded. Ensure models/GroWise_Model.keras exists.")
    
    # Preprocessing Config
    target_size = (224, 224)
    
    try:
        # Load and resize
        img = Image.open(image_path).convert('RGB')
        img = img.resize(target_size)
        
        # Convert to numpy array and ensure float32
        img_array = np.array(img, dtype=np.float32)
        
        # Normalize: Divide by 255.0
        img_array = img_array / 255.0
        
        # Add batch dimension
        img_array = np.expand_dims(img_array, axis=0)
        
        # Predict
        predictions = _model.predict(img_array)
        predicted_class_index = int(np.argmax(predictions, axis=1)[0])
        confidence = float(np.max(predictions))
        
        # Get disease name
        disease_name = _class_names.get(predicted_class_index, "Unknown Disease")
        if isinstance(disease_name, str):
            disease_name = disease_name.replace('___', ' ').replace('_', ' ')
            
        return {
            "disease": disease_name,
            "confidence": confidence,
            "class_id": predicted_class_index
        }
    except Exception as e:
        print(f"Error during prediction: {e}")
        raise
