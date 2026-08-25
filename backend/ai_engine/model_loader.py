import os
import json
import numpy as np
from sentence_transformers import SentenceTransformer

# Global cache variables
_model = None
_embeddings = None
_answers = None

def initialize():
    """
    Initializes the model and loads the embeddings and answers into memory once.
    This should be called during the application startup.
    """
    global _model, _embeddings, _answers
    
    if _model is not None:
        return # Already initialized

    print("Initializing Chatbot AI Engine...")

    base_dir = os.path.dirname(os.path.abspath(__file__))
    embeddings_path = os.path.join(base_dir, "embeddings_fp16.npy")
    answers_path = os.path.join(base_dir, "answers_min.json")
    
    # 1. Load the model
    print("Loading sentence-transformers/all-MiniLM-L6-v2...")
    _model = SentenceTransformer("all-MiniLM-L6-v2")
    
    # 2. Load embeddings
    print("Loading embeddings...")
    _embeddings = np.load(embeddings_path)
    
    # 3. Load answers
    print("Loading answers...")
    with open(answers_path, "r", encoding="utf-8") as f:
        _answers = json.load(f)
        
    print("Chatbot AI Engine initialized successfully!")

def get_model():
    if _model is None:
        raise RuntimeError("Model is not initialized. Call initialize() first.")
    return _model

def get_embeddings():
    if _embeddings is None:
        raise RuntimeError("Embeddings are not loaded. Call initialize() first.")
    return _embeddings

def get_answers():
    if _answers is None:
        raise RuntimeError("Answers are not loaded. Call initialize() first.")
    return _answers
