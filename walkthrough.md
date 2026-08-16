# Android Migration & History Parsing Walkthrough

## What was Changed

### 1. Android Deal Flow Migration (`SharedDealScreen.kt`)
- Migrated all `DONATION` deal flow transitions to use the unified backend state machine (`POST /api/deals/transition`).
- Replaced direct Firestore `addDoc`/`update` calls for state changes like `PICKUP_ADDRESS_SUBMITTED`, `LOGISTICS_CHOICE`, and `SELF_SERVICE` with `RetrofitClient.apiService.transitionDeal(payload)`.
- Replaced local order generation and receipt card creation with backend API calls. This aligns the Android implementation perfectly with the website's command-driven state architecture.
- Added necessary properties (`dealId`, `itemId`) to `OrderRequest` and created `OrderResponse` in `AuthModels.kt`.

### 2. Live Self Pickup Tracking for Farmers
- Created a new `SelfPickupTrackScreen.kt` component in Android specifically dedicated to Farmer self-pickup tracking.
- Implemented real-time location mapping showing the live GPS trace of the NGO approaching the farm, identical to the ride-hailing tracking mechanism.
- Added OTP verification flow that calls `POST /api/orders/verify_self_pickup` strictly verifying the interaction through the backend.
- Updated `HomeActivity.kt` to define the route `track_self_pickup/{orderId}` and updated `SharedTrackScreen.kt` to redirect automatically to the new tracking screen for active self-pickup donation orders.

### 3. History & Wallet Parsing (Web + Android)
- **Android `OrderHistoryScreen.kt`**: Extended `isNgoRescue` parsing logic to natively check `orderIdStr.startsWith("GW-DON-")`. This enforces correct classification of all historic and new donation orders under the Donation Rescue banner.
- **Android `WalletScreen.kt`**: Implemented identical `GW-DON-` verification within the `driverOrderHistory` parser, correctly mapping the order to "Donation Rescue".
- **Website `DriverWallet.jsx`**: Extended the transaction layout mapping logic to parse `DONATION` order types dynamically (using `getOrderType`), rendering a `<Heart />` icon and assigning the 'Donation Rescue' label dynamically.

## Validation Results
- The entire Android donation workflow correctly triggers backend state transitions, solving all issues of duplicate cards or missing receipts.
- Live tracking and backend-centric OTP flows ensure security and UI synchronization.
- All wallets and historical screens properly identify, render, and tag donation deals originating from `GW-DON-` prefixes.
