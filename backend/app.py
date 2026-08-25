import os
import requests
from flask import Flask, request, jsonify
from routes.auth import auth_bp
from routes.crop_doctor import crop_doctor_bp
from routes.cultivation import cultivation_bp
from routes.inventory import inventory_bp
from routes.rental import rental_bp
from routes.profile import profile_bp
from routes.reviews import reviews_bp
from routes.chat_ai import chat_ai_bp
from routes.admin import admin_bp
from routes.logistics import logistics_bp
from routes.order_routes import order_bp 
from routes.wallet import wallet_bp
from routes.payout_webhook import payout_bp
from routes.ngo_routes import ngo_bp
from routes.deals_routes import deals_bp


from dotenv import load_dotenv
from apscheduler.schedulers.background import BackgroundScheduler
from flask import send_from_directory
import requests
import os



# Securely loads your GEMINI_API_KEY from the .env file
load_dotenv()

app = Flask(__name__)

# Configure Flask app keys
app.config['SECRET_KEY'] = 'growise_super_secure_secret_key'


# Register Modular Blueprints
app.register_blueprint(auth_bp, url_prefix='/api/auth')
app.register_blueprint(crop_doctor_bp, url_prefix='/api/crop-doctor')
app.register_blueprint(cultivation_bp, url_prefix='/api/cultivation')
app.register_blueprint(inventory_bp, url_prefix='/api/inventory')
app.register_blueprint(rental_bp, url_prefix='/api/rental')
app.register_blueprint(profile_bp, url_prefix='/api/profile') # <-- ADD THIS LINE
app.register_blueprint(reviews_bp, url_prefix='/api/reviews')
app.register_blueprint(chat_ai_bp, url_prefix='/api/chat')
app.register_blueprint(admin_bp, url_prefix='/api/admin')
app.register_blueprint(logistics_bp, url_prefix='/api/logistics')
app.register_blueprint(order_bp, url_prefix='/api/orders')
app.register_blueprint(wallet_bp, url_prefix='/api/wallet')
app.register_blueprint(payout_bp, url_prefix='/api/payout')
app.register_blueprint(ngo_bp, url_prefix='/api/ngo')
app.register_blueprint(deals_bp, url_prefix='/api/deals')

# --- AUTOMATIC HOURLY BACKGROUND SCHEDULER ---
def start_scheduler():
    scheduler = BackgroundScheduler()
    
    def run_cleanup():
        try:
            # Pings your own cleanup cron route internally every hour
            inv_res = requests.post("http://127.0.0.1:5000/api/inventory/cron/clean-expired")
            print(f"[BACKGROUND CRON] Hourly inventory sweep executed: {inv_res.text}")
            
            life_res = requests.post("http://127.0.0.1:5000/api/inventory/cron/lifecycle")
            print(f"[BACKGROUND CRON] Hourly inventory lifecycle executed: {life_res.text}")
            
            deal_res = requests.post("http://127.0.0.1:5000/api/deals/cron/expire-requests")
            print(f"[BACKGROUND CRON] Hourly deal expiration executed: {deal_res.text}")
            
            log_res = requests.post("http://127.0.0.1:5000/api/logistics/cron/expire-pools")
            print(f"[BACKGROUND CRON] Hourly pool expiry executed: {log_res.text}")
        except Exception as e:
            print(f"[BACKGROUND CRON ERROR] Cleanup failed to run: {e}")

    # Schedule the job to run every 1 hour
    scheduler.add_job(func=run_cleanup, trigger="interval", hours=1)
    scheduler.start()
    print("Background APScheduler initialized: Clean-expired cron set to run hourly.")

@app.route('/uploads/<path:folder>/<path:filename>')
def serve_uploads(folder, filename):
    return send_from_directory(os.path.join('uploads', folder), filename)

@app.route('/')
def home():
    return "GroWise Production Backend Server Core is Running."

@app.route('/api/weather', methods=['GET'])
def get_weather():
    lat = request.args.get('lat')
    lon = request.args.get('lon')
    
    location_name = "Local Area"
    full_address = ""
    
    # Reverse Geocoding via Nominatim
    if lat and lon:
        try:
            nom_url = f"https://nominatim.openstreetmap.org/reverse?format=json&lat={lat}&lon={lon}"
            nom_resp = requests.get(nom_url, headers={'User-Agent': 'GroWiseApp/1.0'})
            if nom_resp.status_code == 200:
                loc_data = nom_resp.json()
                address = loc_data.get('address', {})
                location_name = address.get('city') or address.get('town') or address.get('village') or address.get('county') or "Local Area"
                full_address = loc_data.get('display_name', '')
        except Exception as e:
            print("Reverse Geocode Error:", e)

    api_key = os.environ.get('OPENWEATHER_API_KEY')
    if api_key and lat and lon:
        try:
            url = f"https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={api_key}&units=metric"
            resp = requests.get(url)
            if resp.status_code == 200:
                data = resp.json()
                return jsonify({
                    "success": True,
                    "location": location_name,
                    "address": full_address,
                    "latitude": lat,
                    "longitude": lon,
                    "temp": data.get("main", {}).get("temp"),
                    "condition": data.get("weather", [{}])[0].get("main", "Clear")
                })
        except Exception as e:
            print("Weather Proxy Error:", e)

    # Fallback Mock if no API key or fetch fails
    return jsonify({
        "success": True,
        "location": location_name,
        "address": full_address,
        "latitude": lat,
        "longitude": lon,
        "temp": 28.5,
        "condition": "Sunny"
    }), 200

if __name__ == '__main__':
    print("Starting GroWise Secure Backend Server Context...")

if os.environ.get("WERKZEUG_RUN_MAIN") == "true":
    # Initialize the automated background scheduler right before launching the server
    start_scheduler()

    # Initialize the Offline Semantic Search Chatbot Model
    from ai_engine import model_loader
    try:
        model_loader.initialize()
    except Exception as e:
        print(f"Failed to initialize Chatbot Engine: {e}")

    # Initialize the Disease Classification AI Engine
    try:
        from ai_engine import disease_model_loader
        disease_model_loader.initialize()
    except Exception as e:
        print(f"Failed to initialize Disease Engine: {e}")

app.run(debug=True, host="0.0.0.0", port=5000)