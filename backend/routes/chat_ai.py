import os
import json
import traceback
from flask import Blueprint, request, jsonify
from google import genai
from google.genai import types
from dotenv import load_dotenv

# Explicitly load the variables from your .env file
load_dotenv()

chat_ai_bp = Blueprint('chat_ai', __name__)

# Fetch the key and pass it directly to the new Client
api_key = os.environ.get("GEMINI_API_KEY")
client = genai.Client(api_key=api_key)

@chat_ai_bp.route('/analyze-deal', methods=['POST'])
def analyze_deal():
    data = request.get_json()
    crop = data.get("cropName", "Crop")
    location = data.get("location", "Market")
    role = data.get("role", "farmer")

    # Determine perspective based on user role
    if role == "farmer":
        perspective = f"You are an agricultural AI advising a farmer selling {crop} in {location}, India. Give the absolute minimum and maximum price per kg they should quote to a buyer."
    else:
        perspective = f"You are an agricultural AI advising a buyer purchasing {crop} in {location}, India. Give the absolute minimum and maximum price per kg they should offer to a farmer."

    prompt = f"{perspective} Respond ONLY with a valid JSON object. Format exactly like this: {{\"minPrice\": 20.0, \"maxPrice\": 40.0, \"reason\": \"2 line exact market reason here.\"}}. Do not include markdown formatting, backticks, or any other text."

    try:
        response = client.models.generate_content(
            model='gemini-3.5-flash',
            contents=prompt,
        )
        
        # Clean up response to safely parse JSON
        raw_text = response.text.strip()
        if raw_text.startswith("```json"):
            raw_text = raw_text.replace("```json", "").replace("```", "").strip()
        elif raw_text.startswith("```"):
            raw_text = raw_text.replace("```", "").strip()
        
        parsed_data = json.loads(raw_text)
        
        return jsonify({
            "success": True,
            "minPrice": parsed_data.get("minPrice"),
            "maxPrice": parsed_data.get("maxPrice"),
            "reason": parsed_data.get("reason")
        }), 200
        
    except Exception as e:
        print(f"AI Error: {traceback.format_exc()}")
        return jsonify({
            "success": False, 
            "minPrice": 20.0, 
            "maxPrice": 40.0,
            "reason": "Live market data currently unavailable. Showing baseline estimates."
        }), 500

@chat_ai_bp.route('/general-chat', methods=['POST'])
def general_chat():
    data = request.get_json()
    message = data.get("message", "")
    history = data.get("history", []) # List of dicts: [{"role": "user", "parts": "hi"}, ...]
    language = data.get("language", "English")
    user_role = data.get("role", "farmer")

    # Strict system instruction to force behavior and language
    system_instruction = f"You are GroWise AI, a helpful agricultural assistant. The user is a {user_role}. You MUST reply exclusively in {language}. Keep answers concise, strictly related to agriculture, farming, crops, weather, and market logistics."

    # Reconstruct the conversation history for context
    formatted_history = []
    for msg in history:
        # Map frontend roles to Gemini SDK roles ("user" or "model")
        role = msg.get("role", "user")
        parts = msg.get("parts", "")
        formatted_history.append(
            types.Content(role=role, parts=[types.Part.from_text(text=parts)])
        )
    
    # Append the current message
    formatted_history.append(
        types.Content(role="user", parts=[types.Part.from_text(text=message)])
    )

    try:
        response = client.models.generate_content(
            model='gemini-3.5-flash',
            contents=formatted_history,
            config=types.GenerateContentConfig(
                system_instruction=system_instruction,
            )
        )
        return jsonify({"success": True, "reply": response.text}), 200
    except Exception as e:
        print(f"AI Chat Error: {traceback.format_exc()}")
        return jsonify({"success": False, "error": "Unable to reach AI right now."}), 500