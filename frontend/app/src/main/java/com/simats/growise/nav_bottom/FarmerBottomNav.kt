package com.simats.growise.nav_bottom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

// Integrated Sealed Class (Renamed to avoid conflict with the Composable below)
sealed class BottomNavRoute(var title: String, var icon: ImageVector, var route: String) {
    object Home : BottomNavRoute("Home", Icons.Filled.Home, "home")
    object Deal : BottomNavRoute("Deal", Icons.Filled.ShoppingCart, "deal")
    object Track : BottomNavRoute("Track", Icons.Filled.LocalShipping, "track")
    object Profile : BottomNavRoute("Profile", Icons.Filled.Person, "profile")
}

@Composable
fun FarmerBottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val primaryTabs = listOf("home", "deal", "track", "profile")
    if (currentRoute !in primaryTabs) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        // High-end curved top container mimicking the premium design framework
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BottomNavItem(
                title = "Home",
                icon = Icons.Filled.Home,
                isSelected = currentRoute == "home",
                onClick = { if (currentRoute != "home") navController.navigate("home") }
            )

            BottomNavItem(
                title = "Deal",
                icon = Icons.Filled.Chat,
                isSelected = currentRoute == "deal",
                onClick = { if (currentRoute != "deal") navController.navigate("deal") }
            )

            // Dynamic spacing element to ensure clearance for overlapping action hub
            Spacer(modifier = Modifier.width(72.dp))

            BottomNavItem(
                title = "Track",
                icon = Icons.Filled.LocalShipping,
                isSelected = currentRoute == "track",
                onClick = { if (currentRoute != "track") navController.navigate("track") }
            )

            BottomNavItem(
                title = "Profile",
                icon = Icons.Filled.Person,
                isSelected = currentRoute == "profile",
                onClick = { if (currentRoute != "profile") navController.navigate("profile") }
            )
        }

        // Overlapping Central Agriculture AI Button Container
        Box(
            modifier = Modifier
                .offset(y = (-24).dp)
                .size(68.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = com.simats.growise.ui.theme.GoldenYellow)
                .clip(CircleShape)
                .background(com.simats.growise.ui.theme.GoldenYellow)
                .clickable { if (currentRoute != "ai") navController.navigate("ai") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = "AI Assistant Hub",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun RowScope.BottomNavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) com.simats.growise.ui.theme.TerracottaPrimary else Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(26.dp)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(com.simats.growise.ui.theme.TerracottaPrimary)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray.copy(alpha = 0.6f)
            )
        }
    }
}