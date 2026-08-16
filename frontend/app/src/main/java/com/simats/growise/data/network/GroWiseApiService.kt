    package com.simats.growise.data.network

    import com.simats.growise.data.model.*
    import okhttp3.MultipartBody
    import com.simats.growise.data.model.*
import retrofit2.Response
    import retrofit2.http.Body
    import retrofit2.http.Multipart
    import retrofit2.http.POST
    import retrofit2.http.Part
    import retrofit2.http.Query

    interface GroWiseApiService {
        @POST("api/auth/signup")
        suspend fun signup(@Body request: SignupRequest): Response<SignupResponse>

        @POST("api/auth/login")
        suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

        @POST("api/auth/update-password")
        suspend fun updatePassword(@Body request: PasswordUpdateRequest): Response<StandardBackendResponse>

        @Multipart
        @POST("api/crop-doctor/diagnose")
        suspend fun uploadCropImage(
            @Part image: MultipartBody.Part,
            @Part("language") language: okhttp3.RequestBody
        ): CropDiagnosisResponse

        @POST("api/crop-doctor/save-diagnosis")
        suspend fun saveDiagnosis(@Body request: SaveDiagnosisRequest): StandardBackendResponse

        @POST("api/crop-doctor/history")
        suspend fun fetchDiagnosisHistory(@Body request: EmailRequest): DiagnosisHistoryResponse

        @POST("api/cultivation/generate")
        suspend fun generateRoadmap(@Body request: CultivationRequest): CultivationResponse

        @POST("api/cultivation/fetch-active-crops")
        suspend fun fetchActiveCrops(@Body request: EmailRequest): ActiveCropResponse

        @POST("api/cultivation/mark-task-done")
        suspend fun markTaskDone(@Body request: TaskCompletionRequest): StandardBackendResponse

        @Multipart
        @POST("api/inventory/add")
        suspend fun addInventoryItemWithImage(
            @Part("email") email: okhttp3.RequestBody,
            @Part("category") category: okhttp3.RequestBody,
            @Part("cropName") cropName: okhttp3.RequestBody,
            @Part("pricePerKg") pricePerKg: okhttp3.RequestBody,
            @Part("availableKg") availableKg: okhttp3.RequestBody,
            @Part("moq") moq: okhttp3.RequestBody,
            @Part("harvestDate") harvestDate: okhttp3.RequestBody,
            @Part("expiryDate") expiryDate: okhttp3.RequestBody,
            @Part image: MultipartBody.Part?
        ): StandardBackendResponse

        @POST("api/inventory/add")
        suspend fun addInventoryItem(@Body request: InventoryItemRequest): StandardBackendResponse

        @POST("api/inventory/fetch")
        suspend fun fetchInventory(@Body request: EmailRequest): List<InventoryItemResponse>

        // FIX: Added dedicated endpoint to fetch the global market items for Users & NGOs
        @POST("api/inventory/market")
        suspend fun fetchMarketItems(): List<InventoryItemResponse>

        @POST("api/inventory/update")
        suspend fun updateInventoryItem(@Body request: InventoryUpdateRequest): StandardBackendResponse

        @Multipart
        @POST("api/inventory/update")
        suspend fun updateInventoryItemWithImage(
            @Part("itemId") itemId: okhttp3.RequestBody,
            @Part("pricePerKg") pricePerKg: okhttp3.RequestBody,
            @Part("availableKg") availableKg: okhttp3.RequestBody,
            @Part("moq") moq: okhttp3.RequestBody,
            @Part("harvestDate") harvestDate: okhttp3.RequestBody,
            @Part("expiryDate") expiryDate: okhttp3.RequestBody,
            @Part image: MultipartBody.Part?
        ): StandardBackendResponse

        @POST("api/inventory/delete")
        suspend fun deleteInventoryItem(@Body request: ItemIdRequest): StandardBackendResponse

        @POST("api/inventory/donate-ngo")
        suspend fun routeToNgoPool(@Body request: ItemIdRequest): StandardBackendResponse

        @Multipart
        @POST("api/rental/add")
        suspend fun addRentalItemWithImage(
            @Part("email") email: okhttp3.RequestBody,
            @Part("equipmentName") equipmentName: okhttp3.RequestBody,
            @Part("category") category: okhttp3.RequestBody,
            @Part("rate") rate: okhttp3.RequestBody,
            @Part("rateType") rateType: okhttp3.RequestBody,
            @Part("ownerPhone") ownerPhone: okhttp3.RequestBody,
            @Part("latitude") latitude: okhttp3.RequestBody,
            @Part("longitude") longitude: okhttp3.RequestBody,
            @Part image: MultipartBody.Part?
        ): StandardBackendResponse

        @Multipart
        @POST("api/rental/add")
        suspend fun addRentalItemWithImage(
            @Part("email") email: okhttp3.RequestBody,
            @Part("equipmentName") equipmentName: okhttp3.RequestBody,
            @Part("category") category: okhttp3.RequestBody,
            @Part("ratePerHour") ratePerHour: okhttp3.RequestBody,
            @Part("ratePerDay") ratePerDay: okhttp3.RequestBody,
            @Part("latitude") latitude: okhttp3.RequestBody,
            @Part("longitude") longitude: okhttp3.RequestBody,
            @Part image: MultipartBody.Part?
        ): StandardBackendResponse

        @POST("api/rental/add")
        suspend fun addRentalItem(@Body request: RentalItemRequest): StandardBackendResponse

        @POST("api/rental/search")
        suspend fun fetchRentalItems(
            @Query("latitude") lat: Double,
            @Query("longitude") lon: Double,
            @Query("category") category: String,
            @Query("sortBy") sortBy: String
        ): List<RentalItemResponse>

        @POST("api/rental/my-items")
        suspend fun fetchMyRentalItems(@Body request: EmailRequest): List<RentalItemResponse>

        @POST("api/rental/delete")
        suspend fun deleteRentalItem(@Body request: ItemIdRequest): StandardBackendResponse

        // Added Toggle Lock Endpoint
        @POST("api/rental/toggle-lock")
        suspend fun toggleRentalLock(@Body request: ToggleRentalRequest): StandardBackendResponse

        @POST("api/profile/sync-telemetry")
        suspend fun saveLocationTelemetry(@Body request: TelemetryRequest): Response<StandardBackendResponse>

        @POST("api/profile/update-details")
        suspend fun saveProfileFields(@Body request: ProfileUpdateRequest): Response<StandardBackendResponse>

        @Multipart
        @POST("api/profile/update-details")
        suspend fun updateProfileWithImage(
            @Part("email") email: okhttp3.RequestBody,
            @Part("primaryCrop") primaryCrop: okhttp3.RequestBody,
            @Part("farmAddress") farmAddress: okhttp3.RequestBody,
            @Part("homeAddress") homeAddress: okhttp3.RequestBody,
            @Part("isSameAddress") isSameAddress: okhttp3.RequestBody,
            @Part("soilType") soilType: okhttp3.RequestBody,
            @Part("totalAcreage") totalAcreage: okhttp3.RequestBody,
            @Part("username") username: okhttp3.RequestBody,
            @Part("phone") phone: okhttp3.RequestBody,
            @Part("farmLat") farmLat: okhttp3.RequestBody,
            @Part("farmLon") farmLon: okhttp3.RequestBody,
            @Part("homeLat") homeLat: okhttp3.RequestBody,
            @Part("homeLon") homeLon: okhttp3.RequestBody,
            @Part profile_image: MultipartBody.Part
        ): Response<StandardBackendResponse>

        @POST("api/profile/fetch-details")
        suspend fun retrieveProfileFields(@Query("email") email: String): Response<ProfileDetailsResponse>

        // --- NEW UPDATED CODE ---
        @POST("api/profile/update-details")
        suspend fun saveUserProfileFields(@Body request: ProfileUpdateRequest): Response<StandardBackendResponse>

        @Multipart
        @POST("api/profile/update-details")
        suspend fun updateUserProfileWithImage(
            @Part("email") email: okhttp3.RequestBody,
            @Part("username") username: okhttp3.RequestBody,
            @Part("phone") phone: okhttp3.RequestBody,
            @Part("addresses") addressesJson: okhttp3.RequestBody,
            @Part profile_image: MultipartBody.Part
        ): Response<StandardBackendResponse>

        // --- FAVORITES HUB ---
        @POST("api/profile/toggle-favorite")
        suspend fun toggleFavorite(@Body request: ToggleFavoriteRequest): Response<StandardBackendResponse>

        @POST("api/profile/get-favorites")
        suspend fun getFavorites(@Body request: EmailRequest): Response<List<FavoriteFarmerResponse>>

        // --- REVIEWS HUB ---
        @POST("api/reviews/fetch")
        suspend fun fetchReviews(@Body request: com.simats.growise.data.model.FarmerEmailRequest): List<ReviewResponse>

        @POST("api/reviews/add")
        suspend fun addReview(@Body request: ReviewRequest): StandardBackendResponse

        @POST("api/reviews/update")
        suspend fun updateReview(@Body request: ReviewRequest): StandardBackendResponse

        @POST("api/reviews/delete")
        suspend fun deleteReview(@Body request: ReviewIdRequest): StandardBackendResponse

        // --- AI CHAT NEGOTIATION HUB ---
        @POST("api/chat/analyze-deal")
        suspend fun analyzeMarketDeal(@Body request: DealAnalysisRequest): DealAnalysisResponse

        // --- ADMIN HUB ---
        @retrofit2.http.GET("api/admin/stats")
        suspend fun getAdminStats(): retrofit2.Response<AdminStatsResponse>

        @retrofit2.http.GET("api/admin/pending-drivers")
        suspend fun getPendingDrivers(): retrofit2.Response<PendingDriversResponse>

        @POST("api/admin/verify-driver")
        suspend fun verifyDriver(@Body request: AdminVerificationRequest): Response<StandardBackendResponse>

        @retrofit2.http.GET("api/admin/list")
        suspend fun getAdminList(@Query("type") type: String): retrofit2.Response<AdminListResponse>

        @retrofit2.http.GET("api/admin/graph-data")
        suspend fun getGraphData(@Query("range") range: String): retrofit2.Response<AdminGraphResponse>

        // --- LOGISTICS & ORDERS HUB ---
        @POST("api/logistics/calculate-fare")
        suspend fun calculateFare(@Body request: LogisticsFareRequest): Response<LogisticsFareResponse>

        @retrofit2.http.GET("api/logistics/available-loads")
        suspend fun getAvailableLoads(
            @Query("lat") lat: Double,
            @Query("lon") lon: Double,
            @Query("email") email: String
        ): Response<LoadWrapper>

        // --- SECURE ORDERS HUB ---
        @POST("api/orders/create_order")
        suspend fun createOrder(@Body request: OrderRequest): Response<OrderResponse>

        @POST("api/orders/verify_self_pickup")
        suspend fun verifySelfPickup(@Body request: VerifySelfPickupRequest): Response<StandardBackendResponse>

        @POST("api/orders/request-donation")
        suspend fun requestDonation(@Body request: DonationRequest): Response<DonationResponse>

        @POST("api/orders/accept-donation")
        suspend fun acceptDonation(@Body request: AcceptDonationRequest): Response<StandardBackendResponse>

        @POST("api/orders/reject-donation")
        suspend fun rejectDonation(@Body request: RejectDonationRequest): Response<StandardBackendResponse>

        @POST("api/orders/confirm-donation-logistics")
        suspend fun confirmDonationLogistics(@Body request: ConfirmDonationLogisticsRequest): Response<StandardBackendResponse>

        @POST("api/orders/pay-donation-invoice")
        suspend fun payDonationInvoice(@Body request: PayDonationInvoiceRequest): Response<StandardBackendResponse>

        @POST("api/deals/transition")
        suspend fun transitionDeal(@Body request: TransitionDealRequest): Response<StandardBackendResponse>

        // --- WALLET & TRANSACTIONS HUB ---
        @POST("api/wallet/fetch-transactions")
        suspend fun fetchWalletTransactions(@Body request: EmailRequest): List<Transaction>
        // --- LOGISTICS POOLING HUB ---
        @POST("api/logistics/quote-pool")
        suspend fun quotePool(@Body request: LogisticsFareRequest): Response<PoolQuoteResponse>

        @POST("api/logistics/cancel-order")
        suspend fun cancelOrder(@Body request: OrderIdRequest): Response<StandardBackendResponse>

        @retrofit2.http.GET("api/logistics/available-pools")
        suspend fun getAvailablePools(
            @retrofit2.http.Query("lat") lat: Double,
            @retrofit2.http.Query("lon") lon: Double,
            @retrofit2.http.Query("email") email: String = ""
        ): retrofit2.Response<AvailablePoolsResponse>

        @retrofit2.http.POST("api/logistics/create-pool")
        suspend fun createPool(@retrofit2.http.Body request: CreatePoolRequest): retrofit2.Response<PoolResponse>

        @retrofit2.http.POST("api/logistics/quote-join-pool")
        suspend fun quoteJoinPool(@retrofit2.http.Body request: JoinPoolQuoteRequest): retrofit2.Response<JoinPoolQuoteResponse>

        @retrofit2.http.POST("api/logistics/join-pool")
        suspend fun joinPool(@retrofit2.http.Body request: JoinPoolRequest): retrofit2.Response<PoolResponse>

        @retrofit2.http.POST("api/logistics/delete-pool")
        suspend fun deletePool(@retrofit2.http.Body request: PoolIdRequest): retrofit2.Response<StandardBackendResponse>

        // --- NEW: AI GENERAL CHAT HUB ---
        @POST("api/chat/general-chat")
        suspend fun sendAiChatMessage(@Body request: AiChatRequest): AiChatResponse
    }
