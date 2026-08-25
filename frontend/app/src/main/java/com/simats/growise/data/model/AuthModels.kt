// AuthModels.kt
package com.simats.growise.data.model

import com.google.firebase.firestore.PropertyName
import okhttp3.MultipartBody

// --- SIGNUP DATA MODELS ---
data class SignupRequest(
    val name: String,
    val email: String,
    val phone: String, // Added Phone Number
    val password:  String,
    val role: String, // "farmer", "user", or "driver"
    val aadhaarNumber: String? = null,
    val isNgo: Boolean = false,
    val darpanId: String? = null,
    val vehicleType: String? = null, // Added for Driver
    val licenseUrl: String? = null, // Added for Driver
    val rcBookUrl: String? = null // Added for Driver
)
data class SignupResponse(
    val message: String,
    val role: String
)

// --- LOGIN DATA MODELS ---
data class LoginRequest(
    val email: String,
    val password:  String
)

data class PasswordUpdateRequest(
    val identifier: String,
    val oldPassword: String,
    val newPassword: String
)

data class LoginResponse(
    val message: String?,
    val name: String?,
    val email: String?,
    val role: String?,
    val verificationStatus: String? = null, // "PENDING", "APPROVED", "REJECTED", "APPROVED_NEEDS_ID"
    val rejectionReason: String? = null, // Passed if Rejected
    val driverId: String? = null, // Passed if APPROVED_NEEDS_ID
    val customToken: String? = null // Firebase Custom Auth Token
)

data class AdminVerificationRequest(
    val email: String,
    val action: String, // "APPROVE" or "REJECT"
    val reason: String? = null
)

data class AdminStatsResponse(
    val success: Boolean,
    val totalUsers: Int,
    val activeFarmers: Int,
    val verifiedDrivers: Int,
    val activeDealsToday: Int,
    val error: String? = null
)

data class AdminListItem(
    val name: String,
    val email: String,
    val id: String,
    val role: String,
    val status: String,
    val phone: String
)

data class AdminListResponse(
    val success: Boolean,
    val items: List<AdminListItem>?,
    val error: String? = null
)

data class AdminGraphResponse(
    val success: Boolean,
    val dataPoints: List<Float>?,
    val error: String? = null
)

data class PendingDriverResponse(
    val email: String,
    val name: String,
    val phone: String,
    val vehicleType: String,
    val licenseUrl: String,
    val rcBookUrl: String,
    val aadhaarNumber: String
)

// --- AI CROP DOCTOR DATA MODELS ---
data class CropDiagnosisResponse(
    val success: Boolean,
    val disease: String? = null,
    val confidence: Double? = null,
    val imagePath: String? = null,
    val remedy: String? = null,
    val data: GeminiDiagnosisData? = null,
    val error: String? = null
)

data class GeminiDiagnosisData(
    val disease: String? = null,
    val confidence: Double? = null,
    val organicTreatment: List<String>? = null,
    val chemicalTreatment: List<String>? = null,
    val prevention: List<String>? = null,
    val symptoms: List<String>? = null,
    val causes: String? = null,
    val remedy: String? = null
)

// --- SMART CULTIVATION SCHEDULER DATA MODELS ---
data class CultivationRequest(val email: String, val crop_name: String)
data class CultivationTask(val day: Int, val title: String, val description: String, val status: Int = 0) // 0: Pending, 1: Done, -1: Missed
data class CultivationResponse(val success: Boolean, val roadmap: List<CultivationTask>?, val error: String?)

data class ActiveCropResponse(val success: Boolean, val active_crops: List<ActiveCropStateModel>?, val error: String?)
data class ActiveCropStateModel(val cropName: String, val currentDay: Int, val consecutiveMisses: Int, val processStatus: String = "Active", val startDate: String = "", val roadmap: List<CultivationTask>)

data class TaskCompletionRequest(val email: String, val crop_name: String, val next_day: Int)

// --- COMMON ERROR MODEL ---
data class ApiErrorResponse(
    val error: String
)

// --- AGRICULTURAL INVENTORY DATA MODELS ---
data class InventoryItemRequest(
    val category: String,
    val cropName: String,
    val pricePerKg: Double,
    val availableKg: Double,
    val moq: Double,
    val harvestDate: String,
    val expiryDate: String
)

data class InventoryItemResponse(
    val id: String,
    val email: String = "",
    val category: String = "Vegetables",
    val cropName: String,
    val pricePerKg: Double,
    val discountedPrice: Double = pricePerKg,
    val availableKg: Double,
    val reservedQuantity: Double = 0.0,
    val moq: Double = 1.0,
    val harvestDate: String = "",
    val expiryDate: String = "",
    val expiryStatus: String,
    @com.google.gson.annotations.SerializedName("isDonatedToNgo") val donatedToNgo: Boolean = false,
    val imageUrl: String = ""
)

data class InventoryUpdateRequest(
    val itemId: String,
    val availableKg: Double? = null,
    val moq: Double? = null,
    val pricePerKg: Double? = null
)

data class StandardBackendResponse(
    val success: Boolean,
    val message: String?
)

// --- PEER-TO-PEER MACHINERY LEASING DATA MODELS ---
data class RentalItemRequest(
    val email: String,
    val equipmentName: String,
    val category: String,
    val ratePerHour: Double,
    val ratePerDay: Double,
    val latitude: Double,
    val longitude: Double
)
data class RentalItemResponse(
    val id: String,
    val email: String = "",
    val equipmentName: String,
    val category: String,
    val ratePerHour: Double,
    val ratePerDay: Double,
    val ownerPhone: String,
    val ownerName: String = "Farmer",
    val ownerProfileUrl: String = "",
    val isVerified: Boolean = true,
    val imageUrl: String = "",
    val city: String = "Unknown Location",
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double = 0.0,
    val isLocked: Boolean = false
)

// --- PROFILE & SETTINGS DATA MODELS ---
data class ProfileDetailsResponse(
    val name: String,
    val profileImage: String? = null,
    val email: String,
    val role: String,
    val isNgo: Boolean = false,
    val member_since: String,
    val primary_crop: String,
    val farm_address: String,
    val home_address: String? = null,
    val is_same_address: Boolean = false,
    val soil_type: String,
    val total_acreage: String,
    val aadhaar_masked: String,
    val darpan_masked: String? = null,
    val addresses: List<AddressModel>? = null,
    val phone: String? = null,
    val profile_image_url: String? = null,
    val username: String? = null,
    val farmLat: Double? = null,
    val farmLon: Double? = null,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val driverId: String? = null,
    val vehicleType: String? = null,
    val licenseUrl: String? = null,
    val rcBookUrl: String? = null
)

data class ProfileUpdateRequest(
    val email: String,
    val primaryCrop: String? = null,
    val farmAddress: String? = null,
    val homeAddress: String? = null,
    val isSameAddress: Boolean? = null,
    val soilType: String? = null,
    val totalAcreage: String? = null,
    val username: String,
    val phone: String,
    val farmLat: Double? = null,
    val farmLon: Double? = null,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val addresses: List<AddressModel>? = null
)

data class AddressModel(
    val id: String,
    val title: String,
    val fullAddress: String,
    val lat: Double,
    val lon: Double,
    val isDefault: Boolean
)

// --- CROP DIAGNOSIS HISTORY MODELS ---
data class SaveDiagnosisRequest(
    val email: String,
    val disease: String,
    val remedy: String,
    val imagePath: String,
    val mode: String,
    val language: String
)

data class DiagnosticRecord(
    val id: String? = null,
    val disease: String? = null,
    val remedy: String? = null,
    val imagePath: String? = null,
    val mode: String? = null,
    val date: String? = null
)

data class DiagnosisHistoryResponse(
    val success: Boolean,
    val history: List<DiagnosticRecord>?,
    val error: String?
)

// --- REVIEW DATA MODELS ---
data class ReviewRequest(
    val id: String? = null,
    val farmerEmail: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val rating: Double = 0.0,
    val text: String = "",
    val timestamp: Long = 0L
)

data class ReviewResponse(
    val id: String = "",
    val farmerEmail: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val userProfileImage: String = "", // Added for Review Profile Photos
    val rating: Double = 0.0,
    val text: String = "",
    val timestamp: Long = 0L
)

// --- AI NEGOTIATION DATA MODELS ---
data class DealAnalysisRequest(
    val cropName: String,
    val location: String,
    val role: String
)

data class DealAnalysisResponse(
    val success: Boolean,
    val minPrice: Double?,
    val maxPrice: Double?,
    val reason: String?
)
// --- NEW UPDATED CODE ---
@com.google.firebase.firestore.IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val type: String = "TEXT", // TEXT, INQUIRY_CARD, COUNTER_CARD, ADDRESS_CARD, INVOICE_CARD, RECEIPT_CARD, AUDIO, RESCUE_CARD, LOGISTICS_CHOICE
    val dealId: String = "",
    val dealType: String = "", // Added to support unified Donation Deals
    val event: String = "",
    val text: String = "",
    val itemId: String = "",
    val inventoryId: String = "",
    val cropName: String = "",
    val imageUrl: String = "",
    val kg: Double = 0.0,
    val basePrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val transportCost: Double = 0.0,
    val totalPrice: Double = 0.0,
    val deliveryAddress: String = "",
    val dropAddress: String = "", // Added to fix "No setter/field for dropAddress"
    @get:PropertyName("dropLat") @set:PropertyName("dropLat") var rawDropLat: Any = 0.0,
    @get:PropertyName("dropLon") @set:PropertyName("dropLon") var rawDropLon: Any = 0.0,
    val pickupAddress: String = "",
    @get:PropertyName("pickupLat") @set:PropertyName("pickupLat") var rawPickupLat: Any = 0.0,
    @get:PropertyName("pickupLon") @set:PropertyName("pickupLon") var rawPickupLon: Any = 0.0,
    val userName: String = "",
    val farmerName: String = "",
    val vehicleType: String = "", // Fixed: Added to support dynamic invoice display
    val distanceKm: Double = 0.0, // Fixed: Added to support tracking distance
    val orderId: String = "",
    val pickupOtp: String = "",
    val dropOtp: String = "",
    val reason: String = "",
    val status: String = "PENDING",
    val paymentStatus: String = "",
    val invoice: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var rawTimestamp: Any? = null
) {
    @get:com.google.firebase.firestore.Exclude
    val timestamp: Long
        get() = when (rawTimestamp) {
            is Long -> rawTimestamp as Long
            is Double -> (rawTimestamp as Double).toLong()
            is com.google.firebase.Timestamp -> (rawTimestamp as com.google.firebase.Timestamp).toDate().time
            is java.util.Date -> (rawTimestamp as java.util.Date).time
            is String -> rawTimestamp.toString().toLongOrNull() ?: 0L
            else -> 0L
        }

    @get:com.google.firebase.firestore.Exclude
    val dropLat: Double get() = rawDropLat.toString().toDoubleOrNull() ?: 0.0
    
    @get:com.google.firebase.firestore.Exclude
    val dropLon: Double get() = rawDropLon.toString().toDoubleOrNull() ?: 0.0
    
    @get:com.google.firebase.firestore.Exclude
    val pickupLat: Double get() = rawPickupLat.toString().toDoubleOrNull() ?: 0.0
    
    @get:com.google.firebase.firestore.Exclude
    val pickupLon: Double get() = rawPickupLon.toString().toDoubleOrNull() ?: 0.0
}

fun com.google.firebase.firestore.DocumentSnapshot.toSafeChatMessage(): ChatMessage {
    val map = this.data ?: return ChatMessage(id = this.id)
    
    fun getStr(key: String, default: String = ""): String {
        val raw = map[key]
        if (raw == null) {
            if (key in listOf("userName", "farmerName", "senderId", "receiverId", "type", "timestamp")) {
                android.util.Log.e("SafeChatMessage", "Legacy data warning: Mandatory field '$key' is missing or null in document ${this.id}")
            }
            return default
        }
        return raw.toString()
    }
    
    fun getDouble(key: String, default: Double = 0.0): Double {
        val raw = map[key] ?: return default
        val v = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: default
            else -> default
        }
        return if (v.isNaN() || v.isInfinite()) default else v
    }
    
    fun getSafeAny(key: String): Any {
        val raw = map[key] ?: return 0.0
        if (raw is Double && (raw.isNaN() || raw.isInfinite())) return 0.0
        if (raw is Float && (raw.isNaN() || raw.isInfinite())) return 0.0
        return raw
    }
    
    return ChatMessage(
        id = this.id,
        senderId = getStr("senderId"),
        receiverId = getStr("receiverId"),
        type = getStr("type", "TEXT"),
        dealId = getStr("dealId"),
        dealType = getStr("dealType"),
        event = getStr("event"),
        text = getStr("text"),
        itemId = getStr("itemId"),
        inventoryId = getStr("inventoryId"),
        cropName = getStr("cropName"),
        imageUrl = getStr("imageUrl"),
        kg = getDouble("kg"),
        basePrice = getDouble("basePrice"),
        targetPrice = getDouble("targetPrice"),
        transportCost = getDouble("transportCost"),
        totalPrice = getDouble("totalPrice"),
        deliveryAddress = getStr("deliveryAddress"),
        dropAddress = getStr("dropAddress"),
        rawDropLat = getSafeAny("dropLat"),
        rawDropLon = getSafeAny("dropLon"),
        pickupAddress = getStr("pickupAddress"),
        rawPickupLat = getSafeAny("pickupLat"),
        rawPickupLon = getSafeAny("pickupLon"),
        userName = getStr("userName"),
        farmerName = getStr("farmerName"),
        vehicleType = getStr("vehicleType"),
        distanceKm = getDouble("distanceKm"),
        orderId = getStr("orderId").takeIf { it.isNotBlank() } 
            ?: getStr("dealId").takeIf { it.isNotBlank() } 
            ?: this.id.removeSuffix("_invoice").removeSuffix("_receipt"),
        pickupOtp = getStr("pickupOtp"),
        dropOtp = getStr("dropOtp"),
        reason = getStr("reason"),
        status = getStr("status", "PENDING"),
        paymentStatus = getStr("paymentStatus"),
        invoice = getStr("invoice"),
        rawTimestamp = map["timestamp"]
    )
}

data class ChatThread(
    val id: String = "",
    val userEmail: String = "",
    val farmerEmail: String = "",
    val lastMessage: String = "",
    val unreadCountUser: Int = 0,
    val unreadCountFarmer: Int = 0,
    val timestamp: Long = 0L,
    val type: String = "MARKETPLACE",
    val status: String? = null,
    val orderId: String? = null,
    val itemName: String? = null,
    val requestedKg: Double? = null,
    val imageUrl: String? = null
)

// --- LOGISTICS & ORDERS DATA MODELS ---
data class LogisticsFareRequest(
    val weight: Double,
    val pickupLat: Double,
    val pickupLon: Double,
    val dropLat: Double,
    val dropLon: Double
)

data class LogisticsFareResponse(
    val success: Boolean,
    val distanceKm: Double,
    val vehicleType: String,
    val suggestedFare: Double,
    val fareMode: String,
    val error: String? = null
)

data class OrderResponse(
    val success: Boolean,
    val orderId: String,
    val pickupOtp: String,
    val dropOtp: String
)

data class PayDonationInvoiceRequest(
    val orderId: String,
    val ngoEmail: String,
    val transportFare: Double
)

data class OrderRequest(
    val orderId: String,
    val farmerEmail: String,
    val userEmail: String,
    val cropName: String,
    val weightKg: Double,
    val cropValue: Double,
    val transportFare: Double,
    val totalPaid: Double,
    val pickupAddress: String,
    val dropAddress: String,
    val pickupLat: Double,
    val pickupLon: Double,
    val dropLat: Double,
    val dropLon: Double,
    val distanceKm: Double,
    val vehicleType: String,
    val pickupOtp: String = "",
    val dropOtp: String = "",
    val isDonation: Boolean = false,
    val dealId: String = "",
    val itemId: String = "",
    val chatId: String = ""
)

data class AvailableLoadResponse(
    val orderId: String,
    val farmerEmail: String,
    val userEmail: String,
    val cropName: String,
    val weightKg: Double,
    val transportFare: Double,
    val pickupAddress: String,
    val dropAddress: String,
    val pickupLat: Double = 0.0,
    val pickupLon: Double = 0.0,
    val dropLat: Double = 0.0,
    val dropLon: Double = 0.0,
    val distanceKm: Double,
    val vehicleType: String,
    val status: String,
    val timestamp: Long
)

data class LoadWrapper(
    val success: Boolean,
    val loads: List<AvailableLoadResponse>?
)

data class CreatePoolRequest(
    val farmerEmail: String,
    val cropName: String,
    val weightKg: Double,
    val pickupAddress: String,
    val dropAddress: String,
    val pickupLat: Double,
    val pickupLon: Double,
    val dropLat: Double,
    val dropLon: Double,
    val dispatchTime: Long,
    val vehicleType: String,
    val distanceKm: Double = 0.0,
    val totalAmount: Double = 0.0
)

data class PoolQuoteResponse(
    val success: Boolean,
    val distanceKm: Double,
    val vehicleType: String,
    val baseFare: Double,
    val perKmRate: Double,
    val totalAmount: Double,
    val capacity: Double,
    val error: String? = null
)

data class JoinPoolQuoteRequest(
    val poolId: String,
    val farmerEmail: String,
    val weightKg: Double,
    val pickupLat: Double,
    val pickupLon: Double,
    val dropLat: Double,
    val dropLon: Double
)

data class JoinPoolQuoteResponse(
    val success: Boolean,
    val proportionalShare: Double,
    val detourKm: Double,
    val detourCost: Double,
    val totalShareB: Double,
    val refundA: Double,
    val newShareA: Double,
    val error: String? = null
)

data class JoinPoolRequest(
    val poolId: String,
    val farmerEmail: String,
    val cropName: String,
    val weightKg: Double,
    val pickupAddress: String,
    val dropAddress: String,
    val pickupLat: Double,
    val pickupLon: Double,
    val dropLat: Double,
    val dropLon: Double,
    val calculatedShare: Double = 0.0
)

data class PoolResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val poolId: String? = null
)

data class PoolItem(
    val orderId: String = "",
    val hostEmail: String = "",
    val vehicleType: String = "",
    val remainingCapacity: Double = 0.0,
    val totalCapacity: Double = 0.0,
    val dispatchTimestamp: Long = 0L,
    val distanceToMe: Double = 0.0,
    val pickupAddress: String = "",
    val dropAddress: String = "",
    val cropName: String = "",
    val weightKg: Double = 0.0,
    val pickupLat: Double = 0.0,
    val pickupLon: Double = 0.0,
    val dropLat: Double = 0.0,
    val dropLon: Double = 0.0,
    val status: String = "",
    val totalPayment: Double = 0.0,
    val coLoaderPayment: Double = 0.0,
    val pickupOtp_A: String = "",
    val dropOtp_A: String = "",
    val pickupOtp_B: String = "",
    val dropOtp_B: String = "",
    val coLoaderEmail: String? = null,
    val driverEmail: String? = null,
    val coLoaderPickupAddress: String? = null,
    val coLoaderDropAddress: String? = null,
    val coLoaderPickupLat: Double? = null,
    val coLoaderPickupLon: Double? = null,
    val coLoaderDropLat: Double? = null,
    val coLoaderDropLon: Double? = null
)

data class AvailablePoolsResponse(
    val success: Boolean,
    val pools: List<PoolItem> = emptyList(),
    val error: String? = null
)
// --- WALLET & TRANSACTION DATA MODELS ---
data class Transaction(
    val type: String = "UNKNOWN",
    val title: String = "",
    val amount: Double = 0.0,
    val isCredit: Boolean = false,
    val timestamp: Long = 0L
)
// --- AI GENERAL CHAT MODELS ---
data class AiChatRequest(
    val message: String,
    val history: List<AiHistoryMessage>,
    val role: String
)

data class AiHistoryMessage(
    val role: String, // Must be "user" or "model"
    val parts: String
)

data class AiChatResponse(
    val success: Boolean,
    val reply: String?,
    val error: String?
)

// --- FAVORITE FARMER MODELS ---
data class ToggleFavoriteRequest(
    val email: String,
    val farmerEmail: String,
    val farmerName: String,
    val profileImageUrl: String
)

data class FavoriteFarmerResponse(
    val farmerEmail: String = "",
    val farmerName: String = "",
    val profileImageUrl: String = "",
    val addedAt: Long = 0L
)