package com.simats.growise.nav_bottom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
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
import com.simats.growise.ui.theme.TerracottaPrimary

@Composable
fun DriverBottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Unified tabs: home, wallet (central hub), profile
    val primaryTabs = listOf("home", "wallet", "profile")
    if (currentRoute !in primaryTabs) return

    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(76.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), spotColor = Color(0x1A000000))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DriverBottomNavItem(
                title = "Loads",
                icon = Icons.Filled.ListAlt,
                isSelected = currentRoute == "home",
                modifier = Modifier.weight(1f),
                onClick = { if (currentRoute != "home") navController.navigate("home") }
            )

            // Spacer to leave room for the central Truck button
            Spacer(modifier = Modifier.weight(1f))

            DriverBottomNavItem(
                title = "Profile",
                icon = Icons.Filled.Person,
                isSelected = currentRoute == "profile",
                modifier = Modifier.weight(1f),
                onClick = { if (currentRoute != "profile") navController.navigate("profile") }
            )
        }

        // Central Unified Truck Icon (Routes to Wallet/History Hub)
        Box(
            modifier = Modifier.offset(y = (-24).dp).size(68.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = TerracottaPrimary)
                .clip(CircleShape)
                .background(TerracottaPrimary)
                .clickable { if (currentRoute != "wallet") navController.navigate("wallet") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = "Wallet and History",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun DriverBottomNavItem(title: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) TerracottaPrimary else Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(26.dp)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(TerracottaPrimary))
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray.copy(alpha = 0.6f))
        }
    }
}