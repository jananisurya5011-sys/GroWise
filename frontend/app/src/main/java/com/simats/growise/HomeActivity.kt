package com.simats.growise

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simats.growise.admin.AdminDashboard
import com.simats.growise.admin.AdminProfile
import com.simats.growise.admin.AdminVerifications
import com.simats.growise.common.OrderHistoryScreen
import com.simats.growise.common.SharedDealScreen
import com.simats.growise.common.SharedTrackScreen
import com.simats.growise.common.WalletScreen
import com.simats.growise.driver.DriverHome
import com.simats.growise.driver.DriverProfile
import com.simats.growise.farmer.FDiagnose
import com.simats.growise.farmer.FHomeScreen
import com.simats.growise.farmer.FListProduct
import com.simats.growise.farmer.FRentalHub
import com.simats.growise.farmer.FProfile
import com.simats.growise.farmer.FSmartCultivation
import com.simats.growise.farmer.FWeatherScreen
import com.simats.growise.farmer.FDiagnosticHistory
import com.simats.growise.farmer.FCropPoolScreen
import com.simats.growise.nav_bottom.AdminBottomNav
import com.simats.growise.nav_bottom.DriverBottomNav
import com.simats.growise.nav_bottom.FarmerBottomNav
import com.simats.growise.nav_bottom.UserBottomNav
import com.simats.growise.user.UHomeScreen
import com.simats.growise.user.UProfile
import com.simats.growise.user.NGOFeedScreen
import com.simats.growise.ui.theme.GroWiseTheme
import com.simats.growise.common.SettingsScreen
import com.simats.growise.common.ChangePasswordScreen

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE)
        val userRole = intent.getStringExtra("USER_ROLE") ?: sharedPref.getString("ROLE", "farmer") ?: "user"
        val userEmail = sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com"

        setContent {
            GroWiseTheme {
                when (userRole) {
                    "farmer" -> FarmerAppShell(userEmail = userEmail)
                    "user", "ngo" -> UserAppShell(userEmail = userEmail)
                    "driver" -> DriverAppShell(userEmail = userEmail)
                    "admin" -> AdminAppShell(userEmail = userEmail)
                    else -> UserAppShell(userEmail = userEmail)
                }
            }
        }
    }
}

@Composable
fun FarmerAppShell(userEmail: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != "deal/{itemId}"

    Scaffold(
        containerColor = com.simats.growise.ui.theme.PeachBackground,
        bottomBar = { if (showBottomBar) FarmerBottomNav(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(400)) }, exitTransition = { fadeOut(animationSpec = tween(400)) },
            popEnterTransition = { fadeIn(animationSpec = tween(400)) }, popExitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            composable("home") { FHomeScreen(navController = navController, onWeatherClick = { navController.navigate("weather") }, onSmartCultivationClick = { navController.navigate("cultivation") }, onDiagnoseCropClick = { navController.navigate("diagnose") }, onHarvestManagerClick = { navController.navigate("harvest") }, onRentEquipmentClick = { navController.navigate("rental") }, onDiagnosticHistoryClick = { navController.navigate("diagnostic_history") }) }
            composable("weather") { FWeatherScreen(onBackClick = { navController.popBackStack() }) }
            composable("cultivation") { FSmartCultivation(onBackClick = { navController.popBackStack() }) }
            composable("diagnose") { FDiagnose(onBackClick = { navController.popBackStack() }) }
            composable("diagnostic_history") { FDiagnosticHistory(onBackClick = { navController.popBackStack() }) }
            composable("harvest") { FListProduct(onBackClick = { navController.popBackStack() }) }
            composable("rental") { FRentalHub(onBackClick = { navController.popBackStack() }) }
            composable("crop_pool") { FCropPoolScreen(navController = navController, userEmail = userEmail) }

            composable("deal") { SharedDealScreen(currentUserEmail = userEmail, targetEmail = "", role = "farmer", onBackClick = { navController.popBackStack() }, onChatClick = { email -> navController.navigate("deal/$email") }, onOrderClick = { navController.navigate("track") }) }

            composable(
                route = "deal/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val buyerEmail = backStackEntry.arguments?.getString("itemId") ?: ""
                SharedDealScreen(currentUserEmail = userEmail, targetEmail = buyerEmail, role = "farmer", onBackClick = { navController.popBackStack() })
            }

            composable("track") { SharedTrackScreen(currentUserEmail = userEmail, role = "farmer", navController = navController) }
            composable("donation_tracking") { com.simats.growise.farmer.DonationTrackingScreen(navController = navController) }
            composable("donation_detail/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                com.simats.growise.farmer.DonationDetailScreen(orderId = orderId, navController = navController)
            }
            composable("track_self_pickup/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                com.simats.growise.common.SelfPickupTrackScreen(orderId = orderId, navController = navController)
            }
            composable("profile") { FProfile(userEmail = userEmail, onBackClick = { navController.popBackStack() }) }
            composable("history") { OrderHistoryScreen(userEmail = userEmail, navController = navController) }
            composable("wallet") { WalletScreen(userEmail = userEmail, role = "farmer", navController = navController) }
            composable("settings") { SettingsScreen(navController = navController) }
            composable("change_password") { ChangePasswordScreen(navController = navController, identifier = userEmail) }
            composable("ai") { com.simats.growise.common.AiChatScreen(navController = navController, userEmail = userEmail, role = "farmer") }
        }
    }
}
@Composable
fun UserAppShell(userEmail: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != "inbox/{itemId}"

    Scaffold(
        containerColor = com.simats.growise.ui.theme.PeachBackground,
        bottomBar = { if (showBottomBar) UserBottomNav(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(400)) }, exitTransition = { fadeOut(animationSpec = tween(400)) },
            popEnterTransition = { fadeIn(animationSpec = tween(400)) }, popExitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            composable("home") { UHomeScreen(navController = navController, onWeatherClick = { navController.navigate("weather") }, onSettingsClick = { navController.navigate("profile") }, onDonationHubClick = { navController.navigate("donations") }, onFarmerClick = { farmerEmail -> navController.navigate("farmer_menu/$farmerEmail") }, onItemClick = { farmerEmail -> navController.navigate("farmer_menu/$farmerEmail") }, onAddFavoriteClick = { navController.navigate("inbox") }) }
            composable("weather") { FWeatherScreen(onBackClick = { navController.popBackStack() }) }
            composable("donations") { NGOFeedScreen(navController = navController, userEmail = userEmail) }

            composable(
                route = "farmer_menu/{farmerEmail}",
                arguments = listOf(navArgument("farmerEmail") { type = NavType.StringType })
            ) { backStackEntry ->
                val farmerEmail = backStackEntry.arguments?.getString("farmerEmail") ?: ""
                com.simats.growise.user.UFarmerMenuScreen(navController = navController, farmerEmail = farmerEmail)
            }

            composable("inbox") { SharedDealScreen(currentUserEmail = userEmail, targetEmail = "", role = "user", onBackClick = { navController.popBackStack() }, onChatClick = { email -> navController.navigate("inbox/$email") }, onOrderClick = { navController.navigate("status") }) }

            composable(
                route = "inbox/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val farmerEmail = backStackEntry.arguments?.getString("itemId") ?: ""
                SharedDealScreen(currentUserEmail = userEmail, targetEmail = farmerEmail, role = "user", onBackClick = { navController.popBackStack() })
            }

            composable("ai") { com.simats.growise.common.AiChatScreen(navController = navController, userEmail = userEmail, role = "user") }
            composable("status") { SharedTrackScreen(currentUserEmail = userEmail, role = "user", navController = navController) }
            composable("track_self_pickup/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                com.simats.growise.common.SelfPickupTrackScreen(orderId = orderId, navController = navController)
            }
            composable("profile") { UProfile(userEmail = userEmail, onBackClick = { navController.popBackStack() }) }
            composable("history") { OrderHistoryScreen(userEmail = userEmail, navController = navController) }
            composable("wallet") { WalletScreen(userEmail = userEmail, role = "user", navController = navController) }
            composable("settings") { SettingsScreen(navController = navController) }
            composable("change_password") { ChangePasswordScreen(navController = navController, identifier = userEmail) }
        }
    }
}

@Composable
fun DriverAppShell(userEmail: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf("home", "wallet", "profile")

    Scaffold(
        containerColor = com.simats.growise.ui.theme.PeachBackground,
        bottomBar = { if (showBottomBar) DriverBottomNav(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(400)) }, exitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            composable("home") { DriverHome(navController = navController) } // Make sure DriverHome accepts navController
            composable("status") { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Active Trip Status") } }
            composable("profile") { DriverProfile(userEmail = userEmail, navController = navController) }
            composable("wallet") { WalletScreen(userEmail = userEmail, role = "driver", navController = navController) }
            composable("history") { OrderHistoryScreen(userEmail = userEmail, navController = navController) }
            composable("document_vault") { com.simats.growise.driver.DriverDocumentVaultScreen(userEmail = userEmail, navController = navController) }
            composable("settings") { SettingsScreen(navController = navController) }
            composable("change_password") { ChangePasswordScreen(navController = navController, identifier = userEmail) }
        }
    }
}

@Composable
fun AdminAppShell(userEmail: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf("home", "verify", "profile")

    Scaffold(
        containerColor = com.simats.growise.ui.theme.PeachBackground,
        bottomBar = { if (showBottomBar) AdminBottomNav(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(400)) }, exitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            composable("home") { AdminDashboard(navController = navController) }
            composable("verify") { AdminVerifications(navController = navController) }
            composable("profile") { AdminProfile(userEmail = userEmail) }

            composable(
                route = "admin_list/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: ""
                com.simats.growise.admin.AdminListScreen(navController = navController, listType = type)
            }

            composable(
                route = "admin_driver_details/{email}",
                arguments = listOf(navArgument("email") { type = NavType.StringType })
            ) { backStackEntry ->
                val email = backStackEntry.arguments?.getString("email") ?: ""
                com.simats.growise.admin.AdminDriverDetailsScreen(driverEmail = email, navController = navController)
            }
            composable("settings") { SettingsScreen(navController = navController) }
            composable("change_password") { ChangePasswordScreen(navController = navController, identifier = userEmail) }
        }
    }
}