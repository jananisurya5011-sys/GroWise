import os
import json
import traceback
import time
from google import genai
from google.genai import errors

def get_gemini_client():
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set.")
    return genai.Client(api_key=api_key)

def analyze_crop_image(image_file, language="en"):
    """
    Analyzes a crop image using Gemini Vision and returns a strict JSON object.
    The response will be natively in the requested language.
    image_file: A PIL Image object or raw bytes (Gemini accepts PIL Image)
    """
    try:
        client = get_gemini_client()
        
        prompt = f"""
        You are an expert plant pathologist and agronomist.
        Analyze the provided image.
        
        IMPORTANT RULES:
        1. If the image contains a human body part, an animal, text, a vehicle, or any random non-plant object, you MUST return exact JSON with healthStatus "Not a plant", disease "Not a plant".
        2. All textual content in the JSON values MUST be translated natively into the language specified by this ISO code: '{language}'.
        3. You MUST return ONLY a valid JSON object. Do NOT wrap it in Markdown code blocks like ```json. Do NOT output any additional text.
        
        REQUIRED JSON SCHEMA:
        {{
          "cropName": "Name of the crop or 'Unknown'",
          "species": "Scientific species or 'Unknown'",
          "healthStatus": "'Healthy', 'Diseased', or 'Not a plant'",
          "disease": "Name of disease, 'Healthy', or 'Not a plant'",
          "pest": "Name of pest or 'None'",
          "confidence": "Float between 0.0 and 1.0 representing your confidence",
          "severity": "'Low', 'Medium', 'High', 'Critical', or 'None'",
          "symptoms": ["List", "of", "observed", "symptoms"],
          "cause": "Primary cause of the condition",
          "organicTreatment": ["List", "of", "organic", "treatments"],
          "chemicalTreatment": ["List", "of", "chemical", "treatments"],
          "fertilizer": ["Recommended", "fertilizers"],
          "watering": "Watering recommendations",
          "environment": "Ideal environment details",
          "prevention": ["List", "of", "prevention", "steps"],
          "growthTips": ["General", "growth", "tips"],
          "harvestImpact": "Expected impact on harvest",
          "recommendedActions": ["Immediate", "actions", "to", "take"],
          "summary": "A short 2-3 sentence expert summary of the diagnosis."
        }}
        """
        
        max_retries = 3
        response = None
        for attempt in range(max_retries):
            try:
                response = client.models.generate_content(
                    model='gemini-3.5-flash',
                    contents=[prompt, image_file]
                )
                print("\n========== GEMINI RAW RESPONSE ==========")
                print(response.text)
                print("=========================================\n")
                break
            except errors.ServerError as e:
                if attempt < max_retries - 1:
                    time.sleep(5)
                else:
                    return {
                        "success": False,
                        "error": "Gemini service temporarily unavailable. Please try again later."
                    }
        
        text_response = response.text.strip()
        
        # Aggressive JSON extraction to strip any markdown wrappers or hallucinated text
        if "{" in text_response and "}" in text_response:
            text_response = text_response[text_response.find("{"):text_response.rfind("}")+1]
            
        result = json.loads(text_response)
        
        # Ensure confidence is a float
        try:
            raw_conf = result.get("confidence", 0.95)
            if isinstance(raw_conf, str):
                raw_conf = raw_conf.replace('%', '').replace(',', '').strip()
            confidence_val = float(raw_conf)
            if confidence_val > 1.0:
                confidence_val = confidence_val / 100.0
            result["confidence"] = confidence_val
        except (ValueError, TypeError):
            result["confidence"] = 0.95
        print("\n========== PARSED RESULT ==========")
        print(result)
        print("===================================\n")    
        return result
        
    except errors.ClientError as e:
        traceback.print_exc()
        return {
            "success": False,
            "error": "AI service temporarily unavailable. Please try again later."
        }
    except json.JSONDecodeError as e:
        traceback.print_exc()
        print(f"Failed to parse JSON: {text_response}")
        raise Exception("Failed to parse AI response into structured JSON.")
    except Exception as e:
        traceback.print_exc()
        raise Exception("Internal AI Engine Error.")
