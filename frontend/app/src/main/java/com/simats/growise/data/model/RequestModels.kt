package com.simats.growise.data.model

import com.google.gson.annotations.SerializedName

data class TransitionDealRequest(
    val chatId: String,
    val dealId: String,
    val status: String? = null,
    val type: String? = null,
    val logisticsType: String? = null,
    val distanceKm: Double? = null,
    val vehicleType: String? = null,
    val transportCost: Double? = null,
    val totalPrice: Double? = null,
    val dropLat: Double? = null,
    val dropLon: Double? = null,
    val dropAddress: String? = null,
    val pickupLat: Double? = null,
    val pickupLon: Double? = null,
    val pickupAddress: String? = null,
    val farmerName: String? = null,
    val userName: String? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val text: String? = null,
    val timestamp: Long? = null,
    val item: String? = null,
    val kg: Double? = null,
    val price: Double? = null,
    val targetPrice: Double? = null,
    val reason: String? = null
)

data class EmailRequest(
    val email: String
)

data class FarmerEmailRequest(
    val farmerEmail: String
)

data class ItemIdRequest(
    val itemId: String
)

data class ReviewIdRequest(
    @SerializedName("id")
    val reviewId: String
)

data class OrderIdRequest(
    val orderId: String
)

data class PoolIdRequest(
    val poolId: String
)

data class VerifySelfPickupRequest(
    val orderId: String,
    val otp: String
)

data class ToggleRentalRequest(
    val itemId: String,
    val lockStatus: Boolean
)

data class TelemetryRequest(
    val email: String,
    val lat: String,
    val lon: String
)

data class DriverModel(
    val driverId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val vehicleNumber: String? = null,
    val vehicleType: String? = null,
    val driverLicense: String? = null,
    val status: String? = null
)

data class PendingDriversResponse(
    val success: Boolean,
    val drivers: List<com.simats.growise.data.model.PendingDriverResponse>? 
)

data class DonationRequest(
    val ngoEmail: String,
    val farmerEmail: String,
    val itemId: String,
    val requestedQuantity: Double
)

data class AcceptDonationRequest(
    val orderId: String,
    val farmerEmail: String
)

data class RejectDonationRequest(
    val orderId: String,
    val farmerEmail: String
)

data class ConfirmDonationLogisticsRequest(
    val orderId: String,
    val ngoEmail: String,
    val vehicleType: String,
    val transportFare: Double,
    val dropAddress: String,
    val dropLat: Double,
    val dropLon: Double,
    val distanceKm: Double
)

data class DonationResponse(
    val success: Boolean,
    val orderId: String?,
    val error: String?
)
