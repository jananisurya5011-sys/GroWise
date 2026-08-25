import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from ai_engine import model_loader

def search(question, threshold=0.55):
    """
    Takes a user question and returns the best matching answer and confidence score.
    Uses cosine similarity against the precomputed embeddings.
    """
    try:
        model = model_loader.get_model()
        embeddings = model_loader.get_embeddings()
        answers = model_loader.get_answers()
        
        # Compute embedding for the incoming question
        # Model returns shape (embedding_dim,) or (1, embedding_dim)
        query_embedding = model.encode(question)
        
        # Ensure 2D arrays for sklearn
        if query_embedding.ndim == 1:
            query_embedding = query_embedding.reshape(1, -1)
            
        # Calculate cosine similarity
        similarities = cosine_similarity(query_embedding, embeddings)[0]
        
        # Find the index of the highest similarity
        best_match_idx = np.argmax(similarities)
        best_score = float(similarities[best_match_idx])
        
        if best_score < threshold:
            return {
                "answer": "I'm sorry, I don't have information about that topic.",
                "confidence": best_score
            }
            
        return {
            "answer": answers[best_match_idx]["answer"],
            "confidence": best_score
        }
    except Exception as e:
        print(f"Chatbot Engine Error: {e}")
        return {
            "answer": "I'm sorry, I encountered an internal error processing your request.",
            "confidence": 0.0
        }
